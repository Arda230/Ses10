import { createServer, type Server } from "node:http";
import type { PublicUser } from "../application/auth/authTypes.js";
import type { RoomRepository } from "../application/rooms/roomTypes.js";
import type { Logger } from "../shared/logger.js";
import { createRoomApi } from "./roomApi.js";

interface HttpServerOptions {
  logger: Logger;
  livekit?: { url: string; apiKey: string; apiSecret: string };
  authApi?: (request: import("node:http").IncomingMessage, response: import("node:http").ServerResponse, path: string) => Promise<boolean>;
  authenticate?: (request: import("node:http").IncomingMessage) => Promise<PublicUser | undefined>;
  roomRepository?: RoomRepository;
}

const MAX_BODY_BYTES = 4_096;
const ROOM_PATTERN = /^[A-Za-z0-9_-]{1,64}$/;
const IDENTITY_PATTERN = /^[A-Za-z0-9_.@-]{1,64}$/;
const rateBuckets = new Map<string, { count: number; resetAt: number }>();
const RATE_LIMITS: Record<string, { limit: number; windowMs: number }> = {
  "/api/auth/login": { limit: 10, windowMs: 60_000 },
  "/api/auth/register": { limit: 5, windowMs: 60_000 },
  "/api/livekit/token": { limit: 20, windowMs: 60_000 },
  "/api/rooms": { limit: 10, windowMs: 60_000 },
};

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

export function createHttpServer({ logger, livekit, authApi, authenticate, roomRepository }: HttpServerOptions): Server {
  const roomApi = createRoomApi(livekit, authenticate, roomRepository);
  return createServer((request, response) => {
    void (async () => {
      const path = new URL(request.url ?? "/", "http://localhost").pathname;
      if (path.startsWith("/api/") && isUnsafe(request.method) && !hasValidOrigin(request)) { respondJson(response, 403, { error: "INVALID_ORIGIN" }); return; }
      if (isRateLimited(request, path)) { response.setHeader("retry-after", "60"); respondJson(response, 429, { error: "RATE_LIMITED" }); return; }
      if (authApi && await authApi(request, response, path)) return;
      if (await roomApi(request, response, path)) return;
      if (request.method === "GET" && path === "/health") { respondJson(response, 200, { status: "ok" }); return; }
      if (request.method === "GET" && path === "/ready") { respondJson(response, 200, { status: "ready" }); return; }
      logger.warn("Route not found", { method: request.method, path });
      respondJson(response, 404, { error: "Not found" });
    })().catch((error: unknown) => {
      logger.error("HTTP request failed", { error: error instanceof Error ? error.message : "unknown" });
      if (!response.headersSent) respondJson(response, 500, { error: "INTERNAL_ERROR" });
      else response.end();
    });
  });
}


function isUnsafe(method: string | undefined): boolean { return method !== "GET" && method !== "HEAD" && method !== "OPTIONS"; }
function hasValidOrigin(request: import("node:http").IncomingMessage): boolean {
  const origin = request.headers.origin;
  if (!origin) return true;
  try { return new URL(origin).host === request.headers.host; } catch { return false; }
}
function isRateLimited(request: import("node:http").IncomingMessage, path: string): boolean {
  if (request.method !== "POST") return false;
  const rule = RATE_LIMITS[path];
  if (!rule) return false;
  const now = Date.now();
  const key = `${request.socket.remoteAddress ?? "unknown"}:${path}`;
  const bucket = rateBuckets.get(key);
  if (!bucket || bucket.resetAt <= now) { rateBuckets.set(key, { count: 1, resetAt: now + rule.windowMs }); return false; }
  bucket.count += 1;
  return bucket.count > rule.limit;
}
