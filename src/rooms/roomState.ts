import { randomUUID } from "node:crypto";
import type { ServerResponse } from "node:http";

import type { RoomRole } from "../application/rooms/roomTypes.js";

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
  joinTimer: NodeJS.Timeout | undefined;
}

interface InternalRoom {
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
    const existing = room.participants.get(identity);
    if (existing) {
      existing.name = name;
      existing.role = role;
      existing.userId = userId;
      if (existing.disconnectTimer) clearTimeout(existing.disconnectTimer);
      existing.disconnectTimer = undefined;
      return existing;
    }
    const participant: ParticipantState = { userId, identity, name, role, sessionId: randomUUID(), streams: new Set(), disconnectTimer: undefined, joinTimer: undefined };
    participant.joinTimer = setTimeout(() => { if (participant.streams.size === 0) this.disconnect(roomName, identity); }, 30_000);
    participant.joinTimer.unref();
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
    return { roomName, revision: room.revision, seats: room.seats, participantCount: participants.length, participants, self: this.#participant(room, participant) };
  }

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
    if (seat.locked && participant.role === "listener") throw new Error("LOCKED");
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
    if (participant.joinTimer) clearTimeout(participant.joinTimer);
    participant.joinTimer = undefined;
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
    this.#sessions.delete(participant.sessionId);
    if (participant.disconnectTimer) clearTimeout(participant.disconnectTimer);
    if (participant.joinTimer) clearTimeout(participant.joinTimer);
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
    if (!room) { room = { name, revision: 0, seats: Array.from({ length: 12 }, (_, index) => ({ id: index + 1, locked: false, occupant: null })), participants: new Map() }; this.#rooms.set(name, room); }
    return room;
  }
}

function canModerate(role: RoomRole): boolean { return role === "host" || role === "moderator"; }
