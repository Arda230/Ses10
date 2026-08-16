import { createServer, type Server } from "node:http";
import type { PublicUser } from "../application/auth/authTypes.js";
import type { Logger } from "../shared/logger.js";
import { createRoomApi } from "./roomApi.js";

interface HttpServerOptions {
  logger: Logger;
  livekit?: { url: string; apiKey: string; apiSecret: string };
  authApi?: (request: import("node:http").IncomingMessage, response: import("node:http").ServerResponse, path: string) => Promise<boolean>;
  authenticate?: (request: import("node:http").IncomingMessage) => Promise<PublicUser | undefined>;
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

export function createHttpServer({ logger, livekit, authApi, authenticate }: HttpServerOptions): Server {
  const roomApi = createRoomApi(livekit, authenticate);
  return createServer(async (request, response) => {
    const path = new URL(request.url ?? "/", "http://localhost").pathname;
    if (authApi && await authApi(request, response, path)) return;
    if (await roomApi(request, response, path)) return;

    if (request.method === "GET" && path === "/health") {
      respondJson(response, 200, { status: "ok" });
      return;
    }

    if (request.method === "GET" && path === "/ready") {
      respondJson(response, 200, { status: "ready" });
      return;
    }

    logger.warn("Route not found", { method: request.method, path });
    respondJson(response, 404, { error: "Not found" });
  });
}
