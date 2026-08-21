import type { PublicUser } from "../src/application/auth/authTypes.js";
import type { RoomAccess, RoomInfo, RoomRepository, RoomRole } from "../src/application/rooms/roomTypes.js";

export class MemoryRoomRepository implements RoomRepository {
  readonly rooms = new Map<string, RoomInfo>();
  readonly roles = new Map<string, RoomRole>();

  seed(owner: PublicUser, slug = "server-identity-room", title = "Test Odası"): RoomInfo {
    const room: RoomInfo = { id: `room-${this.rooms.size + 1}`, slug, title, category: "Sohbet", description: "", status: "open", owner: { id: owner.id, username: owner.username } };
    this.rooms.set(slug, room);
    this.roles.set(`${slug}:${owner.id}`, "host");
    return room;
  }
  listOpen() { return Promise.resolve([...this.rooms.values()].filter((room) => room.status === "open")); }
  findBySlug(slug: string) { return Promise.resolve(this.rooms.get(slug)); }
  create(input: { slug: string; title: string; category: string; description: string; owner: PublicUser }) {
    const room: RoomInfo = { id: `room-${this.rooms.size + 1}`, slug: input.slug, title: input.title, category: input.category, description: input.description, status: "open", owner: { id: input.owner.id, username: input.owner.username } };
    this.rooms.set(room.slug, room); this.roles.set(`${room.slug}:${input.owner.id}`, "host"); return Promise.resolve(room);
  }
  getOrJoin(slug: string, user: PublicUser): Promise<RoomAccess | undefined> {
    const room = this.rooms.get(slug); if (!room || room.status !== "open") return Promise.resolve(undefined);
    const key = `${slug}:${user.id}`; const role = this.roles.get(key) ?? (room.owner.id === user.id ? "host" : "listener"); this.roles.set(key, role);
    return Promise.resolve({ room, role });
  }
  setMemberRole(slug: string, actorUserId: string, targetUserId: string, role: Exclude<RoomRole, "host">): Promise<RoomAccess | undefined> {
    const room = this.rooms.get(slug); if (!room) return Promise.resolve(undefined);
    if (room.owner.id !== actorUserId || room.owner.id === targetUserId) return Promise.reject(new Error("FORBIDDEN"));
    this.roles.set(`${slug}:${targetUserId}`, role); return Promise.resolve({ room, role });
  }
  listMessages() { return Promise.resolve([]); }
  addMessage(slug: string, user: PublicUser, body: string, type = "user") { return Promise.resolve({ id: "message-1", roomId: this.rooms.get(slug)?.id ?? "", userId: user.id, displayName: user.displayName ?? user.username, body, type, createdAt: new Date().toISOString() }); }
  listGifts() { return Promise.resolve([]); }
  sendGift() { return Promise.reject(new Error("NOT_IMPLEMENTED")); }
  publicProfile() { return Promise.resolve(undefined); }
  loadSeatLocks() { return Promise.resolve([]); }
  setSeatLock() { return Promise.resolve(); }
  closeRoom() { return Promise.resolve(true); }
}
