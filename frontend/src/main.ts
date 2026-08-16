import "./style.css";
import { Room, RoomEvent, Track } from "livekit-client";
import { roomCard, rankingRow } from "./components/markup";
import { people, rooms } from "./data/fixtures";
import { mockConversations } from "./state/mockState";
import { adminView, aiView, authView, messagesView, profileView, roomView } from "./views/routeViews";
const roomMarkup = rooms.map(roomCard).join("");
const peopleMarkup = people.map(rankingRow).join("");
document.querySelector<HTMLDivElement>("#app")!.innerHTML = `
<div class="app"><header class="navbar"><a class="logo" href="#top"><span class="logo-mark">S</span><span>ses<span>on</span></span></a><nav><a class="active" href="#top">Keşfet</a><a href="#rooms">Canlı odalar</a><a href="#popular">Popüler</a><a href="#rankings">Sıralama</a><a href="#events">Etkinlikler</a></nav><div class="nav-right"><span class="live-status"><i></i> 2.4K kişi online</span><button class="login" data-open-auth>Giriş yap</button><button class="join" data-open-auth>Kayıt ol <span>↗</span></button><button class="hamburger">☰</button></div></header>
<main id="top"><section class="hero"><div class="hero-copy"><span class="eyebrow"><i></i> SESİNİ DUYUR</span><h1>Sesin burada<br /><span>hayat bulur.</span></h1><p>Dinle. Konuş. Bağlan.<br />SesOn'da herkesin söyleyecek bir şeyi var.</p><div class="hero-buttons"><button class="join big" data-open-auth>Hemen keşfet <span>↗</span></button><a href="#rooms"><span class="play">▶</span> Nasıl çalışır?</a></div><div class="mini-proof"><div><span>AY</span><span>MK</span><span>ED</span><span>+10K</span></div><small>10.000+ kişi sesini paylaşıyor</small></div></div><div class="hero-center"><div class="wave wave-a"></div><div class="wave wave-b"></div><div class="wave wave-c"></div><div class="mic-halo"><div class="mic"><span class="mic-top"></span><span class="mic-stem"></span><span class="mic-foot"></span></div></div><span class="music-note note-a">♪</span><span class="music-note note-b">♫</span><span class="signal signal-a"></span><span class="signal signal-b"></span><div class="now-playing"><span class="equalizer"><i></i><i></i><i></i><i></i><i></i></span><span><small>ŞİMDİ DİNLENİYOR</small><b>Sesinle bağlan</b></span><strong>03:42</strong></div></div><aside class="auth-card"><div class="auth-top"><span class="auth-dot"></span><small>SESON'A KATIL</small><span class="auth-lock">⌁</span></div><h2>Kendi sesini<br /><em>bul.</em></h2><p>Binlerce sohbete katıl,<br />hikâyeni paylaş.</p><div class="auth-tabs"><button class="selected">Giriş yap</button><button>Kayıt ol</button></div><form class="inline-form"><label>E-posta adresin</label><input type="email" placeholder="ornek@email.com" required /><button class="join" type="submit">Devam et <span>→</span></button></form><small class="terms">Devam ederek Kullanım Koşulları'nı kabul edersin.</small><div class="social-login"><span>veya</span><button type="button">G <b>Google ile devam et</b></button></div></aside></section>
<section class="metrics"><div><small>AKTİF KULLANICI</small><b>10K<span>+</span></b></div><div><small>CANLI ODA</small><b>2.4K</b></div><div><small>PAYLAŞILAN DAKİKA</small><b>365K</b></div><div><small>TOPLULUK PUANI</small><b>4.9 <span class="stars">★★★★★</span></b></div></section>
<section class="section" id="rooms"><div class="section-head"><div><span class="eyebrow">ŞİMDİ CANLI</span><h2>Bir odaya <em>katıl.</em></h2></div><a href="#rooms">Tüm odaları gör <span>→</span></a></div><div class="rooms-grid">${roomMarkup}</div></section>
<section class="section dark-section" id="popular"><div class="section-head"><div><span class="eyebrow">TOPLULUKTAN</span><h2>Popüler <em>içerikler.</em></h2></div><div class="filter-tabs"><button class="active">Bugün</button><button>Bu hafta</button><button>Yeni</button></div></div><div class="content-columns"><article class="featured-content"><div class="content-art"><span class="content-ring"></span><span class="content-avatar">İD</span><span class="content-live">● 12.4K dinliyor</span></div><div><span class="eyebrow">SESON ORIGINALS</span><h3>Birlikte daha güzel</h3><p>İrem Derici ile müzik, hayat ve bize iyi gelen şeyler üzerine.</p><button class="text-link">Dinlemeye başla <span>→</span></button></div></article><div class="topic-list"><article><span class="topic-icon">◉</span><div><b>Gece Sohbetleri</b><small>4.2K dinliyor · 18 dk</small></div><span>▶</span></article><article><span class="topic-icon purple">✦</span><div><b>Yeni Başlangıçlar</b><small>2.8K dinliyor · 32 dk</small></div><span>▶</span></article><article><span class="topic-icon cyan">♫</span><div><b>Şehrin Sesleri</b><small>1.9K dinliyor · 24 dk</small></div><span>▶</span></article></div></div></section>
<section class="section split-section" id="rankings"><div class="ranking-box"><div class="section-head compact"><div><span class="eyebrow">HAFTANIN SESLERİ</span><h2>Sıralama</h2></div><button class="round">↗</button></div><ol>${peopleMarkup}</ol></div><div class="quote-box"><span class="quote">“</span><h3>Sesini paylaş.<br /><em>Dünyaya bağlan.</em></h3><p>Burada her sesin bir hikâyesi, her hikâyenin bir dinleyicisi var.</p><div class="quote-bars"><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i></div></div></section>
<section class="events" id="events"><div><span class="eyebrow">TAKVİMİNDE YER AÇ</span><h2>Bir sonraki<br /><em>buluşma burada.</em></h2><p>Canlı yayınlar, özel konuklar ve topluluğun en sevdiği etkinlikler.</p><button class="outline">Tüm etkinlikler <span>→</span></button></div><div class="event-list"><article><time>18 <small>AĞU</small></time><span>ÖZEL YAYIN</span><h3>Sesin Hikayesi</h3><p>Konuk: İrem Derici · 21:00</p></article><article><time>24 <small>AĞU</small></time><span>TOPLULUK</span><h3>Gece Sohbetleri</h3><p>Herkes davetli · 22:30</p></article></div></section></main><footer><a class="logo" href="#top"><span class="logo-mark">S</span><span>ses<span>on</span></span></a><p>Sesini paylaş. Dünyaya bağlan.</p><div><a href="#rooms">Keşfet</a><a href="#popular">Popüler</a><a href="#events">Etkinlikler</a><a href="#">Yardım</a></div><small>© 2024 SesOn</small></footer></div>`;


const app = document.querySelector<HTMLDivElement>("#app")!;
const landingMarkup = app.innerHTML;

type Route = "landing" | "auth" | "room" | "profile" | "messages" | "nova" | "lina" | "admin";
const routeFromLocation = (): Route => {
  const pathname = window.location.pathname.replace(/\/$/, "") || "/";
  const pathRoutes: Record<string, Route> = { "/auth": "auth", "/room": "room", "/profile": "profile", "/messages": "messages", "/nova": "nova", "/lina": "lina", "/admin": "admin" };
  if (pathRoutes[pathname]) return pathRoutes[pathname];
  const hash = window.location.hash.replace("#", "");
  if (hash === "auth") return "auth";
  if (hash === "room") return "room";
  if (hash === "profile") return "profile";
  if (hash === "messages") return "messages";
  if (hash === "nova") return "nova";
  if (hash === "lina") return "lina";
  if (hash === "admin") return "admin";
  return "landing";
};

function navigate(path: string): void {
  window.history.pushState({}, "", path);
  renderRoute();
}

function bindLandingInteractions(): void {
  const form = document.querySelector<HTMLFormElement>(".inline-form");
  form?.addEventListener("submit", (event) => event.preventDefault());
  document.querySelectorAll<HTMLButtonElement>("[data-open-auth]").forEach((button) => button.addEventListener("click", () => { navigate("/auth"); }));
  document.querySelector<HTMLButtonElement>(".hamburger")?.addEventListener("click", () => document.querySelector("nav")?.classList.toggle("open"));
  document.querySelectorAll<HTMLButtonElement>(".auth-tabs button").forEach((tab) => tab.addEventListener("click", () => {
    document.querySelectorAll(".auth-tabs button").forEach((item) => item.classList.remove("selected"));
    tab.classList.add("selected");
    const submit = document.querySelector<HTMLButtonElement>(".inline-form .join");
    if (submit) submit.innerHTML = `${tab.textContent?.includes("Kayıt") ? "Hesap oluştur" : "Devam et"} <span>→</span>`;
  }));
  document.querySelectorAll<HTMLButtonElement>(".filter-tabs button").forEach((tab) => tab.addEventListener("click", () => {
    document.querySelectorAll(".filter-tabs button").forEach((item) => item.classList.remove("active"));
    tab.classList.add("active");
  }));
  document.querySelectorAll<HTMLElement>(".room-card").forEach((card) => {
    card.setAttribute("tabindex", "0"); card.setAttribute("role", "button");
    const select = () => { if (card.classList.toggle("is-selected")) navigate("/room"); };
    card.addEventListener("click", select);
    card.addEventListener("keydown", (event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); select(); } });
  });
}

function bindAuthInteractions(): void {
  document.querySelectorAll<HTMLButtonElement>("[data-auth-tab]").forEach((tab) => tab.addEventListener("click", () => {
    document.querySelectorAll("[data-auth-tab]").forEach((item) => item.classList.remove("active"));
    tab.classList.add("active");
    const signup = tab.dataset.authTab === "signup";
    document.querySelector<HTMLElement>("[data-auth-heading]")!.innerHTML = signup ? "SesOn'a <em>katıl.</em>" : "Tekrar <em>hoş geldin.</em>";
    document.querySelector<HTMLElement>("[data-auth-copy]")!.textContent = signup ? "Topluluğa katılmak için bilgilerini gir." : "SesOn'a devam etmek için e-posta adresini gir.";
    document.querySelector<HTMLButtonElement>("[data-auth-submit]")!.innerHTML = `${signup ? "Hesap oluştur" : "Giriş yap"} <span>→</span>`;
  }));
  document.querySelector<HTMLFormElement>(".auth-form")?.addEventListener("submit", (event) => {
    event.preventDefault();
    const form = event.currentTarget as HTMLFormElement;
    form.querySelectorAll(".field-error").forEach((item) => item.remove());
    const email = form.querySelector<HTMLInputElement>("#route-email");
    const password = form.querySelector<HTMLInputElement>("#route-password");
    const errors: string[] = [];
    if (!email?.value.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value)) errors.push("Geçerli bir e-posta adresi gir.");
    if (!password?.value || password.value.length < 6) errors.push("Şifren en az 6 karakter olmalı.");
    if (errors.length) { errors.forEach((message) => form.insertAdjacentHTML("beforeend", `<small class="field-error">${message}</small>`)); return; }
    form.insertAdjacentHTML("beforeend", `<small class="form-success">İşlem mock olarak tamamlandı ✓</small>`);
  });
  document.querySelector<HTMLButtonElement>(".hamburger")?.addEventListener("click", () => document.querySelector("nav")?.classList.toggle("open"));
}

function escapeHTML(value: string): string {
  return value.replace(/[&<>'"]/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[character] ?? character);
}

function bindGenericInteractions(): void {
  document.querySelectorAll<HTMLButtonElement>(".hamburger").forEach((button) => button.addEventListener("click", () => document.querySelector("nav")?.classList.toggle("open")));
  document.querySelectorAll<HTMLButtonElement>(".profile-tabs button").forEach((tab) => tab.addEventListener("click", () => { document.querySelectorAll(".profile-tabs button").forEach((item) => item.classList.remove("active")); tab.classList.add("active"); }));

  document.querySelectorAll<HTMLButtonElement>(".conversation").forEach((conversation) => conversation.addEventListener("click", () => {
    document.querySelectorAll(".conversation").forEach((item) => item.classList.remove("active"));
    conversation.classList.add("active");
    const selected = mockConversations.find((item) => item.id === conversation.dataset.conversation);
    if (!selected) return;
    const header = document.querySelector<HTMLElement>(".thread-head");
    const body = document.querySelector<HTMLElement>("[data-thread-body]");
    if (header) header.innerHTML = `<span class="person-avatar ${selected.tone}">${selected.initials}</span><div><b>${selected.name}</b><small>Şu an aktif · @${selected.name.toLowerCase().replace(/ /g, "")}</small></div><button class="round">⋯</button>`;
    if (body) body.innerHTML = `<div class="thread-date">BUGÜN</div>${selected.messages.length ? selected.messages.map((message) => `<div class="message ${message.from === "me" ? "sent" : "received"}">${escapeHTML(message.text)} <small>${message.time}</small></div>`).join("") : `<div class="empty-state">Henüz mesaj yok. İlk mesajı sen gönder.</div>`}`;
  }));

  document.querySelector<HTMLFormElement>("[data-message-form]")?.addEventListener("submit", (event) => {
    event.preventDefault();
    const form = event.currentTarget as HTMLFormElement;
    const input = form.querySelector<HTMLInputElement>("input");
    const text = input?.value.trim() ?? "";
    const body = document.querySelector<HTMLElement>("[data-thread-body]");
    if (!text || !body) return;
    body.insertAdjacentHTML("beforeend", `<div class="message sent">${escapeHTML(text)} <small>şimdi</small></div>`);
    if (input) input.value = "";
    body.scrollTop = body.scrollHeight;
  });

  document.querySelector<HTMLButtonElement>("[data-profile-edit]")?.addEventListener("click", (event) => {
    const button = event.currentTarget as HTMLButtonElement;
    button.textContent = button.textContent?.includes("Kaydedildi") ? "Düzenle" : "Kaydedildi ✓";
    document.querySelector<HTMLElement>(".profile-main h1")?.classList.toggle("is-edited");
  });
  document.querySelector<HTMLButtonElement>("[data-follow]")?.addEventListener("click", (event) => {
    const button = event.currentTarget as HTMLButtonElement;
    const following = button.dataset.following === "true";
    button.dataset.following = String(!following);
    button.innerHTML = following ? "Takip et <span>＋</span>" : "Takip ediliyor <span>✓</span>";
  });
  document.querySelector<HTMLButtonElement>("[data-wallet]")?.addEventListener("click", (event) => { (event.currentTarget as HTMLButtonElement).textContent = "Cüzdan açıldı ✓"; });

  document.querySelectorAll<HTMLFormElement>(".ai-composer").forEach((form) => form.addEventListener("submit", (event) => {
    event.preventDefault();
    const input = form.querySelector<HTMLInputElement>("input");
    const text = input?.value.trim() ?? "";
    const messages = form.closest<HTMLElement>(".ai-chat")?.querySelector<HTMLElement>(".ai-messages");
    if (!text || !messages) return;
    const response = form.closest(".nova-page") ? "Sesindeki niyeti anladım. Bunu daha etkili bir hikâyeye dönüştürmen için birkaç önerim var." : "Bunu senin için not aldım. Şu anda canlı odalarda benzer bir sohbet bulabilirim.";
    messages.insertAdjacentHTML("beforeend", `<div class="ai-bubble user">${escapeHTML(text)}</div><div class="ai-bubble ai-loading">Yanıt hazırlanıyor…</div>`);
    if (input) input.value = "";
    window.setTimeout(() => { const loading = messages.querySelector<HTMLElement>(".ai-loading"); if (loading) { loading.classList.remove("ai-loading"); loading.textContent = response; } }, 350);
  }));

  const adminSearch = document.querySelector<HTMLInputElement>("[data-admin-search]");
  adminSearch?.addEventListener("input", () => {
    const query = adminSearch.value.toLocaleLowerCase("tr-TR");
    document.querySelectorAll<HTMLTableRowElement>("tbody tr").forEach((row) => { row.hidden = !row.textContent?.toLocaleLowerCase("tr-TR").includes(query); });
    const visible = [...document.querySelectorAll<HTMLTableRowElement>("tbody tr")].some((row) => !row.hidden);
    const empty = document.querySelector<HTMLElement>("[data-admin-empty]");
    if (empty) empty.hidden = visible;
  });
  document.querySelectorAll<HTMLButtonElement>("[data-admin-action]").forEach((button) => button.addEventListener("click", () => { button.textContent = "✓"; button.classList.add("done"); }));
  document.querySelectorAll<HTMLElement>(".admin-nav-item").forEach((item) => item.addEventListener("click", () => { document.querySelectorAll(".admin-nav-item").forEach((entry) => entry.classList.remove("active")); item.classList.add("active"); }));
}

let activeRoom: Room | undefined;

function bindRoomInteractions(): void {
  const joinButton = document.querySelector<HTMLButtonElement>("[data-room-join]");
  const micButton = document.querySelector<HTMLButtonElement>("[data-mic-toggle]");
  const leaveButton = document.querySelector<HTMLButtonElement>(".leave-button");
  const status = document.querySelector<HTMLElement>("[data-room-status]");
  const audioContainer = document.querySelector<HTMLElement>("[data-remote-audio]");
  const setStatus = (message: string, isError = false) => { if (status) { status.textContent = message; status.classList.toggle("is-error", isError); } };
  joinButton?.addEventListener("click", async () => {
    joinButton.disabled = true; setStatus("Ses odasına bağlanılıyor…");
    try {
      const identity = sessionStorage.getItem("seson-livekit-identity") ?? "guest-" + crypto.randomUUID();
      sessionStorage.setItem("seson-livekit-identity", identity);
      const response = await fetch("/api/livekit/token", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ roomName: "geceye-bir-sarki", identity }) });
      const payload = await response.json() as { token?: string; serverUrl?: string; error?: string };
      if (!response.ok || !payload.token || !payload.serverUrl) throw new Error(payload.error ?? "Katılım tokenı alınamadı.");
      const room = new Room({ audioCaptureDefaults: { echoCancellation: true, noiseSuppression: true } });
      room.on(RoomEvent.TrackSubscribed, (track) => { if (track.kind === Track.Kind.Audio && audioContainer) audioContainer.appendChild(track.attach()); });
      room.on(RoomEvent.TrackUnsubscribed, (track) => track.detach().forEach((element) => element.remove()));
      room.on(RoomEvent.Disconnected, () => { activeRoom = undefined; if (micButton) micButton.disabled = true; if (leaveButton) leaveButton.disabled = true; if (joinButton) joinButton.disabled = false; setStatus("Ses odasından ayrıldın."); });
      await room.connect(payload.serverUrl, payload.token, { autoSubscribe: true });
      await room.startAudio(); activeRoom = room;
      if (micButton) micButton.disabled = false; if (leaveButton) leaveButton.disabled = false;
      setStatus("Ses odasına bağlandın. Mikrofonun kapalı.");
    } catch (error) { joinButton.disabled = false; setStatus(error instanceof Error ? error.message : "Ses odasına bağlanılamadı.", true); }
  });
  micButton?.addEventListener("click", async () => {
    if (!activeRoom) return; micButton.disabled = true;
    try {
      const enabled = !activeRoom.localParticipant.isMicrophoneEnabled;
      await activeRoom.localParticipant.setMicrophoneEnabled(enabled);
      micButton.setAttribute("aria-pressed", String(enabled)); micButton.textContent = enabled ? "🎙" : "♫";
      micButton.classList.toggle("is-muted", !enabled); setStatus(enabled ? "Mikrofonun açık." : "Mikrofonun kapalı.");
    } catch { setStatus("Mikrofon erişimi sağlanamadı. Tarayıcı iznini kontrol et.", true); } finally { micButton.disabled = false; }
  });
  leaveButton?.addEventListener("click", () => activeRoom?.disconnect());
  document.querySelector<HTMLButtonElement>("[data-raise-hand]")?.addEventListener("click", (event) => (event.currentTarget as HTMLButtonElement).classList.toggle("is-raised"));
}

function renderRoute(): void {
  const route = routeFromLocation();
  if (route === "auth") { app.innerHTML = authView(); bindAuthInteractions(); return; }
  if (route === "room") { app.innerHTML = roomView(); bindRoomInteractions(); return; }
  if (route === "profile") { app.innerHTML = profileView(); bindGenericInteractions(); return; }
  if (route === "messages") { app.innerHTML = messagesView(); bindGenericInteractions(); return; }
  if (route === "nova") { app.innerHTML = aiView("nova"); bindGenericInteractions(); return; }
  if (route === "lina") { app.innerHTML = aiView("lina"); bindGenericInteractions(); return; }
  if (route === "admin") { app.innerHTML = adminView(); bindGenericInteractions(); return; }
  app.innerHTML = landingMarkup;
  bindLandingInteractions();
}

window.addEventListener("hashchange", renderRoute);
window.addEventListener("popstate", renderRoute);
renderRoute();
