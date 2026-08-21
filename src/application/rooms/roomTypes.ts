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

export interface RoomMessage { id: string; roomId: string; userId: string | null; displayName: string; body: string; type: string; createdAt: string }
export interface GiftInfo { id: string; name: string; price: number; assetIdentifier: string }
export interface GiftResult { transactionId: string; balance: number; message: RoomMessage; duplicate: boolean }
export interface PublicProfile { id: string; username: string; displayName: string; avatarUrl: string | null; role: "user" | "admin" }

export interface RoomAccess { room: RoomInfo; role: RoomRole }

export interface RoomRepository {
  listOpen(): Promise<RoomInfo[]>;
  findBySlug(slug: string): Promise<RoomInfo | undefined>;
  create(input: { slug: string; title: string; category: string; description: string; owner: PublicUser }): Promise<RoomInfo>;
  getOrJoin(slug: string, user: PublicUser): Promise<RoomAccess | undefined>;
  setMemberRole(slug: string, actorUserId: string, targetUserId: string, role: Exclude<RoomRole, "host">): Promise<RoomAccess | undefined>;
  listMessages(slug: string): Promise<RoomMessage[]>;
  addMessage(slug: string, user: PublicUser, body: string, type?: string): Promise<RoomMessage>;
  listGifts(): Promise<GiftInfo[]>;
  sendGift(slug: string, sender: PublicUser, receiverUserId: string, giftId: string, quantity: number, requestId: string): Promise<GiftResult>;
  publicProfile(userId: string): Promise<PublicProfile | undefined>;
  loadSeatLocks(slug: string): Promise<number[]>;
  setSeatLock(slug: string, seatId: number, locked: boolean): Promise<void>;
  closeRoom(slug: string, actorUserId: string): Promise<boolean>;
}
