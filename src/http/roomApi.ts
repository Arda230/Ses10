import { randomUUID } from "node:crypto";
import type { IncomingMessage, ServerResponse } from "node:http";
import { AccessToken, RoomServiceClient, TrackSource } from "livekit-server-sdk";

import type { PublicUser } from "../application/auth/authTypes.js";
import type { RoomRepository, RoomRole } from "../application/rooms/roomTypes.js";
import { RoomStateStore } from "../rooms/roomState.js";

interface LiveKitConfig { url: string; apiKey: string; apiSecret: string }
const ROOM_PATTERN = /^[A-Za-z0-9_-]{1,64}$/;

function json(response: ServerResponse, status: number, payload: object) {
  response.writeHead(status, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" });
  response.end(JSON.stringify(payload));
}

async function body(request: IncomingMessage): Promise<Record<string, unknown>> {
  if (!request.headers["content-type"]?.toLowerCase().startsWith("application/json")) throw new Error("INVALID_JSON");
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of request) {
    const value = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    size += value.length;
    if (size > 8_192) throw new Error("PAYLOAD_TOO_LARGE");
    chunks.push(value);
  }
  try {
    const parsed: unknown = JSON.parse(Buffer.concat(chunks).toString("utf8"));
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error();
    return parsed as Record<string, unknown>;
  } catch { throw new Error("INVALID_JSON"); }
}

function errorStatus(error: unknown) {
  if (!(error instanceof Error)) return 400;
  if (error.message === "FORBIDDEN" || error.message === "LOCKED") return 403;
  if (error.message === "OCCUPIED" || error.message === "ALREADY_SEATED" || error.message === "ROOM_EXISTS") return 409;
  if (error.message === "NOT_FOUND") return 404;
  if (error.message === "PAYLOAD_TOO_LARGE") return 413;
  if (["INVALID_JSON", "INVALID_ROOM", "INVALID_MESSAGE", "INVALID_GIFT", "INSUFFICIENT_BALANCE", "NO_AVAILABLE_SEAT"].includes(error.message)) return 400;
  return 500;
}

export function createRoomApi(
  livekit: LiveKitConfig | undefined,
  authenticate: ((request: IncomingMessage) => Promise<PublicUser | undefined>) | undefined,
  repository: RoomRepository | undefined,
  roomState?: RoomStateStore,
) {
  const service = livekit ? new RoomServiceClient(livekit.url, livekit.apiKey, livekit.apiSecret) : undefined;
  const rooms = roomState ?? new RoomStateStore((roomName, identity) => { void service?.removeParticipant(roomName, identity).catch(() => undefined); });

  return async (request: IncomingMessage, response: ServerResponse, path: string): Promise<boolean> => {
    if (path === "/api/rooms" && request.method === "GET") {
      const user = await authenticate?.(request);
      if (!user) { json(response, 401, { error: "UNAUTHORIZED" }); return true; }
      if (!repository) { json(response, 503, { error: "ROOM_SERVICE_UNAVAILABLE" }); return true; }
      const listed = await repository.listOpen();
      json(response, 200, { rooms: listed.map((room) => ({ ...room, onlineCount: rooms.participantCount(room.slug) })) });
      return true;
    }

    if (path === "/api/rooms" && request.method === "POST") {
      const user = await authenticate?.(request);
      if (!user) { json(response, 401, { error: "UNAUTHORIZED" }); return true; }
      if (!repository) { json(response, 503, { error: "ROOM_SERVICE_UNAVAILABLE" }); return true; }
      try {
        const input = await body(request);
        if (typeof input.title !== "string" || input.title.trim().length < 3 || input.title.trim().length > 100) throw new Error("INVALID_ROOM");
        if (typeof input.category !== "string" || input.category.trim().length < 2 || input.category.trim().length > 60) throw new Error("INVALID_ROOM");
        const description = typeof input.description === "string" ? input.description.trim() : "";
        if (description.length > 280) throw new Error("INVALID_ROOM");
        const base = slugify(input.title).slice(0, 54) || "ses-odasi";
        const room = await repository.create({ slug: `${base}-${randomUUID().slice(0, 8)}`, title: input.title.trim(), category: input.category.trim(), description, owner: user });
        json(response, 201, { room });
      } catch (error) { json(response, errorStatus(error), { error: publicError(error) }); }
      return true;
    }

    if (request.method === "GET" && path === "/api/gifts") { const user = await authenticate?.(request); if (!user) { json(response, 401, { error: "UNAUTHORIZED" }); return true; } if (!repository) { json(response, 503, { error: "ROOM_SERVICE_UNAVAILABLE" }); return true; } json(response, 200, { gifts: await repository.listGifts(), balance: user.balance ?? 0 }); return true; }
    const profileMatch = path.match(/^\/api\/users\/([0-9a-f-]{36})$/i);
    if (request.method === "GET" && profileMatch) { const user = await authenticate?.(request); if (!user) { json(response, 401, { error: "UNAUTHORIZED" }); return true; } if (!repository) { json(response, 503, { error: "ROOM_SERVICE_UNAVAILABLE" }); return true; } const profile = await repository.publicProfile(profileMatch[1]!); json(response, profile ? 200 : 404, profile ? { user: profile } : { error: "NOT_FOUND" }); return true; }

    if (request.method === "POST" && path === "/api/livekit/token") {
      if (!livekit) { json(response, 503, { error: "Ses odası servisi yapılandırılmamış." }); return true; }
      if (!repository) { json(response, 503, { error: "ROOM_SERVICE_UNAVAILABLE" }); return true; }
      try {
        const user = await authenticate?.(request);
        if (!user) { json(response, 401, { error: "UNAUTHORIZED" }); return true; }
        const input = await body(request);
        const roomName = input.roomName;
        if (typeof roomName !== "string" || !ROOM_PATTERN.test(roomName)) throw new Error("INVALID_ROOM");
        const access = await repository.getOrJoin(roomName, user);
        if (!access) throw new Error("NOT_FOUND");
        const identity = liveKitIdentity(user.id);
        const wasPresent = Boolean(rooms.participant(roomName, identity));
        const [messages, lockedSeats] = await Promise.all([repository.listMessages(roomName), repository.loadSeatLocks(roomName)]);
        rooms.hydrate(roomName, messages, lockedSeats);
        rooms.join(roomName, user.id, identity, user.username, access.role);
        if (!wasPresent) { const joined = await repository.addMessage(roomName, user, `${user.displayName ?? user.username} odaya katıldı.`, "join"); rooms.appendMessage(roomName, joined); }
        const token = new AccessToken(livekit.apiKey, livekit.apiSecret, { identity, name: user.username, ttl: "10m" });
        token.addGrant({ roomJoin: true, room: roomName, canPublish: false, canSubscribe: true, canPublishData: false });
        json(response, 200, { token: await token.toJwt(), serverUrl: livekit.url, room: access.room, state: rooms.snapshot(roomName, identity) });
      } catch (error) { json(response, errorStatus(error), { error: publicError(error) }); }
      return true;
    }

    const match = path.match(/^\/api\/rooms\/([A-Za-z0-9_-]{1,64})(?:\/(.*))?$/);
    if (!match) return false;
    const roomName = match[1]!;
    const action = match[2] ?? "";
    const user = await authenticate?.(request);
    if (!user) { json(response, 401, { error: "UNAUTHORIZED" }); return true; }
    if (!repository) { json(response, 503, { error: "ROOM_SERVICE_UNAVAILABLE" }); return true; }

    if (request.method === "GET" && action === "") {
      const room = await repository.findBySlug(roomName);
      json(response, room ? 200 : 404, room ? { room } : { error: "NOT_FOUND" });
      return true;
    }

    const authenticatedIdentity = liveKitIdentity(user.id);
    const participant = rooms.participant(roomName, authenticatedIdentity);
    if (!participant || participant.userId !== user.id) { json(response, rooms.isKicked(roomName, user.id) ? 403 : 401, { error: "UNAUTHORIZED" }); return true; }

    if (request.method === "GET" && action === "events") {
      response.writeHead(200, { "content-type": "text/event-stream", "cache-control": "no-cache, no-transform", connection: "keep-alive", "x-accel-buffering": "no" });
      rooms.subscribe(roomName, participant.identity, response);
      return true;
    }

    try {
      const claim = action.match(/^seats\/(\d+)\/claim$/);
      const lock = action.match(/^seats\/(\d+)\/lock$/);
      if (request.method === "POST" && action === "messages") { const input = await body(request); if (typeof input.body !== "string" || input.body.trim().length < 1 || input.body.trim().length > 500) throw new Error("INVALID_MESSAGE"); const message = await repository.addMessage(roomName, user, input.body.trim()); rooms.appendMessage(roomName, message); json(response, 201, { message }); return true; }
      if (request.method === "POST" && action === "gifts") { const input = await body(request); if (typeof input.receiverUserId !== "string" || typeof input.giftId !== "string" || typeof input.requestId !== "string" || !/^[0-9a-f-]{36}$/i.test(input.requestId)) throw new Error("INVALID_GIFT"); if (!rooms.participantByUserId(roomName, input.receiverUserId)) throw new Error("NOT_FOUND"); const result = await repository.sendGift(roomName, user, input.receiverUserId, input.giftId, typeof input.quantity === "number" ? input.quantity : 1, input.requestId); if (!result.duplicate) rooms.appendMessage(roomName, result.message); json(response, 200, result); return true; }
      if (request.method === "POST" && action === "hand-raise") { const input = await body(request); if (typeof input.raised !== "boolean") throw new Error("INVALID_JSON"); rooms.raiseHand(roomName, participant.identity, input.raised); json(response, 200, rooms.snapshot(roomName, participant.identity)); return true; }
      if (request.method === "POST" && action === "hand-raise/resolve") { const input = await body(request); if (typeof input.targetIdentity !== "string" || typeof input.accepted !== "boolean") throw new Error("INVALID_JSON"); const target = rooms.participant(roomName, input.targetIdentity); rooms.resolveHand(roomName, participant.identity, input.targetIdentity, input.accepted, typeof input.seatId === "number" ? input.seatId : undefined); if (input.accepted && target) { const state = rooms.snapshot(roomName, participant.identity); const seat = state.seats.find((item) => item.occupant?.identity === target.identity); const message = await repository.addMessage(roomName, user, `${target.name} Mic ${seat?.id ?? "?"} koltuğuna kabul edildi.`, "hand_accepted"); rooms.appendMessage(roomName, message); } json(response, 200, rooms.snapshot(roomName, participant.identity)); return true; }
      if (request.method === "POST" && action === "kick") { const input = await body(request); if (typeof input.targetIdentity !== "string") throw new Error("INVALID_JSON"); const target = rooms.participant(roomName, input.targetIdentity); rooms.kick(roomName, participant.identity, input.targetIdentity); if (target) { const message = await repository.addMessage(roomName, user, `${target.name} odadan çıkarıldı.`, "kick"); rooms.appendMessage(roomName, message); } await service?.removeParticipant(roomName, input.targetIdentity).catch(() => undefined); json(response, 200, rooms.snapshot(roomName, participant.identity)); return true; }
      if (request.method === "POST" && action === "close") { await repository.closeRoom(roomName, user.id); rooms.closeRoom(roomName, participant.identity); json(response, 200, { status: "closed" }); return true; }
      if (request.method === "POST" && claim) {
        const seatId = Number(claim[1]);
        const previous = structuredClone(rooms.snapshot(roomName, participant.identity).seats[seatId - 1]!);
        rooms.claimSeat(roomName, participant.identity, seatId);
        try { await service?.updateParticipant(roomName, participant.identity, undefined, { canPublish: true, canPublishSources: [TrackSource.MICROPHONE], canSubscribe: true }); }
        catch (error) { rooms.restoreSeat(roomName, previous); throw error; }
        const message = await repository.addMessage(roomName, user, `${user.displayName ?? user.username} Mic ${seatId} koltuğuna geçti.`, "seat_claim"); rooms.appendMessage(roomName, message);
      } else if (request.method === "DELETE" && action === "seat") {
        const input = await body(request);
        const target = typeof input.targetIdentity === "string" ? input.targetIdentity : participant.identity;
        const previous = structuredClone(rooms.snapshot(roomName, participant.identity).seats.find((seat) => seat.occupant?.identity === target));
        rooms.leaveSeat(roomName, participant.identity, target);
        try { await service?.updateParticipant(roomName, target, undefined, { canPublish: false, canSubscribe: true }); }
        catch (error) { if (previous) rooms.restoreSeat(roomName, previous); throw error; }
        if (previous?.occupant) { const type = target === participant.identity ? "seat_leave" : "seat_removed"; const text = target === participant.identity ? `${previous.occupant.name} mikrofondan indi.` : `${previous.occupant.name} host/moderatör tarafından mikrofondan indirildi.`; const message = await repository.addMessage(roomName, user, text, type); rooms.appendMessage(roomName, message); }
      } else if (request.method === "POST" && lock) {
        const input = await body(request);
        if (typeof input.locked !== "boolean") throw new Error("INVALID_JSON");
        const seatId = Number(lock[1]); const previousLocked = rooms.snapshot(roomName, participant.identity).seats[seatId - 1]?.locked ?? false; rooms.setSeatLock(roomName, participant.identity, seatId, input.locked); try { await repository.setSeatLock(roomName, seatId, input.locked); } catch (error) { rooms.setSeatLock(roomName, participant.identity, seatId, previousLocked); throw error; }
      } else if (request.method === "POST" && action === "mute") {
        const input = await body(request);
        if (typeof input.targetIdentity !== "string" || typeof input.muted !== "boolean") throw new Error("INVALID_JSON");
        const previous = structuredClone(rooms.snapshot(roomName, participant.identity).seats.find((seat) => seat.occupant?.identity === input.targetIdentity));
        rooms.setMuted(roomName, participant.identity, input.targetIdentity, input.muted);
        try { await service?.updateParticipant(roomName, input.targetIdentity, undefined, { canPublish: !input.muted, canPublishSources: input.muted ? [] : [TrackSource.MICROPHONE], canSubscribe: true }); }
        catch (error) { if (previous) rooms.restoreSeat(roomName, previous); throw error; }
      } else if (request.method === "POST" && action === "roles") {
        const input = await body(request);
        if (typeof input.targetUserId !== "string" || (input.role !== "moderator" && input.role !== "listener")) throw new Error("INVALID_JSON");
        if (!rooms.participantByUserId(roomName, input.targetUserId)) throw new Error("NOT_FOUND");
        const updated = await repository.setMemberRole(roomName, user.id, input.targetUserId, input.role as Exclude<RoomRole, "host">);
        if (!updated) throw new Error("NOT_FOUND");
        rooms.setRole(roomName, participant.identity, input.targetUserId, updated.role as Exclude<RoomRole, "host">);
      } else if (request.method === "DELETE" && action === "participants/me") {
        const left = await repository.addMessage(roomName, user, `${user.displayName ?? user.username} odadan ayrıldı.`, "leave"); rooms.appendMessage(roomName, left);
        rooms.disconnect(roomName, participant.identity);
        await service?.removeParticipant(roomName, participant.identity).catch(() => undefined);
        json(response, 200, { status: "left" });
        return true;
      } else { json(response, 404, { error: "NOT_FOUND" }); return true; }
      json(response, 200, rooms.snapshot(roomName, participant.identity));
    } catch (error) { json(response, errorStatus(error), { error: publicError(error) }); }
    return true;
  };
}

function liveKitIdentity(userId: string): string { return "user_" + userId.replaceAll("-", ""); }
function slugify(value: string): string {
  return value.trim().toLocaleLowerCase("tr-TR").normalize("NFKD").replace(/[\u0300-\u036f]/g, "").replace(/ı/g, "i").replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

function publicError(error: unknown): string {
  const allowed = new Set(["FORBIDDEN", "LOCKED", "OCCUPIED", "ALREADY_SEATED", "NOT_FOUND", "PAYLOAD_TOO_LARGE", "INVALID_JSON", "INVALID_ROOM", "INVALID_MESSAGE", "INVALID_GIFT", "INSUFFICIENT_BALANCE", "NO_AVAILABLE_SEAT"]);
  return error instanceof Error && allowed.has(error.message) ? error.message : "INTERNAL_ERROR";
}
