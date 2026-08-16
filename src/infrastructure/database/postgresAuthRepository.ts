import { and, eq, gt, isNull, or, sql } from "drizzle-orm";
import type { NodePgDatabase } from "drizzle-orm/node-postgres";

import type { AuthRepository, PublicUser, UserEntity } from "../../application/auth/authTypes.js";
import * as schema from "./schema.js";

export class PostgresAuthRepository implements AuthRepository {
  constructor(private readonly db: NodePgDatabase<typeof schema>) {}

  async findUserByLogin(normalizedLogin: string): Promise<UserEntity | undefined> {
    const record = await this.db.query.users.findFirst({
      where: or(eq(schema.users.emailNormalized, normalizedLogin), eq(schema.users.usernameNormalized, normalizedLogin)),
    });
    return record ? toEntity(record) : undefined;
  }

  async createUser(input: { username: string; usernameNormalized: string; email: string; emailNormalized: string; passwordHash: string }): Promise<UserEntity> {
    const [record] = await this.db.insert(schema.users).values(input).returning();
    if (!record) throw new Error("USER_CREATE_FAILED");
    return toEntity(record);
  }

  async createSession(input: { userId: string; tokenHash: string; expiresAt: Date }): Promise<void> {
    await this.db.insert(schema.sessions).values(input);
  }

  async findUserBySessionHash(tokenHash: string, now: Date): Promise<PublicUser | undefined> {
    const [row] = await this.db.select({
      id: schema.users.id,
      username: schema.users.username,
      email: schema.users.email,
      role: schema.users.role,
      status: schema.users.status,
    }).from(schema.sessions).innerJoin(schema.users, eq(schema.sessions.userId, schema.users.id)).where(and(
      eq(schema.sessions.tokenHash, tokenHash),
      isNull(schema.sessions.revokedAt),
      gt(schema.sessions.expiresAt, now),
      eq(schema.users.status, "active"),
    )).limit(1);
    if (!row || row.status !== "active") return undefined;
    await this.db.update(schema.sessions).set({ lastSeenAt: now }).where(eq(schema.sessions.tokenHash, tokenHash));
    return { id: row.id, username: row.username, email: row.email, role: row.role };
  }

  async revokeSession(tokenHash: string, now: Date): Promise<void> {
    await this.db.update(schema.sessions).set({ revokedAt: now }).where(and(eq(schema.sessions.tokenHash, tokenHash), isNull(schema.sessions.revokedAt)));
  }

  async touchLogin(userId: string, now: Date): Promise<void> {
    await this.db.update(schema.users).set({ lastLoginAt: now, updatedAt: sql`now()` }).where(eq(schema.users.id, userId));
  }
}

function toEntity(record: schema.UserRecord): UserEntity {
  return {
    id: record.id,
    username: record.username,
    email: record.email,
    passwordHash: record.passwordHash,
    role: record.role,
    status: record.status,
  };
}
