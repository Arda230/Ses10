export interface CurrentUser { id: string; username: string; email: string; role: "user" | "admin" }
export interface RoomInfo { id: string; slug: string; title: string; category: string; description: string; status: "open" | "closed"; owner: { id: string; username: string } }

export class ApiError extends Error {
  constructor(public readonly status: number, message: string, public readonly code = "REQUEST_FAILED") { super(message); }
}

export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, { credentials: "same-origin", ...init });
  if (response.status === 204) return undefined as T;
  const payload = await response.json().catch(() => ({})) as { error?: string | { code?: string; message?: string } } & T;
  if (!response.ok) {
    const code = typeof payload.error === "string" ? payload.error : payload.error?.code ?? "REQUEST_FAILED";
    const message = typeof payload.error === "object" ? payload.error.message : undefined;
    throw new ApiError(response.status, message ?? errorMessage(code), code);
  }
  return payload;
}

export const jsonRequest = <T>(path: string, method: string, value: object) => api<T>(path, { method, headers: { "content-type": "application/json" }, body: JSON.stringify(value) });
export const getSession = () => api<{ user: CurrentUser | null }>("/api/auth/me");
export const getRooms = () => api<{ rooms: RoomInfo[] }>("/api/rooms");

function errorMessage(code: string): string {
  const messages: Record<string, string> = { UNAUTHORIZED: "Oturumun sona erdi. Lütfen yeniden giriş yap.", NOT_FOUND: "Oda bulunamadı veya artık açık değil.", CONFLICT: "Bu bilgilerle kayıtlı bir hesap zaten var.", INVALID_CREDENTIALS: "Kullanıcı adı/e-posta veya şifre hatalı.", VALIDATION_ERROR: "Lütfen bilgilerini kontrol et." };
  return messages[code] ?? "İşlem tamamlanamadı. Lütfen tekrar dene.";
}
