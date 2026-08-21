import { randomUUID } from "node:crypto";
import type { ServerResponse } from "node:http";

import type { RoomMessage, RoomRole } from "../application/rooms/roomTypes.js";

export interface SeatState {
  id: number;
  locked: boolean;
  occupant: null | { userId: string; identity: string; name: string; role: RoomRole; muted: boolean; forceMuted: boolean };
}

export interface ParticipantSnapshot {
  userId: string;
  identity: string;
  name: string;
  role: RoomRole;
  seatId: number | null;
}

export interface RoomSnapshot {
  messages: RoomMessage[];
  handRaises: Array<{ userId: string; identity: string; name: string; requestedAt: string }>;
  closed: boolean;
  roomName: string;
  revision: number;
  seats: SeatState[];
  participantCount: number;
  participants: ParticipantSnapshot[];
  self: ParticipantSnapshot;
}

interface ParticipantState {
  userId: string;
  identity: string;
  name: string;
  role: RoomRole;
  sessionId: string;
  streams: Set<ServerResponse>;
  disconnectTimer: NodeJS.Timeout | undefined;
}

interface InternalRoom {
  kickedUserIds: Set<string>;
  messages: RoomMessage[];
  handRaises: Map<string, { userId: string; identity: string; name: string; requestedAt: string }>;
  closed: boolean;
  name: string;
  revision: number;
  seats: SeatState[];
  participants: Map<string, ParticipantState>;
}

export class RoomStateStore {
  constructor(private readonly onDisconnect?: (roomName: string, identity: string) => void | Promise<void>) {}

  readonly #rooms = new Map<string, InternalRoom>();
  readonly #sessions = new Map<string, { roomName: string; identity: string }>();

  join(roomName: string, userId: string, identity: string, name: string, role: RoomRole) {
    const room = this.#room(roomName);
    if (room.kickedUserIds.has(userId)) throw new Error("FORBIDDEN");
    const existing = room.participants.get(identity);
    if (existing) {
      existing.name = name;
      existing.role = role;
      existing.userId = userId;
      if (existing.disconnectTimer) clearTimeout(existing.disconnectTimer);
      existing.disconnectTimer = undefined;
      return existing;
    }
    const participant: ParticipantState = { userId, identity, name, role, sessionId: randomUUID(), streams: new Set(), disconnectTimer: undefined };
    room.participants.set(identity, participant);
    this.#sessions.set(participant.sessionId, { roomName, identity });
    this.#changed(roomName);
    return participant;
  }

  authenticate(sessionId: string | undefined, roomName: string) {
    if (!sessionId) return undefined;
    const session = this.#sessions.get(sessionId);
    if (!session || session.roomName !== roomName) return undefined;
    return this.#room(roomName).participants.get(session.identity);
  }

  participantCount(roomName: string): number {
    return this.#rooms.get(roomName)?.participants.size ?? 0;
  }

  isKicked(roomName: string, userId: string): boolean { return this.#rooms.get(roomName)?.kickedUserIds.has(userId) ?? false; }

  participant(roomName: string, identity: string) {
    return this.#room(roomName).participants.get(identity);
  }

  participantByUserId(roomName: string, userId: string) {
    return [...this.#room(roomName).participants.values()].find((participant) => participant.userId === userId);
  }

  snapshot(roomName: string, identity: string): RoomSnapshot {
    const room = this.#room(roomName);
    const participant = room.participants.get(identity);
    if (!participant) throw new Error("UNAUTHORIZED");
    const participants = [...room.participants.values()].map((item) => this.#participant(room, item));
    return { roomName, revision: room.revision, messages: room.messages, handRaises: [...room.handRaises.values()], closed: room.closed, seats: room.seats, participantCount: participants.length, participants, self: this.#participant(room, participant) };
  }

  hydrate(roomName: string, messages: RoomMessage[], lockedSeatIds: number[]) { const room = this.#room(roomName); if (room.messages.length === 0) room.messages = messages; for (const seat of room.seats) seat.locked = lockedSeatIds.includes(seat.id); }
  appendMessage(roomName: string, message: RoomMessage) { const room = this.#room(roomName); if (!room.messages.some((item) => item.id === message.id)) { room.messages = [...room.messages.slice(-99), message]; this.#changed(roomName); } }
  raiseHand(roomName: string, identity: string, raised: boolean) { const room = this.#room(roomName); const participant = room.participants.get(identity); if (!participant) throw new Error("NOT_FOUND"); if (raised) room.handRaises.set(identity, { userId: participant.userId, identity, name: participant.name, requestedAt: new Date().toISOString() }); else room.handRaises.delete(identity); this.#changed(roomName); }
  resolveHand(roomName: string, actorIdentity: string, targetIdentity: string, accepted: boolean, seatId?: number) { const room = this.#room(roomName); const actor = room.participants.get(actorIdentity); if (!actor || !canModerate(actor.role)) throw new Error("FORBIDDEN"); if (!room.handRaises.has(targetIdentity)) throw new Error("NOT_FOUND"); if (accepted) { const target = room.participants.get(targetIdentity); if (!target) throw new Error("NOT_FOUND"); const targetSeat = seatId ?? room.seats.find((seat) => seat.id !== 1 && !seat.locked && !seat.occupant)?.id; if (!targetSeat) throw new Error("NO_AVAILABLE_SEAT"); this.claimSeat(roomName, targetIdentity, targetSeat); } room.handRaises.delete(targetIdentity); this.#changed(roomName); }
  kick(roomName: string, actorIdentity: string, targetIdentity: string) { const room = this.#room(roomName); const actor = room.participants.get(actorIdentity); const target = room.participants.get(targetIdentity); if (!actor || !canModerate(actor.role) || !target) throw new Error("FORBIDDEN"); if (target.role === "host") throw new Error("FORBIDDEN"); room.kickedUserIds.add(target.userId); this.disconnect(roomName, targetIdentity); }
  closeRoom(roomName: string, actorIdentity: string) { const room = this.#room(roomName); const actor = room.participants.get(actorIdentity); if (!actor || actor.role !== "host") throw new Error("FORBIDDEN"); room.closed = true; this.#changed(roomName); }

  setRole(roomName: string, actorIdentity: string, targetUserId: string, role: Exclude<RoomRole, "host">) {
    const room = this.#room(roomName);
    const actor = room.participants.get(actorIdentity);
    const target = [...room.participants.values()].find((item) => item.userId === targetUserId);
    if (!actor || actor.role !== "host") throw new Error("FORBIDDEN");
    if (!target) throw new Error("NOT_FOUND");
    target.role = role;
    const seat = room.seats.find((item) => item.occupant?.identity === target.identity);
    if (seat?.occupant) seat.occupant.role = role;
    this.#changed(roomName);
  }

  claimSeat(roomName: string, identity: string, seatId: number) {
    const room = this.#room(roomName);
    const participant = room.participants.get(identity);
    const seat = room.seats[seatId - 1];
    if (!participant || !seat) throw new Error("NOT_FOUND");
    if (seat.locked) throw new Error("LOCKED");
    if (seat.id === 1 && participant.role !== "host") throw new Error("FORBIDDEN");
    if (seat.occupant && seat.occupant.identity !== identity) throw new Error("OCCUPIED");
    const current = room.seats.find((item) => item.occupant?.identity === identity);
    if (current && current.id !== seatId) throw new Error("ALREADY_SEATED");
    seat.occupant = { userId: participant.userId, identity, name: participant.name, role: participant.role, muted: true, forceMuted: false };
    this.#changed(roomName);
  }

  leaveSeat(roomName: string, identity: string, targetIdentity = identity) {
    const room = this.#room(roomName);
    const actor = room.participants.get(identity);
    if (!actor) throw new Error("UNAUTHORIZED");
    if (targetIdentity !== identity && !canModerate(actor.role)) throw new Error("FORBIDDEN");
    const target = room.participants.get(targetIdentity);
    if (target?.role === "host" && actor.role !== "host") throw new Error("FORBIDDEN");
    const seat = room.seats.find((item) => item.occupant?.identity === targetIdentity);
    if (seat) { seat.occupant = null; this.#changed(roomName); }
  }

  setSeatLock(roomName: string, identity: string, seatId: number, locked: boolean) {
    const room = this.#room(roomName);
    const actor = room.participants.get(identity);
    const seat = room.seats[seatId - 1];
    if (!actor || !canModerate(actor.role)) throw new Error("FORBIDDEN");
    if (!seat) throw new Error("NOT_FOUND");
    seat.locked = locked;
    this.#changed(roomName);
  }

  setMuted(roomName: string, identity: string, targetIdentity: string, muted: boolean) {
    const room = this.#room(roomName);
    const actor = room.participants.get(identity);
    const target = room.participants.get(targetIdentity);
    const seat = room.seats.find((item) => item.occupant?.identity === targetIdentity);
    if (!actor || !target || !seat?.occupant) throw new Error("NOT_FOUND");
    if (identity !== targetIdentity && !canModerate(actor.role)) throw new Error("FORBIDDEN");
    if (target.role === "host" && actor.role !== "host" && identity !== targetIdentity) throw new Error("FORBIDDEN");
    if (identity === targetIdentity && seat.occupant.forceMuted && !muted) throw new Error("FORBIDDEN");
    if (identity !== targetIdentity) seat.occupant.forceMuted = muted;
    seat.occupant.muted = muted;
    this.#changed(roomName);
  }

  subscribe(roomName: string, identity: string, response: ServerResponse) {
    const participant = this.#room(roomName).participants.get(identity);
    if (!participant) throw new Error("UNAUTHORIZED");
    participant.streams.add(response);
    if (participant.disconnectTimer) clearTimeout(participant.disconnectTimer);
    participant.disconnectTimer = undefined;
    this.#writeSnapshot(roomName, participant, response);
    const heartbeat = setInterval(() => response.write(": heartbeat\n\n"), 20_000);
    response.on("close", () => {
      clearInterval(heartbeat);
      participant.streams.delete(response);
      if (participant.streams.size === 0) { participant.disconnectTimer = setTimeout(() => this.disconnect(roomName, identity), 15_000); participant.disconnectTimer.unref(); }
    });
  }

  disconnect(roomName: string, identity: string) {
    const room = this.#room(roomName);
    const participant = room.participants.get(identity);
    if (!participant) return;
    const seat = room.seats.find((item) => item.occupant?.identity === identity);
    if (seat) seat.occupant = null;
    room.participants.delete(identity);
    room.handRaises.delete(identity);
    this.#sessions.delete(participant.sessionId);
    if (participant.disconnectTimer) clearTimeout(participant.disconnectTimer);
    for (const stream of participant.streams) stream.end();
    this.#changed(roomName);
    void this.onDisconnect?.(roomName, identity);
  }

  restoreSeat(roomName: string, seat: SeatState) {
    this.#room(roomName).seats[seat.id - 1] = structuredClone(seat);
    this.#changed(roomName);
  }

  broadcast(roomName: string) {
    const room = this.#room(roomName);
    for (const participant of room.participants.values()) for (const stream of participant.streams) this.#writeSnapshot(roomName, participant, stream);
  }

  #changed(roomName: string) { this.#room(roomName).revision += 1; this.broadcast(roomName); }
  #writeSnapshot(roomName: string, participant: ParticipantState, response: ServerResponse) {
    const snapshot = this.snapshot(roomName, participant.identity);
    response.write(`id: ${snapshot.revision}\ndata: ${JSON.stringify(snapshot)}\n\n`);
  }
  #participant(room: InternalRoom, participant: ParticipantState): ParticipantSnapshot {
    return { userId: participant.userId, identity: participant.identity, name: participant.name, role: participant.role, seatId: room.seats.find((seat) => seat.occupant?.identity === participant.identity)?.id ?? null };
  }
  #room(name: string): InternalRoom {
    let room = this.#rooms.get(name);
    if (!room) { room = { name, revision: 0, messages: [], handRaises: new Map(), kickedUserIds: new Set(), closed: false, seats: Array.from({ length: 12 }, (_, index) => ({ id: index + 1, locked: name === "production-test-odasi" && index === 5, occupant: null })), participants: new Map() }; this.#rooms.set(name, room); }
    return room;
  }
}

function canModerate(role: RoomRole): boolean { return role === "host" || role === "moderator"; }
