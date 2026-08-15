import { createServer, type Server } from "node:http";

import type { Logger } from "../shared/logger.js";

interface HttpServerOptions {
  logger: Logger;
}

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

export function createHttpServer({ logger }: HttpServerOptions): Server {
  return createServer((request, response) => {
    const path = new URL(request.url ?? "/", "http://localhost").pathname;

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
