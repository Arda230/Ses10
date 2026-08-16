import { index, pgEnum, pgTable, text, timestamp, uniqueIndex, uuid, varchar } from "drizzle-orm/pg-core";

export const accountRole = pgEnum("account_role", ["user", "admin"]);
export const accountStatus = pgEnum("account_status", ["active", "disabled"]);
export const roomStatus = pgEnum("room_status", ["open", "closed"]);
export const roomMemberRole = pgEnum("room_member_role", ["host", "moderator", "listener"]);

export const users = pgTable("users", {
  id: uuid("id").primaryKey().defaultRandom(),
  username: varchar("username", { length: 40 }).notNull(),
  usernameNormalized: varchar("username_normalized", { length: 40 }).notNull(),
  email: varchar("email", { length: 254 }).notNull(),
  emailNormalized: varchar("email_normalized", { length: 254 }).notNull(),
  passwordHash: text("password_hash").notNull(),
  role: accountRole("role").notNull().default("user"),
  status: accountStatus("status").notNull().default("active"),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
  lastLoginAt: timestamp("last_login_at", { withTimezone: true }),
}, (table) => [
  uniqueIndex("users_username_normalized_unique").on(table.usernameNormalized),
  uniqueIndex("users_email_normalized_unique").on(table.emailNormalized),
]);

export const sessions = pgTable("sessions", {
  id: uuid("id").primaryKey().defaultRandom(),
  userId: uuid("user_id").notNull().references(() => users.id, { onDelete: "cascade" }),
  tokenHash: varchar("token_hash", { length: 64 }).notNull(),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  lastSeenAt: timestamp("last_seen_at", { withTimezone: true }).notNull().defaultNow(),
  expiresAt: timestamp("expires_at", { withTimezone: true }).notNull(),
  revokedAt: timestamp("revoked_at", { withTimezone: true }),
}, (table) => [
  uniqueIndex("sessions_token_hash_unique").on(table.tokenHash),
  index("sessions_user_id_idx").on(table.userId),
  index("sessions_expires_at_idx").on(table.expiresAt),
]);

export const rooms = pgTable("rooms", {
  id: uuid("id").primaryKey().defaultRandom(),
  slug: varchar("slug", { length: 64 }).notNull(),
  title: varchar("title", { length: 100 }).notNull(),
  category: varchar("category", { length: 60 }).notNull(),
  description: varchar("description", { length: 280 }).notNull().default(""),
  ownerUserId: uuid("owner_user_id").notNull().references(() => users.id, { onDelete: "restrict" }),
  status: roomStatus("status").notNull().default("open"),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
}, (table) => [
  uniqueIndex("rooms_slug_unique").on(table.slug),
  index("rooms_owner_user_id_idx").on(table.ownerUserId),
  index("rooms_status_idx").on(table.status),
]);

export const roomMembers = pgTable("room_members", {
  id: uuid("id").primaryKey().defaultRandom(),
  roomId: uuid("room_id").notNull().references(() => rooms.id, { onDelete: "cascade" }),
  userId: uuid("user_id").notNull().references(() => users.id, { onDelete: "cascade" }),
  role: roomMemberRole("role").notNull().default("listener"),
  joinedAt: timestamp("joined_at", { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
}, (table) => [
  uniqueIndex("room_members_room_user_unique").on(table.roomId, table.userId),
  index("room_members_room_id_idx").on(table.roomId),
  index("room_members_user_id_idx").on(table.userId),
]);

export type UserRecord = typeof users.$inferSelect;
export type NewUserRecord = typeof users.$inferInsert;
export type SessionRecord = typeof sessions.$inferSelect;
export type RoomRecord = typeof rooms.$inferSelect;
export type RoomMemberRecord = typeof roomMembers.$inferSelect;
