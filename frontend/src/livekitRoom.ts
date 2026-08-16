import { Room, RoomEvent, Track } from "livekit-client";

type Role = "host" | "moderator" | "listener";
interface SeatState { id: number; locked: boolean; occupant: null | { identity: string; name: string; muted: boolean; forceMuted: boolean } }
interface Snapshot { roomName: string; seats: SeatState[]; participants: number; self: { identity: string; name: string; role: Role; seatId: number | null } }
interface TokenResponse { token: string; serverUrl: string; state: Snapshot }

const ROOM_NAME = "geceye-bir-sarki";

export function bindLiveKitRoom(): void {
  const join = document.querySelector<HTMLButtonElement>("[data-room-join]");
  const mic = document.querySelector<HTMLButtonElement>("[data-mic-toggle]");
  const leave = document.querySelector<HTMLButtonElement>(".leave-button");
  const status = document.querySelector<HTMLElement>("[data-room-status]");
  const grid = document.querySelector<HTMLElement>("[data-seat-grid]");
  const hostControls = document.querySelector<HTMLElement>("[data-host-controls]");
  const audio = document.querySelector<HTMLElement>("[data-remote-audio]");
  let room: Room | undefined;
  let events: EventSource | undefined;
  let state: Snapshot | undefined;
  let selectedSeatId: number | undefined;
  let speakers = new Set<string>();

  const show = (message: string, isError = false) => {
    if (!status) return;
    status.textContent = message;
    status.classList.toggle("is-error", isError);
  };

  const api = async <T>(path: string, init?: RequestInit): Promise<T> => {
    const response = await fetch(path, init);
    const payload = await response.json() as T & { error?: string };
    if (!response.ok) {
      const messages: Record<string, string> = {
        OCCUPIED: "Bu mic koltuğu başka bir kullanıcı tarafından alındı.",
        ALREADY_SEATED: "Aynı anda yalnızca bir mic koltuğunda olabilirsin.",
        LOCKED: "Bu mic koltuğu host tarafından kilitlendi.",
        FORBIDDEN: "Bu işlem için yetkin yok.",
        UNAUTHORIZED: "Oda oturumun sona erdi. Yeniden katıl.",
      };
      throw new Error(messages[payload.error ?? ""] ?? "İşlem tamamlanamadı.");
    }
    return payload;
  };

  const requestJson = <T>(path: string, method: string, value: object = {}) => api<T>(path, {
    method,
    headers: { "content-type": "application/json" },
    body: JSON.stringify(value),
  });

  const initials = (name: string) => name.split(/\s+/).slice(0, 2).map((part) => part[0]?.toLocaleUpperCase("tr-TR") ?? "").join("");

  const render = () => {
    if (!state || !grid) return;
    grid.innerHTML = state.seats.map((seat) => {
      const occupant = seat.occupant;
      const own = occupant?.identity === state?.self.identity;
      const speaking = occupant ? speakers.has(occupant.identity) && !occupant.muted : false;
      const classes = ["seat", occupant ? "occupied" : "empty", seat.locked ? "is-locked" : "", own ? "is-own" : "", speaking ? "is-speaking" : "", selectedSeatId === seat.id ? "is-selected" : ""].filter(Boolean).join(" ");
      const content = occupant
        ? `<span>${initials(occupant.name)}</span><b>${escapeHtml(occupant.name)}</b><small>${occupant.forceMuted ? "host mute" : occupant.muted ? "mic kapalı" : speaking ? "konuşuyor" : "mic açık"}</small>`
        : `<span>${seat.locked ? "⌁" : "+"}</span><b>Mic ${seat.id}</b><small>${seat.locked ? "kilitli" : "boş"}</small>`;
      return `<button class="${classes}" type="button" data-seat-id="${seat.id}" aria-label="Mic ${seat.id}">${content}</button>`;
    }).join("");
    const memberList = document.querySelector<HTMLElement>("[data-member-list]");
    const occupied = state.seats.flatMap((seat) => seat.occupant ? [seat.occupant] : []);
    const visibleMembers = occupied.some((item) => item.identity === state?.self.identity) ? occupied : [...occupied, { identity: state.self.identity, name: state.self.name, muted: true, forceMuted: false }];
    if (memberList) memberList.innerHTML = visibleMembers.map((member) => {
      const ownRole = member.identity === state?.self.identity ? state.self.role : "listener";
      const role = ownRole === "host" ? `<em class="role-host">♛ HOST</em>` : ownRole === "moderator" ? `<em class="role-mod">✦ MOD</em>` : `<em>${member.muted ? "⌁" : "♫"}</em>`;
      return `<article><span class="member-avatar ${ownRole === "host" ? "cyan" : "violet"}">${initials(member.name)}</span><span><b>${escapeHtml(member.name)}</b><small>${member.muted ? "Dinliyor" : "Konuşmacı"}</small></span>${role}</article>`;
    }).join("");
    const memberTotal = document.querySelector<HTMLElement>("[data-member-total]");
    const speakerTotal = document.querySelector<HTMLElement>("[data-speaker-total]");
    if (memberTotal) memberTotal.textContent = String(state.participants);
    if (speakerTotal) speakerTotal.textContent = String(occupied.length);
    const participantCount = document.querySelector<HTMLElement>("[data-participant-count]");
    if (participantCount) participantCount.textContent = String(state.participants);
    if (hostControls) hostControls.hidden = state.self.role !== "host";
    const ownSeat = state.seats.find((seat) => seat.occupant?.identity === state?.self.identity);
    if (mic) {
      mic.disabled = !ownSeat || Boolean(ownSeat.occupant?.forceMuted);
      mic.classList.toggle("is-muted", !room?.localParticipant.isMicrophoneEnabled);
    }
  };

  const leaveSeat = async (targetIdentity?: string) => {
    await requestJson<Snapshot>(`/api/rooms/${ROOM_NAME}/seat`, "DELETE", targetIdentity ? { targetIdentity } : {});
    if (!targetIdentity || targetIdentity === state?.self.identity) await room?.localParticipant.setMicrophoneEnabled(false);
  };

  grid?.addEventListener("click", async (event) => {
    const button = (event.target as HTMLElement).closest<HTMLButtonElement>("[data-seat-id]");
    if (!button || !state) return;
    const seatId = Number(button.dataset.seatId);
    const seat = state.seats[seatId - 1];
    if (!seat) return;
    selectedSeatId = seatId;
    render();
    try {
      if (seat.occupant?.identity === state.self.identity) await leaveSeat();
      else if (!seat.occupant) await requestJson<Snapshot>(`/api/rooms/${ROOM_NAME}/seats/${seatId}/claim`, "POST");
    } catch (error) { show(error instanceof Error ? error.message : "Mic işlemi başarısız.", true); }
  });

  document.querySelector<HTMLButtonElement>("[data-host-lock]")?.addEventListener("click", async () => {
    const seat = state?.seats.find((item) => item.id === selectedSeatId);
    if (!seat) { show("Önce bir mic koltuğu seç.", true); return; }
    try { await requestJson(`/api/rooms/${ROOM_NAME}/seats/${seat.id}/lock`, "POST", { locked: !seat.locked }); }
    catch (error) { show(error instanceof Error ? error.message : "Kilit değiştirilemedi.", true); }
  });

  document.querySelector<HTMLButtonElement>("[data-host-mute]")?.addEventListener("click", async () => {
    const occupant = state?.seats.find((item) => item.id === selectedSeatId)?.occupant;
    if (!occupant) { show("Mute etmek için dolu bir mic seç.", true); return; }
    try { await requestJson(`/api/rooms/${ROOM_NAME}/mute`, "POST", { targetIdentity: occupant.identity, muted: true }); }
    catch (error) { show(error instanceof Error ? error.message : "Kullanıcı mute edilemedi.", true); }
  });

  document.querySelector<HTMLButtonElement>("[data-host-remove]")?.addEventListener("click", async () => {
    const occupant = state?.seats.find((item) => item.id === selectedSeatId)?.occupant;
    if (!occupant) { show("İndirmek için dolu bir mic seç.", true); return; }
    try { await leaveSeat(occupant.identity); }
    catch (error) { show(error instanceof Error ? error.message : "Kullanıcı mic’ten indirilemedi.", true); }
  });

  join?.addEventListener("click", async () => {
    join.disabled = true;
    show("Ses odasına listener olarak bağlanılıyor…");
    try {
      const token = await requestJson<TokenResponse>("/api/livekit/token", "POST", { roomName: ROOM_NAME });
      state = token.state;
      room = new Room({ audioCaptureDefaults: { echoCancellation: true, noiseSuppression: true } });
      room.on(RoomEvent.TrackSubscribed, (track) => { if (track.kind === Track.Kind.Audio && audio) audio.appendChild(track.attach()); });
      room.on(RoomEvent.TrackUnsubscribed, (track) => track.detach().forEach((element) => element.remove()));
      room.on(RoomEvent.ActiveSpeakersChanged, (active) => { speakers = new Set(active.map((item) => item.identity)); render(); });
      room.on(RoomEvent.Disconnected, () => {
        events?.close(); events = undefined; room = undefined;
        void requestJson(`/api/rooms/${ROOM_NAME}/participants/me`, "DELETE").catch(() => undefined);
        if (mic) mic.disabled = true; if (leave) leave.disabled = true; if (join) join.disabled = false;
        show("Ses odasından ayrıldın.");
      });
      await room.connect(token.serverUrl, token.token, { autoSubscribe: true });
      await room.startAudio();
      events = new EventSource(`/api/rooms/${ROOM_NAME}/events`);
      events.onmessage = (message) => {
        state = JSON.parse(message.data) as Snapshot;
        const ownSeat = state.seats.find((seat) => seat.occupant?.identity === state?.self.identity);
        if ((!ownSeat || ownSeat.occupant?.muted) && room?.localParticipant.isMicrophoneEnabled) void room.localParticipant.setMicrophoneEnabled(false);
        render();
      };
      events.onerror = () => show("Oda durumu yeniden bağlanıyor…", true);
      if (leave) leave.disabled = false;
      show(`${state.self.role === "host" ? "Host" : "Listener"} olarak bağlandın. Bir mic seçebilirsin.`);
      render();
    } catch (error) { join.disabled = false; show(error instanceof Error ? error.message : "Ses odasına bağlanılamadı.", true); }
  });

  mic?.addEventListener("click", async () => {
    if (!room || !state?.self.seatId) return;
    mic.disabled = true;
    try {
      const enabled = !room.localParticipant.isMicrophoneEnabled;
      if (enabled) await requestJson(`/api/rooms/${ROOM_NAME}/mute`, "POST", { targetIdentity: state.self.identity, muted: false });
      await room.localParticipant.setMicrophoneEnabled(enabled);
      if (!enabled) await requestJson(`/api/rooms/${ROOM_NAME}/mute`, "POST", { targetIdentity: state.self.identity, muted: true });
      mic.setAttribute("aria-pressed", String(enabled));
      show(enabled ? "Mikrofonun açık." : "Mikrofonun kapalı.");
    } catch (error) { show(error instanceof Error ? error.message : "Mikrofon erişimi sağlanamadı.", true); }
    finally { render(); }
  });

  document.querySelector<HTMLButtonElement>("[data-leave-seat]")?.addEventListener("click", () => {
    if (!state?.self.seatId) { show("Şu anda bir mic koltuğunda değilsin."); return; }
    void leaveSeat().catch((error) => show(error instanceof Error ? error.message : "Mic’ten inilemedi.", true));
  });
  leave?.addEventListener("click", () => room?.disconnect());
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[character] ?? character);
}
