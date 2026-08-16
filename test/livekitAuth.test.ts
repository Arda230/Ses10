import assert from "node:assert/strict";
import { request } from "node:http";
import { test } from "node:test";

import { createHttpServer } from "../src/http/server.js";

const logger = { debug() {}, info() {}, warn() {}, error() {} };
const livekit = {
  url: "wss://example.livekit.cloud",
  apiKey: "test-api-key",
  apiSecret: "test-api-secret-that-is-long-enough-for-hs256",
};

test("LiveKit token endpoint rejects unauthenticated requests", async () => {
  const response = await withServer(undefined, (port) => post(port, {
    roomName: "auth-required-room",
    identity: "attacker-selected-identity",
    name: "Attacker",
  }));
  assert.equal(response.status, 401);
  assert.deepEqual(JSON.parse(response.body), { error: "UNAUTHORIZED" });
});

test("LiveKit token identity and name come only from authenticated user", async () => {
  const user = { id: "123e4567-e89b-12d3-a456-426614174000", username: "laradeniz", email: "lara@example.com", role: "user" as const };
  const response = await withServer(async (incoming) => incoming.headers.cookie === "ses10_session=valid" ? user : undefined, (port) => post(port, {
    roomName: "server-identity-room",
    identity: "attacker-selected-identity",
    name: "Attacker",
  }, "ses10_session=valid"));
  assert.equal(response.status, 200);
  const payload = JSON.parse(response.body) as { token: string; state: { self: { identity: string; name: string } } };
  const claims = JSON.parse(Buffer.from(payload.token.split(".")[1]!, "base64url").toString("utf8")) as { sub?: string; name?: string; video?: { canPublish?: boolean } };
  assert.equal(claims.sub, "user_123e4567e89b12d3a456426614174000");
  assert.equal(claims.name, "laradeniz");
  assert.equal(claims.video?.canPublish, false);
  assert.deepEqual(payload.state.self, {
    identity: "user_123e4567e89b12d3a456426614174000",
    name: "laradeniz",
    role: "host",
    seatId: null,
  });
});

async function withServer<T>(
  authenticate: ((request: import("node:http").IncomingMessage) => Promise<{ id: string; username: string; email: string; role: "user" | "admin" } | undefined>) | undefined,
  run: (port: number) => Promise<T>,
): Promise<T> {
  const server = createHttpServer({ logger, livekit, authenticate });
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  const address = server.address();
  assert.ok(address && typeof address !== "string");
  try {
    return await run(address.port);
  } finally {
    await new Promise<void>((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
  }
}

function post(port: number, payload: object, cookie?: string): Promise<{ status: number; body: string }> {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify(payload);
    const req = request({
      hostname: "127.0.0.1",
      port,
      path: "/api/livekit/token",
      method: "POST",
      headers: { "content-type": "application/json", "content-length": Buffer.byteLength(body), ...(cookie ? { cookie } : {}) },
    }, (response) => {
      let responseBody = "";
      response.setEncoding("utf8");
      response.on("data", (chunk) => { responseBody += chunk; });
      response.on("end", () => resolve({ status: response.statusCode ?? 0, body: responseBody }));
    });
    req.on("error", reject);
    req.end(body);
  });
}
