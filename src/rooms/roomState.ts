import { randomUUID } from "node:crypto";
import type { ServerResponse } from "node:http";

export type RoomRole = "host" | "moderator" | "listener";

export interface SeatState {
  id: number;
  locked: boolean;
  occupant: null | { identity: string; name: string; muted: boolean; forceMuted: boolean };
}

export interface RoomSnapshot {
  roomName: string;
  seats: SeatState[];
  participants: number;
  self: { identity: string; name: string; role: RoomRole; seatId: number | null };
}

interface ParticipantState {
  identity: string;
  name: string;
  role: RoomRole;
  sessionId: string;
  streams: Set<ServerResponse>;
  disconnectTimer: NodeJS.Timeout | undefined;
}

interface InternalRoom {
  name: string;
  seats: SeatState[];
  participants: Map<string, ParticipantState>;
}

export class RoomStateStore {
  readonly #rooms = new Map<string, InternalRoom>();
  readonly #sessions = new Map<string, { roomName: string; identity: string }>();

  join(roomName: string, identity: string, name: string) {
    const room = this.#room(roomName);
    const existing = room.participants.get(identity);
    if (existing) return existing;
    const participant: ParticipantState = {
      identity,
      name,
      role: room.participants.size === 0 ? "host" : "listener",
      sessionId: randomUUID(),
      streams: new Set(),
      disconnectTimer: undefined,
    };
    room.participants.set(identity, participant);
    this.#sessions.set(participant.sessionId, { roomName, identity });
    this.broadcast(roomName);
    return participant;
  }

  authenticate(sessionId: string | undefined, roomName: string) {
    if (!sessionId) return undefined;
    const session = this.#sessions.get(sessionId);
    if (!session || session.roomName !== roomName) return undefined;
    return this.#room(roomName).participants.get(session.identity);
  }

  snapshot(roomName: string, identity: string): RoomSnapshot {
    const room = this.#room(roomName);
    const participant = room.participants.get(identity);
    if (!participant) throw new Error("UNAUTHORIZED");
    return {
      roomName,
      seats: room.seats,
      participants: room.participants.size,
      self: {
        identity,
        name: participant.name,
        role: participant.role,
        seatId: room.seats.find((seat) => seat.occupant?.identity === identity)?.id ?? null,
      },
    };
  }

  claimSeat(roomName: string, identity: string, seatId: number) {
    const room = this.#room(roomName);
    const participant = room.participants.get(identity);
    const seat = room.seats[seatId - 1];
    if (!participant || !seat) throw new Error("NOT_FOUND");
    if (seat.locked && participant.role !== "host") throw new Error("LOCKED");
    if (seat.occupant && seat.occupant.identity !== identity) throw new Error("OCCUPIED");
    const current = room.seats.find((item) => item.occupant?.identity === identity);
    if (current && current.id !== seatId) throw new Error("ALREADY_SEATED");
    seat.occupant = { identity, name: participant.name, muted: true, forceMuted: false };
    this.broadcast(roomName);
    return this.snapshot(roomName, identity);
  }

  leaveSeat(roomName: string, identity: string, targetIdentity = identity) {
    const room = this.#room(roomName);
    const actor = room.participants.get(identity);
    if (!actor) throw new Error("UNAUTHORIZED");
    if (targetIdentity !== identity && actor.role !== "host") throw new Error("FORBIDDEN");
    const seat = room.seats.find((item) => item.occupant?.identity === targetIdentity);
    if (seat) seat.occupant = null;
    this.broadcast(roomName);
  }

  setSeatLock(roomName: string, identity: string, seatId: number, locked: boolean) {
    const room = this.#room(roomName);
    const actor = room.participants.get(identity);
    const seat = room.seats[seatId - 1];
    if (!actor || actor.role !== "host") throw new Error("FORBIDDEN");
    if (!seat) throw new Error("NOT_FOUND");
    seat.locked = locked;
    this.broadcast(roomName);
  }

  setMuted(roomName: string, identity: string, targetIdentity: string, muted: boolean) {
    const room = this.#room(roomName);
    const actor = room.participants.get(identity);
    const seat = room.seats.find((item) => item.occupant?.identity === targetIdentity);
    if (!actor || !seat) throw new Error("NOT_FOUND");
    if (identity !== targetIdentity && actor.role !== "host") throw new Error("FORBIDDEN");
    if (identity === targetIdentity && seat.occupant!.forceMuted && !muted) throw new Error("FORBIDDEN");
    if (identity !== targetIdentity) seat.occupant!.forceMuted = muted;
    seat.occupant!.muted = muted;
    this.broadcast(roomName);
  }

  subscribe(roomName: string, identity: string, response: ServerResponse) {
    const participant = this.#room(roomName).participants.get(identity);
    if (!participant) throw new Error("UNAUTHORIZED");
    participant.streams.add(response);
    if (participant.disconnectTimer) clearTimeout(participant.disconnectTimer);
    participant.disconnectTimer = undefined;
    response.write(`data: ${JSON.stringify(this.snapshot(roomName, identity))}\n\n`);
    response.on("close", () => {
      participant.streams.delete(response);
      if (participant.streams.size === 0) participant.disconnectTimer = setTimeout(() => this.disconnect(roomName, identity), 15_000);
    });
  }

  disconnect(roomName: string, identity: string) {
    const room = this.#room(roomName);
    const participant = room.participants.get(identity);
    if (!participant) return;
    this.leaveSeat(roomName, identity);
    room.participants.delete(identity);
    this.#sessions.delete(participant.sessionId);
    if (participant.disconnectTimer) clearTimeout(participant.disconnectTimer);
    for (const stream of participant.streams) stream.end();
    this.broadcast(roomName);
  }

  broadcast(roomName: string) {
    const room = this.#room(roomName);
    for (const participant of room.participants.values()) {
      const message = `data: ${JSON.stringify(this.snapshot(roomName, participant.identity))}\n\n`;
      for (const stream of participant.streams) stream.write(message);
    }
  }

  #room(name: string): InternalRoom {
    let room = this.#rooms.get(name);
    if (!room) {
      room = {
        name,
        seats: Array.from({ length: 12 }, (_, index) => ({ id: index + 1, locked: false, occupant: null })),
        participants: new Map(),
      };
      this.#rooms.set(name, room);
    }
    return room;
  }
}
