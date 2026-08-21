import { createHash, randomBytes } from "node:crypto";
import argon2 from "argon2";

import type { AuthenticatedSession, AuthRepository, PublicUser, UserEntity } from "./authTypes.js";

export class AuthError extends Error {
  constructor(public readonly code: "VALIDATION_ERROR" | "CONFLICT" | "INVALID_CREDENTIALS", message: string) {
    super(message);
  }
}

const normalize = (value: string) => value.trim().normalize("NFKC").toLocaleLowerCase("en-US");
const tokenHash = (token: string) => createHash("sha256").update(token).digest("hex");

export class AuthService {
  constructor(private readonly repository: AuthRepository, private readonly sessionTtlSeconds: number) {}

  async register(input: { username: string; email: string; password: string }): Promise<AuthenticatedSession> {
    const username = input.username.trim().normalize("NFKC");
    const email = normalize(input.email);
    if (!/^[\p{L}\p{N}_.-]{3,40}$/u.test(username)) throw new AuthError("VALIDATION_ERROR", "Kullanıcı adı 3-40 karakter olmalı.");
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254) throw new AuthError("VALIDATION_ERROR", "Geçerli bir e-posta adresi gerekli.");
    if (input.password.length < 12 || input.password.length > 128) throw new AuthError("VALIDATION_ERROR", "Şifre 12-128 karakter olmalı.");

    const passwordHash = await argon2.hash(input.password, { type: argon2.argon2id, memoryCost: 19_456, timeCost: 2, parallelism: 1 });
    let user: UserEntity;
    try {
      user = await this.repository.createUser({ username, usernameNormalized: normalize(username), email, emailNormalized: email, passwordHash });
    } catch (error) {
      if (isUniqueViolation(error)) throw new AuthError("CONFLICT", "E-posta veya kullanıcı adı zaten kullanılıyor.");
      throw error;
    }
    return this.issueSession(user);
  }

  async login(input: { login: string; password: string }): Promise<AuthenticatedSession> {
    const user = await this.repository.findUserByLogin(normalize(input.login));
    const valid = user ? await argon2.verify(user.passwordHash, input.password).catch(() => false) : await fakeVerify(input.password);
    if (!user || !valid || user.status !== "active") throw new AuthError("INVALID_CREDENTIALS", "Kullanıcı bilgileri doğrulanamadı.");
    await this.repository.touchLogin(user.id, new Date());
    return this.issueSession(user);
  }

  me(token: string | undefined): Promise<PublicUser | undefined> {
    if (!token) return Promise.resolve(undefined);
    return this.repository.findUserBySessionHash(tokenHash(token), new Date());
  }

  async logout(token: string | undefined): Promise<void> {
    if (token) await this.repository.revokeSession(tokenHash(token), new Date());
  }

  private async issueSession(user: UserEntity): Promise<AuthenticatedSession> {
    const token = randomBytes(32).toString("base64url");
    const expiresAt = new Date(Date.now() + this.sessionTtlSeconds * 1_000);
    await this.repository.createSession({ userId: user.id, tokenHash: tokenHash(token), expiresAt });
    return { user: publicUser(user), token, expiresAt };
  }
}

function publicUser(user: UserEntity): PublicUser {
  return {
    id: user.id,
    username: user.username,
    email: user.email,
    role: user.role,
    ...(user.displayName !== undefined ? { displayName: user.displayName } : {}),
    ...(user.avatarUrl !== undefined ? { avatarUrl: user.avatarUrl } : {}),
    ...(user.balance !== undefined ? { balance: user.balance } : {}),
  };
}

function isUniqueViolation(error: unknown): boolean {
  return typeof error === "object" && error !== null && "code" in error && (error as { code?: unknown }).code === "23505";
}

let fallbackHash: Promise<string> | undefined;
async function fakeVerify(password: string): Promise<boolean> {
  fallbackHash ??= argon2.hash("ses10-invalid-credential-placeholder", { type: argon2.argon2id, memoryCost: 19_456, timeCost: 2, parallelism: 1 });
  await argon2.verify(await fallbackHash, password).catch(() => false);
  return false;
}
