import { test } from "node:test";
import assert from "node:assert/strict";
import { request } from "node:http";
import { createHttpServer } from "../src/http/server";

const logger = {
  debug() {},
  info() {},
  warn() {},
  error() {},
};

function get(path: string): Promise<{ statusCode: number; body: string }> {
  return new Promise((resolve, reject) => {
    const server = createHttpServer({ logger });

    server.listen(0, "127.0.0.1", () => {
      const address = server.address();

      if (!address || typeof address === "string") {
        server.close();
        reject(new Error("Server address alınamadı"));
        return;
      }

      const req = request(
        {
          hostname: "127.0.0.1",
          port: address.port,
          path,
          method: "GET",
        },
        (res) => {
          let body = "";

          res.setEncoding("utf8");
          res.on("data", (chunk) => {
            body += chunk;
          });

          res.on("end", () => {
            server.close(() => {
              resolve({
                statusCode: res.statusCode ?? 0,
                body,
              });
            });
          });
        },
      );

      req.on("error", (error) => {
        server.close();
        reject(error);
      });

      req.end();
    });
  });
}

test("GET /health returns ok", async () => {
  const response = await get("/health");

  assert.equal(response.statusCode, 200);
  assert.deepEqual(JSON.parse(response.body), { status: "ok" });
});

test("GET /ready returns ready", async () => {
  const response = await get("/ready");

  assert.equal(response.statusCode, 200);
  assert.deepEqual(JSON.parse(response.body), { status: "ready" });
});
