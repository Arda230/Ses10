import { and, eq, sql } from "drizzle-orm";
import type { NodePgDatabase } from "drizzle-orm/node-postgres";

import type { PublicUser } from "../../application/auth/authTypes.js";
import type { RoomAccess, RoomInfo, RoomRepository, RoomRole } from "../../application/rooms/roomTypes.js";
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
}

const roomSelection = { id: schema.rooms.id, slug: schema.rooms.slug, title: schema.rooms.title, category: schema.rooms.category, description: schema.rooms.description, status: schema.rooms.status, ownerId: schema.users.id, ownerUsername: schema.users.username };
function toRoomInfo(row: RoomRow): RoomInfo {
  return { id: row.id, slug: row.slug, title: row.title, category: row.category, description: row.description, status: row.status, owner: { id: row.ownerId, username: row.ownerUsername } };
}
