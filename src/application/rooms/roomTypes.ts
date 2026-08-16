import type { PublicUser } from "../auth/authTypes.js";

export type RoomRole = "host" | "moderator" | "listener";

export interface RoomInfo {
  id: string;
  slug: string;
  title: string;
  category: string;
  description: string;
  status: "open" | "closed";
  owner: { id: string; username: string };
}

export interface RoomAccess { room: RoomInfo; role: RoomRole }

export interface RoomRepository {
  listOpen(): Promise<RoomInfo[]>;
  findBySlug(slug: string): Promise<RoomInfo | undefined>;
  create(input: { slug: string; title: string; category: string; description: string; owner: PublicUser }): Promise<RoomInfo>;
  getOrJoin(slug: string, user: PublicUser): Promise<RoomAccess | undefined>;
  setMemberRole(slug: string, actorUserId: string, targetUserId: string, role: Exclude<RoomRole, "host">): Promise<RoomAccess | undefined>;
}
