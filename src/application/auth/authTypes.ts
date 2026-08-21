export interface PublicUser {
  id: string;
  username: string;
  displayName?: string | undefined;
  avatarUrl?: string | null | undefined;
  email: string;
  role: "user" | "admin";
  balance?: number | undefined;
}

export interface AuthenticatedSession {
  user: PublicUser;
  token: string;
  expiresAt: Date;
}

export interface UserEntity extends PublicUser {
  passwordHash: string;
  status: "active" | "disabled";
}

export interface AuthRepository {
  findUserByLogin(normalizedLogin: string): Promise<UserEntity | undefined>;
  createUser(input: { username: string; usernameNormalized: string; email: string; emailNormalized: string; passwordHash: string }): Promise<UserEntity>;
  createSession(input: { userId: string; tokenHash: string; expiresAt: Date }): Promise<void>;
  findUserBySessionHash(tokenHash: string, now: Date): Promise<PublicUser | undefined>;
  revokeSession(tokenHash: string, now: Date): Promise<void>;
  touchLogin(userId: string, now: Date): Promise<void>;
}
