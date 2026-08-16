import assert from "node:assert/strict";
import { request } from "node:http";
import { test } from "node:test";

import { AuthError, AuthService } from "../src/application/auth/authService.js";
import type { AuthRepository, PublicUser, UserEntity } from "../src/application/auth/authTypes.js";
import { createAuthApi } from "../src/http/authApi.js";
import { createHttpServer } from "../src/http/server.js";

class MemoryAuthRepository implements AuthRepository {
  users: UserEntity[] = [];
  sessions = new Map<string, { userId: string; expiresAt: Date; revokedAt?: Date }>();

  findUserByLogin(login: string) { return Promise.resolve(this.users.find((user) => user.email === login || user.username.toLowerCase() === login)); }
  createUser(input: { username: string; usernameNormalized: string; email: string; emailNormalized: string; passwordHash: string }) {
    if (this.users.some((user) => user.email === input.emailNormalized || user.username.toLowerCase() === input.usernameNormalized)) return Promise.reject(Object.assign(new Error("duplicate"), { code: "23505" }));
    const user: UserEntity = { id: `user-${this.users.length + 1}`, username: input.username, email: input.email, passwordHash: input.passwordHash, role: "user", status: "active" };
    this.users.push(user);
    return Promise.resolve(user);
  }
  createSession(input: { userId: string; tokenHash: string; expiresAt: Date }) { this.sessions.set(input.tokenHash, { userId: input.userId, expiresAt: input.expiresAt }); return Promise.resolve(); }
  findUserBySessionHash(hash: string, now: Date): Promise<PublicUser | undefined> {
    const session = this.sessions.get(hash);
    const user = session && !session.revokedAt && session.expiresAt > now ? this.users.find((item) => item.id === session.userId) : undefined;
    return Promise.resolve(user ? { id: user.id, username: user.username, email: user.email, role: user.role } : undefined);
  }
  revokeSession(hash: string, now: Date) { const session = this.sessions.get(hash); if (session) session.revokedAt = now; return Promise.resolve(); }
  touchLogin() { return Promise.resolve(); }
}

test("register hashes password with Argon2id and creates a usable session", async () => {
  const repository = new MemoryAuthRepository();
  const service = new AuthService(repository, 3600);
  const result = await service.register({ username: "Lara_Deniz", email: "LARA@example.com", password: "guvenli-bir-sifre-123" });
  assert.equal(result.user.email, "lara@example.com");
  assert.match(repository.users[0]?.passwordHash ?? "", /^\$argon2id\$/);
  assert.notEqual(repository.users[0]?.passwordHash, "guvenli-bir-sifre-123");
  assert.deepEqual(await service.me(result.token), result.user);
});

test("login rejects invalid credentials and logout revokes session", async () => {
  const repository = new MemoryAuthRepository();
  const service = new AuthService(repository, 3600);
  await service.register({ username: "laradeniz", email: "lara@example.com", password: "guvenli-bir-sifre-123" });
  await assert.rejects(() => service.login({ login: "lara@example.com", password: "yanlis-password" }), (error: unknown) => error instanceof AuthError && error.code === "INVALID_CREDENTIALS");
  const loggedIn = await service.login({ login: "LaraDeniz", password: "guvenli-bir-sifre-123" });
  await service.logout(loggedIn.token);
  assert.equal(await service.me(loggedIn.token), undefined);
});

test("auth HTTP endpoints set HttpOnly cookie and expose me/logout flow", async () => {
  const repository = new MemoryAuthRepository();
  const service = new AuthService(repository, 3600);
  const authApi = createAuthApi(service, { cookieName: "ses10_session", cookieSecure: true, sessionTtlSeconds: 3600 });
  const server = createHttpServer({ logger: { debug() {}, info() {}, warn() {}, error() {} }, authApi });
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  const address = server.address();
  assert.ok(address && typeof address !== "string");

  try {
    const register = await http(address.port, "POST", "/api/auth/register", { username: "laradeniz", email: "lara@example.com", password: "guvenli-bir-sifre-123" });
    assert.equal(register.status, 201);
    const setCookie = register.headers["set-cookie"]?.[0] ?? "";
    assert.match(setCookie, /HttpOnly/);
    assert.match(setCookie, /Secure/);
    assert.match(setCookie, /SameSite=Lax/);
    const sessionCookie = setCookie.split(";")[0]!;

    const me = await http(address.port, "GET", "/api/auth/me", undefined, sessionCookie);
    assert.equal(JSON.parse(me.body).user.username, "laradeniz");

    const logout = await http(address.port, "POST", "/api/auth/logout", {}, sessionCookie);
    assert.equal(logout.status, 204);
    assert.match(logout.headers["set-cookie"]?.[0] ?? "", /Max-Age=0/);
    const afterLogout = await http(address.port, "GET", "/api/auth/me", undefined, sessionCookie);
    assert.equal(JSON.parse(afterLogout.body).user, null);
  } finally {
    await new Promise<void>((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
  }
});

function http(port: number, method: string, path: string, payload?: object, cookie?: string): Promise<{ status: number; body: string; headers: import("node:http").IncomingHttpHeaders }> {
  return new Promise((resolve, reject) => {
    const body = payload === undefined ? undefined : JSON.stringify(payload);
    const req = request({ hostname: "127.0.0.1", port, method, path, headers: { ...(body ? { "content-type": "application/json", "content-length": Buffer.byteLength(body) } : {}), ...(cookie ? { cookie } : {}) } }, (response) => {
      let responseBody = "";
      response.setEncoding("utf8");
      response.on("data", (chunk) => { responseBody += chunk; });
      response.on("end", () => resolve({ status: response.statusCode ?? 0, body: responseBody, headers: response.headers }));
    });
    req.on("error", reject);
    req.end(body);
  });
}
