import type { IncomingMessage, ServerResponse } from "node:http";
import { AccessToken, RoomServiceClient, TrackSource } from "livekit-server-sdk";

import { RoomStateStore } from "../rooms/roomState.js";

interface LiveKitConfig { url: string; apiKey: string; apiSecret: string }
const ROOM_PATTERN = /^[A-Za-z0-9_-]{1,64}$/;
const IDENTITY_PATTERN = /^[A-Za-z0-9_.@-]{1,64}$/;
const rooms = new RoomStateStore();

function json(response: ServerResponse, status: number, payload: object) {
  response.writeHead(status, { "content-type": "application/json; charset=utf-8" });
  response.end(JSON.stringify(payload));
}

async function body(request: IncomingMessage): Promise<Record<string, unknown>> {
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of request) {
    const value = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    size += value.length;
    if (size > 4_096) throw new Error("PAYLOAD_TOO_LARGE");
    chunks.push(value);
  }
  const parsed: unknown = JSON.parse(Buffer.concat(chunks).toString("utf8"));
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("INVALID_JSON");
  return parsed as Record<string, unknown>;
}

function session(request: IncomingMessage) {
  return request.headers.cookie?.split(";").map((item) => item.trim()).find((item) => item.startsWith("ses10_room_session="))?.slice("ses10_room_session=".length);
}

function errorStatus(error: unknown) {
  if (!(error instanceof Error)) return 400;
  if (error.message === "FORBIDDEN" || error.message === "LOCKED") return 403;
  if (error.message === "OCCUPIED" || error.message === "ALREADY_SEATED") return 409;
  if (error.message === "NOT_FOUND") return 404;
  return 400;
}

export function createRoomApi(livekit: LiveKitConfig | undefined) {
  const service = livekit ? new RoomServiceClient(livekit.url, livekit.apiKey, livekit.apiSecret) : undefined;

  return async (request: IncomingMessage, response: ServerResponse, path: string): Promise<boolean> => {
    if (request.method === "POST" && path === "/api/livekit/token") {
      if (!livekit) { json(response, 503, { error: "Ses odası servisi yapılandırılmamış." }); return true; }
      try {
        const input = await body(request);
        const roomName = input.roomName;
        const identity = input.identity;
        const name = input.name;
        if (typeof roomName !== "string" || !ROOM_PATTERN.test(roomName)) throw new Error("INVALID_ROOM");
        if (typeof identity !== "string" || !IDENTITY_PATTERN.test(identity)) throw new Error("INVALID_IDENTITY");
        if (typeof name !== "string" || name.trim().length < 1 || name.trim().length > 40) throw new Error("INVALID_NAME");
        const participant = rooms.join(roomName, identity, name.trim());
        const token = new AccessToken(livekit.apiKey, livekit.apiSecret, { identity, name: name.trim(), ttl: "10m" });
        token.addGrant({ roomJoin: true, room: roomName, canPublish: false, canSubscribe: true, canPublishData: false });
        response.setHeader("set-cookie", `ses10_room_session=${participant.sessionId}; HttpOnly; SameSite=Strict; Path=/api; Max-Age=3600`);
        json(response, 200, { token: await token.toJwt(), serverUrl: livekit.url, state: rooms.snapshot(roomName, identity) });
      } catch (error) {
        json(response, errorStatus(error), { error: error instanceof Error ? error.message : "INVALID_REQUEST" });
      }
      return true;
    }

    const match = path.match(/^\/api\/rooms\/([A-Za-z0-9_-]{1,64})(?:\/(.*))?$/);
    if (!match) return false;
    const roomName = match[1]!;
    const action = match[2] ?? "";
    const participant = rooms.authenticate(session(request), roomName);
    if (!participant) { json(response, 401, { error: "UNAUTHORIZED" }); return true; }

    if (request.method === "GET" && action === "events") {
      response.writeHead(200, { "content-type": "text/event-stream", "cache-control": "no-cache", connection: "keep-alive" });
      rooms.subscribe(roomName, participant.identity, response);
      return true;
    }

    try {
      const claim = action.match(/^seats\/(\d+)\/claim$/);
      const lock = action.match(/^seats\/(\d+)\/lock$/);
      if (request.method === "POST" && claim) {
        rooms.claimSeat(roomName, participant.identity, Number(claim[1]));
        await service?.updateParticipant(roomName, participant.identity, undefined, { canPublish: true, canPublishSources: [TrackSource.MICROPHONE], canSubscribe: true });
      } else if (request.method === "DELETE" && action === "seat") {
        const input = await body(request);
        const target = typeof input.targetIdentity === "string" ? input.targetIdentity : participant.identity;
        rooms.leaveSeat(roomName, participant.identity, target);
        await service?.updateParticipant(roomName, target, undefined, { canPublish: false, canSubscribe: true });
      } else if (request.method === "POST" && lock) {
        const input = await body(request);
        if (typeof input.locked !== "boolean") throw new Error("INVALID_JSON");
        rooms.setSeatLock(roomName, participant.identity, Number(lock[1]), input.locked);
      } else if (request.method === "POST" && action === "mute") {
        const input = await body(request);
        if (typeof input.targetIdentity !== "string" || typeof input.muted !== "boolean") throw new Error("INVALID_JSON");
        rooms.setMuted(roomName, participant.identity, input.targetIdentity, input.muted);
        await service?.updateParticipant(roomName, input.targetIdentity, undefined, { canPublish: !input.muted, canPublishSources: input.muted ? [] : [TrackSource.MICROPHONE], canSubscribe: true });
      } else if (request.method === "DELETE" && action === "participants/me") {
        rooms.disconnect(roomName, participant.identity);
        await service?.updateParticipant(roomName, participant.identity, undefined, { canPublish: false }).catch(() => undefined);
        json(response, 200, { status: "left" });
        return true;
      } else {
        json(response, 404, { error: "NOT_FOUND" });
        return true;
      }
      json(response, 200, rooms.snapshot(roomName, participant.identity));
    } catch (error) {
      json(response, errorStatus(error), { error: error instanceof Error ? error.message : "INVALID_REQUEST" });
    }
    return true;
  };
}
