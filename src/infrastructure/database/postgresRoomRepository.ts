import { and, eq, sql } from "drizzle-orm";
import type { NodePgDatabase } from "drizzle-orm/node-postgres";

import type { PublicUser } from "../../application/auth/authTypes.js";
import type { GiftInfo, GiftResult, PublicProfile, RoomAccess, RoomInfo, RoomMessage, RoomRepository, RoomRole } from "../../application/rooms/roomTypes.js";
import * as schema from "./schema.js";

interface RoomRow { id: string; slug: string; title: string; category: string; description: string; status: "open" | "closed"; ownerId: string; ownerUsername: string }

export class PostgresRoomRepository implements RoomRepository {
  constructor(private readonly db: NodePgDatabase<typeof schema>) {}

  async listOpen(): Promise<RoomInfo[]> {
    const rows = await this.db.select(roomSelection).from(schema.rooms).innerJoin(schema.users, eq(schema.rooms.ownerUserId, schema.users.id)).where(eq(schema.rooms.status, "open"));
    return rows.map(toRoomInfo);
  }

  async findBySlug(slug: string): Promise<RoomInfo | undefined> {
    const [row] = await this.db.select(roomSelection).from(schema.rooms).innerJoin(schema.users, eq(schema.rooms.ownerUserId, schema.users.id)).where(eq(schema.rooms.slug, slug)).limit(1);
    return row ? toRoomInfo(row) : undefined;
  }

  async create(input: { slug: string; title: string; category: string; description: string; owner: PublicUser }): Promise<RoomInfo> {
    return this.db.transaction(async (transaction) => {
      const [room] = await transaction.insert(schema.rooms).values({ slug: input.slug, title: input.title, category: input.category, description: input.description, ownerUserId: input.owner.id }).returning();
      if (!room) throw new Error("ROOM_CREATE_FAILED");
      await transaction.insert(schema.roomMembers).values({ roomId: room.id, userId: input.owner.id, role: "host" });
      return { id: room.id, slug: room.slug, title: room.title, category: room.category, description: room.description, status: room.status, owner: { id: input.owner.id, username: input.owner.username } };
    });
  }

  async getOrJoin(slug: string, user: PublicUser): Promise<RoomAccess | undefined> {
    const room = await this.findBySlug(slug);
    if (!room || room.status !== "open") return undefined;
    const [membership] = await this.db.insert(schema.roomMembers).values({ roomId: room.id, userId: user.id, role: room.owner.id === user.id ? "host" : "listener" }).onConflictDoUpdate({ target: [schema.roomMembers.roomId, schema.roomMembers.userId], set: { updatedAt: sql`now()` } }).returning({ role: schema.roomMembers.role });
    return membership ? { room, role: membership.role } : undefined;
  }

  async setMemberRole(slug: string, actorUserId: string, targetUserId: string, role: Exclude<RoomRole, "host">): Promise<RoomAccess | undefined> {
    const room = await this.findBySlug(slug);
    if (!room) return undefined;
    if (room.owner.id !== actorUserId || room.owner.id === targetUserId) throw new Error("FORBIDDEN");
    const [membership] = await this.db.update(schema.roomMembers).set({ role, updatedAt: sql`now()` }).where(and(eq(schema.roomMembers.roomId, room.id), eq(schema.roomMembers.userId, targetUserId))).returning({ role: schema.roomMembers.role });
    return membership ? { room, role: membership.role } : undefined;
  }

  async listMessages(slug: string): Promise<RoomMessage[]> {
    const room = await this.findBySlug(slug); if (!room) throw new Error("NOT_FOUND");
    const rows = await this.db.select().from(schema.roomMessages).where(eq(schema.roomMessages.roomId, room.id)).orderBy(schema.roomMessages.createdAt).limit(100);
    return rows.map((row) => ({ id: row.id, roomId: row.roomId, userId: row.userId, displayName: row.displayName, body: row.body, type: row.type, createdAt: row.createdAt.toISOString() }));
  }

  async addMessage(slug: string, user: PublicUser, body: string, type = "user"): Promise<RoomMessage> {
    const room = await this.findBySlug(slug); if (!room) throw new Error("NOT_FOUND");
    const [row] = await this.db.insert(schema.roomMessages).values({ roomId: room.id, userId: user.id, displayName: user.displayName ?? user.username, body, type }).returning(); if (!row) throw new Error("MESSAGE_FAILED");
    return { id: row.id, roomId: row.roomId, userId: row.userId, displayName: row.displayName, body: row.body, type: row.type, createdAt: row.createdAt.toISOString() };
  }

  async listGifts(): Promise<GiftInfo[]> {
    const rows = await this.db.select().from(schema.giftCatalog).where(eq(schema.giftCatalog.active, 1));
    return rows.map((row) => ({ id: row.id, name: row.name, price: row.price, assetIdentifier: row.assetIdentifier }));
  }

  async sendGift(slug: string, sender: PublicUser, receiverUserId: string, giftId: string, quantity: number, requestId: string): Promise<GiftResult> {
    const room = await this.findBySlug(slug); if (!room) throw new Error("NOT_FOUND"); if (receiverUserId === sender.id || quantity < 1 || quantity > 99) throw new Error("INVALID_GIFT");
    return this.db.transaction(async (tx) => {
      await tx.execute(sql`select pg_advisory_xact_lock(hashtext(${sender.id + ":" + requestId}))`);
      const [existing] = await tx.select().from(schema.giftTransactions).where(and(eq(schema.giftTransactions.senderUserId, sender.id), eq(schema.giftTransactions.requestId, requestId))).limit(1);
      if (existing) { const [account] = await tx.select({ balance: schema.users.balance }).from(schema.users).where(eq(schema.users.id, sender.id)); return { transactionId: existing.id, balance: account?.balance ?? 0, duplicate: true, message: { id: existing.id, roomId: room.id, userId: sender.id, displayName: sender.displayName ?? sender.username, body: "Hediye daha önce gönderildi.", type: "gift", createdAt: existing.createdAt.toISOString() } }; }
      const [gift] = await tx.select().from(schema.giftCatalog).where(and(eq(schema.giftCatalog.id, giftId), eq(schema.giftCatalog.active, 1))).limit(1); if (!gift) throw new Error("INVALID_GIFT");
      const [receiver] = await tx.select({ id: schema.users.id, username: schema.users.username, displayName: schema.users.displayName }).from(schema.users).where(eq(schema.users.id, receiverUserId)).limit(1); if (!receiver) throw new Error("NOT_FOUND");
      const totalCost = gift.price * quantity; const [account] = await tx.update(schema.users).set({ balance: sql`${schema.users.balance} - ${totalCost}` }).where(and(eq(schema.users.id, sender.id), sql`${schema.users.balance} >= ${totalCost}`)).returning({ balance: schema.users.balance }); if (!account) throw new Error("INSUFFICIENT_BALANCE");
      const [transaction] = await tx.insert(schema.giftTransactions).values({ requestId, roomId: room.id, senderUserId: sender.id, receiverUserId, giftId, quantity, totalCost }).returning(); if (!transaction) throw new Error("GIFT_FAILED");
      const text = `${sender.displayName ?? sender.username}, ${receiver.displayName} kullanıcısına ${quantity}× ${gift.name} gönderdi.`; const [message] = await tx.insert(schema.roomMessages).values({ roomId: room.id, userId: sender.id, displayName: sender.displayName ?? sender.username, body: text, type: "gift" }).returning(); if (!message) throw new Error("GIFT_FAILED");
      return { transactionId: transaction.id, balance: account.balance, duplicate: false, message: { id: message.id, roomId: message.roomId, userId: message.userId, displayName: message.displayName, body: message.body, type: message.type, createdAt: message.createdAt.toISOString() } };
    });
  }

  async publicProfile(userId: string): Promise<PublicProfile | undefined> { const [user] = await this.db.select({ id: schema.users.id, username: schema.users.username, displayName: schema.users.displayName, avatarUrl: schema.users.avatarUrl, role: schema.users.role }).from(schema.users).where(eq(schema.users.id, userId)).limit(1); return user; }
  async loadSeatLocks(slug: string): Promise<number[]> { const room = await this.findBySlug(slug); if (!room) throw new Error("NOT_FOUND"); const rows = await this.db.select({ seatId: schema.roomSeats.seatId }).from(schema.roomSeats).where(and(eq(schema.roomSeats.roomId, room.id), eq(schema.roomSeats.locked, 1))); return rows.map((row) => row.seatId); }
  async setSeatLock(slug: string, seatId: number, locked: boolean): Promise<void> { const room = await this.findBySlug(slug); if (!room) throw new Error("NOT_FOUND"); await this.db.insert(schema.roomSeats).values({ roomId: room.id, seatId, locked: locked ? 1 : 0 }).onConflictDoUpdate({ target: [schema.roomSeats.roomId, schema.roomSeats.seatId], set: { locked: locked ? 1 : 0, updatedAt: sql`now()` } }); }
  async closeRoom(slug: string, actorUserId: string): Promise<boolean> { const room = await this.findBySlug(slug); if (!room || room.owner.id !== actorUserId) throw new Error("FORBIDDEN"); const result = await this.db.update(schema.rooms).set({ status: "closed", updatedAt: sql`now()` }).where(eq(schema.rooms.id, room.id)).returning({ id: schema.rooms.id }); return result.length === 1; }
}

const roomSelection = { id: schema.rooms.id, slug: schema.rooms.slug, title: schema.rooms.title, category: schema.rooms.category, description: schema.rooms.description, status: schema.rooms.status, ownerId: schema.users.id, ownerUsername: schema.users.username };
function toRoomInfo(row: RoomRow): RoomInfo {
  return { id: row.id, slug: row.slug, title: row.title, category: row.category, description: row.description, status: row.status, owner: { id: row.ownerId, username: row.ownerUsername } };
}
