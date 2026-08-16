import type { IncomingMessage, ServerResponse } from "node:http";

import { AuthError, AuthService } from "../application/auth/authService.js";

interface AuthHttpConfig {
  cookieName: string;
  cookieSecure: boolean;
  sessionTtlSeconds: number;
}

export function createAuthApi(service: AuthService, config: AuthHttpConfig) {
  return async (request: IncomingMessage, response: ServerResponse, path: string): Promise<boolean> => {
    if (!path.startsWith("/api/auth/")) return false;

    try {
      if (request.method === "POST" && path === "/api/auth/register") {
        const input = await readJson(request);
        if (typeof input.username !== "string" || typeof input.email !== "string" || typeof input.password !== "string") throw new AuthError("VALIDATION_ERROR", "Kullanıcı adı, e-posta ve şifre gerekli.");
        const result = await service.register({ username: input.username, email: input.email, password: input.password });
        setSessionCookie(response, config, result.token);
        respondJson(response, 201, { user: result.user });
        return true;
      }

      if (request.method === "POST" && path === "/api/auth/login") {
        const input = await readJson(request);
        if (typeof input.login !== "string" || typeof input.password !== "string") throw new AuthError("VALIDATION_ERROR", "Kullanıcı bilgileri gerekli.");
        const result = await service.login({ login: input.login, password: input.password });
        setSessionCookie(response, config, result.token);
        respondJson(response, 200, { user: result.user });
        return true;
      }

      if (request.method === "POST" && path === "/api/auth/logout") {
        await service.logout(readCookie(request, config.cookieName));
        clearSessionCookie(response, config);
        response.writeHead(204);
        response.end();
        return true;
      }

      if (request.method === "GET" && path === "/api/auth/me") {
        const user = await service.me(readCookie(request, config.cookieName));
        respondJson(response, 200, { user: user ?? null });
        return true;
      }

      respondJson(response, 404, { error: { code: "NOT_FOUND", message: "Endpoint bulunamadı." } });
    } catch (error) {
      if (error instanceof AuthError) {
        const status = error.code === "CONFLICT" ? 409 : error.code === "INVALID_CREDENTIALS" ? 401 : 400;
        respondJson(response, status, { error: { code: error.code, message: error.message } });
      } else if (error instanceof Error && error.message === "PAYLOAD_TOO_LARGE") {
        respondJson(response, 413, { error: { code: "PAYLOAD_TOO_LARGE", message: "İstek gövdesi çok büyük." } });
      } else {
        respondJson(response, 500, { error: { code: "INTERNAL_ERROR", message: "İşlem tamamlanamadı." } });
      }
    }
    return true;
  };
}

async function readJson(request: IncomingMessage): Promise<Record<string, unknown>> {
  if (!request.headers["content-type"]?.toLowerCase().startsWith("application/json")) throw new AuthError("VALIDATION_ERROR", "İstek JSON formatında olmalı.");
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of request) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    size += buffer.length;
    if (size > 8_192) throw new Error("PAYLOAD_TOO_LARGE");
    chunks.push(buffer);
  }
  try {
    const parsed: unknown = JSON.parse(Buffer.concat(chunks).toString("utf8"));
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error();
    return parsed as Record<string, unknown>;
  } catch {
    throw new AuthError("VALIDATION_ERROR", "Geçersiz JSON gövdesi.");
  }
}

export function readCookie(request: IncomingMessage, name: string): string | undefined {
  const prefix = `${name}=`;
  return request.headers.cookie?.split(";").map((item) => item.trim()).find((item) => item.startsWith(prefix))?.slice(prefix.length);
}

function setSessionCookie(response: ServerResponse, config: AuthHttpConfig, token: string): void {
  const secure = config.cookieSecure ? "; Secure" : "";
  response.setHeader("set-cookie", `${config.cookieName}=${token}; HttpOnly${secure}; SameSite=Lax; Path=/; Max-Age=${config.sessionTtlSeconds}`);
}

function clearSessionCookie(response: ServerResponse, config: AuthHttpConfig): void {
  const secure = config.cookieSecure ? "; Secure" : "";
  response.setHeader("set-cookie", `${config.cookieName}=; HttpOnly${secure}; SameSite=Lax; Path=/; Max-Age=0`);
}

function respondJson(response: ServerResponse, status: number, payload: object): void {
  response.writeHead(status, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" });
  response.end(JSON.stringify(payload));
}
