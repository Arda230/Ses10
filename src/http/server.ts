import { createServer, type Server } from "node:http";
import { AccessToken, TrackSource } from "livekit-server-sdk";

import type { Logger } from "../shared/logger.js";

interface HttpServerOptions {
  logger: Logger;
  livekit?: { url: string; apiKey: string; apiSecret: string };
}

const MAX_BODY_BYTES = 4_096;
const ROOM_PATTERN = /^[A-Za-z0-9_-]{1,64}$/;
const IDENTITY_PATTERN = /^[A-Za-z0-9_.@-]{1,64}$/;

function respondJson(
  response: import("node:http").ServerResponse,
  statusCode: number,
  payload: object,
): void {
  response.writeHead(statusCode, {
    "content-type": "application/json; charset=utf-8",
  });
  response.end(JSON.stringify(payload));
}

async function readJsonBody(request: import("node:http").IncomingMessage): Promise<unknown> {
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of request) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    size += buffer.length;
    if (size > MAX_BODY_BYTES) throw new Error("PAYLOAD_TOO_LARGE");
    chunks.push(buffer);
  }
  try { return JSON.parse(Buffer.concat(chunks).toString("utf8")); }
  catch { throw new Error("INVALID_JSON"); }
}

export function createHttpServer({ logger, livekit }: HttpServerOptions): Server {
  return createServer(async (request, response) => {
    const path = new URL(request.url ?? "/", "http://localhost").pathname;

    if (request.method === "GET" && path === "/health") {
      respondJson(response, 200, { status: "ok" });
      return;
    }

    if (request.method === "GET" && path === "/ready") {
      respondJson(response, 200, { status: "ready" });
      return;
    }

    if (request.method === "POST" && path === "/api/livekit/token") {
      if (!livekit) { respondJson(response, 503, { error: "Ses odası servisi yapılandırılmamış." }); return; }
      if (!request.headers["content-type"]?.toLowerCase().startsWith("application/json")) { respondJson(response, 415, { error: "İstek JSON formatında olmalı." }); return; }
      try {
        const body = await readJsonBody(request);
        const roomName = typeof body === "object" && body !== null && "roomName" in body ? (body as { roomName?: unknown }).roomName : undefined;
        const identity = typeof body === "object" && body !== null && "identity" in body ? (body as { identity?: unknown }).identity : undefined;
        if (typeof roomName !== "string" || !ROOM_PATTERN.test(roomName)) { respondJson(response, 400, { error: "Geçerli bir oda adı gerekli." }); return; }
        if (typeof identity !== "string" || !IDENTITY_PATTERN.test(identity)) { respondJson(response, 400, { error: "Geçerli bir kullanıcı kimliği gerekli." }); return; }
        const accessToken = new AccessToken(livekit.apiKey, livekit.apiSecret, { identity, ttl: "10m" });
        accessToken.addGrant({ roomJoin: true, room: roomName, canPublish: true, canPublishSources: [TrackSource.MICROPHONE], canSubscribe: true, canPublishData: false });
        respondJson(response, 200, { token: await accessToken.toJwt(), serverUrl: livekit.url });
      } catch (error) {
        const tooLarge = error instanceof Error && error.message === "PAYLOAD_TOO_LARGE";
        respondJson(response, tooLarge ? 413 : 400, { error: tooLarge ? "İstek gövdesi çok büyük." : "Geçersiz istek gövdesi." });
      }
      return;
    }

    logger.warn("Route not found", { method: request.method, path });
    respondJson(response, 404, { error: "Not found" });
  });
}
