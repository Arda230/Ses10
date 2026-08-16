import "./style.css";
import "./room.css";
import "./premium-room.css";
import { premiumRoomView } from "./views/premiumRoomView";
import { bindLiveKitRoom } from "./livekitRoom";
import { rankingRow } from "./components/markup";
import { people } from "./data/fixtures";
import { authView } from "./views/routeViews";
import { ApiError, api, getRooms, getSession, jsonRequest, type CurrentUser, type RoomInfo } from "./api/client";
document.body.insertAdjacentHTML("afterbegin", `<div class="premium-splash" data-splash><div class="splash-core"><span class="splash-ring"></span><span class="splash-wave left"><i></i><i></i><i></i></span><strong class="splash-mark">Ses<em>10</em></strong><span class="splash-wave right"><i></i><i></i><i></i></span><small>SESİNLE BAĞLAN</small></div></div>`);
window.setTimeout(() => document.querySelector("[data-splash]")?.classList.add("is-hidden"), 1050);
window.setTimeout(() => document.querySelector("[data-splash]")?.remove(), 1750);
const roomMarkup = `<div class="empty-state" data-room-directory>Giriş yaptıktan sonra gerçek canlı odalar burada gösterilecek.</div>`;
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
  let mode: "login" | "signup" = "login";
  const setMode = (next: "login" | "signup") => {
    mode = next;
    document.querySelectorAll<HTMLButtonElement>("[data-auth-tab]").forEach((item) => item.classList.toggle("active", item.dataset.authTab === mode));
    const signup = mode === "signup";
    const fields = document.querySelector<HTMLElement>("[data-register-fields]");
    const username = document.querySelector<HTMLInputElement>("#route-username");
    const login = document.querySelector<HTMLInputElement>("#route-email");
    if (fields) fields.hidden = !signup;
    if (username) username.required = signup;
    if (login) { login.type = signup ? "email" : "text"; login.name = signup ? "email" : "login"; login.placeholder = signup ? "ornek@email.com" : "E-posta veya kullanıcı adı"; }
    document.querySelector<HTMLElement>("[data-auth-heading]")!.innerHTML = signup ? "SesOn'a <em>katıl.</em>" : "Tekrar <em>hoş geldin.</em>";
    document.querySelector<HTMLElement>("[data-auth-copy]")!.textContent = signup ? "Topluluğa katılmak için gerçek hesap bilgilerini gir." : "Güvenli oturumunla SesOn'a devam et.";
    document.querySelector<HTMLButtonElement>("[data-auth-submit]")!.innerHTML = `${signup ? "Hesap oluştur" : "Giriş yap"} <span>→</span>`;
  };
  document.querySelectorAll<HTMLButtonElement>("[data-auth-tab]").forEach((tab) => tab.addEventListener("click", () => setMode(tab.dataset.authTab === "signup" ? "signup" : "login")));
  document.querySelector<HTMLButtonElement>("[data-password-toggle]")?.addEventListener("click", (event) => {
    const password = document.querySelector<HTMLInputElement>("#route-password");
    if (!password) return;
    password.type = password.type === "password" ? "text" : "password";
    (event.currentTarget as HTMLButtonElement).setAttribute("aria-label", password.type === "password" ? "Şifreyi göster" : "Şifreyi gizle");
  });
  document.querySelector<HTMLFormElement>(".auth-form")?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget as HTMLFormElement;
    const feedback = form.querySelector<HTMLElement>("[data-auth-feedback]");
    const submit = form.querySelector<HTMLButtonElement>("[data-auth-submit]");
    const login = form.querySelector<HTMLInputElement>("#route-email")?.value.trim() ?? "";
    const password = form.querySelector<HTMLInputElement>("#route-password")?.value ?? "";
    const username = form.querySelector<HTMLInputElement>("#route-username")?.value.trim() ?? "";
    if (mode === "signup" && (!/^[\p{L}\p{N}_.-]{3,40}$/u.test(username) || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(login))) { if (feedback) feedback.textContent = "Geçerli kullanıcı adı ve e-posta gir."; return; }
    if (password.length < 12 || password.length > 128) { if (feedback) feedback.textContent = "Şifre 12-128 karakter olmalı."; return; }
    if (submit) { submit.disabled = true; submit.textContent = mode === "signup" ? "Hesap oluşturuluyor…" : "Giriş yapılıyor…"; }
    if (feedback) { feedback.textContent = ""; feedback.classList.remove("is-error"); }
    try {
      const result = mode === "signup" ? await jsonRequest<{ user: CurrentUser }>("/api/auth/register", "POST", { username, email: login, password }) : await jsonRequest<{ user: CurrentUser }>("/api/auth/login", "POST", { login, password });
      currentUser = result.user;
      navigate("/room");
    } catch (error) {
      if (feedback) { feedback.textContent = error instanceof Error ? error.message : "Giriş tamamlanamadı."; feedback.classList.add("is-error"); }
      if (submit) { submit.disabled = false; submit.innerHTML = `${mode === "signup" ? "Hesap oluştur" : "Giriş yap"} <span>→</span>`; }
    }
  });
  document.querySelector<HTMLButtonElement>(".hamburger")?.addEventListener("click", () => document.querySelector("nav")?.classList.toggle("open"));
}
function escapeHTML(value: string): string {
  return value.replace(/[&<>'"]/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[character] ?? character);
}

function bindRoomInteractions(room: RoomInfo): void {
  roomCleanup = bindLiveKitRoom(room);
}

async function renderRoute(): Promise<void> {
  if (roomCleanup) { void roomCleanup(); roomCleanup = undefined; }
  const route = routeFromLocation();
  if (route === "auth") { if (currentUser) { navigate("/room"); return; } app.innerHTML = authView(); bindAuthInteractions(); return; }
  if (route === "room") {
    if (!currentUser) { app.innerHTML = authView(); bindAuthInteractions(); return; }
    try {
      const listing = await getRooms();
      const requested = new URLSearchParams(window.location.search).get("room");
      const room = listing.rooms.find((item) => item.slug === requested) ?? listing.rooms[0];
      const creating = new URLSearchParams(window.location.search).get("new") === "1";
      app.innerHTML = premiumRoomView(creating ? undefined : room, currentUser, listing.rooms);
      bindAuthenticatedChrome();
      bindRoomDirectory();
      if (!creating && room) bindRoomInteractions(room); else bindRoomCreation();
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) { currentUser = null; app.innerHTML = authView(); bindAuthInteractions(); return; }
      app.innerHTML = `<div class="route-page"><div class="fatal-state">Oda verileri yüklenemedi. Lütfen tekrar dene.</div></div>`;
    }
    return;
  }
  if (route !== "landing" && !currentUser) { app.innerHTML = authView(); bindAuthInteractions(); return; }
  if (route === "profile" && currentUser) { app.innerHTML = accountView(currentUser); bindAuthenticatedChrome(); return; }
  if (route === "admin" && currentUser?.role !== "admin") { navigate("/room"); return; }
  if (route === "messages" || route === "nova" || route === "lina" || route === "admin") { app.innerHTML = unavailableView(route, currentUser!); bindAuthenticatedChrome(); return; }
  app.innerHTML = landingMarkup;
  bindLandingInteractions();
  if (currentUser) void hydrateLandingRooms();
}

async function hydrateLandingRooms(): Promise<void> {
  const target = document.querySelector<HTMLElement>("[data-room-directory]")?.parentElement;
  if (!target) return;
  try {
    const { rooms } = await getRooms();
    target.innerHTML = rooms.length ? rooms.map((room) => `<article class="room-card" data-real-room="${escapeHTML(room.slug)}" tabindex="0" role="button"><div class="room-image"><span class="room-avatar cyan">${escapeHTML(room.owner.username.slice(0, 2).toLocaleUpperCase("tr-TR"))}</span></div><div class="room-info"><span class="room-topic">${escapeHTML(room.category)}</span><h3>${escapeHTML(room.title)}</h3><p>${escapeHTML(room.owner.username)} · Canlı oda</p></div></article>`).join("") : `<div class="empty-state">Henüz açık oda yok. Oda ekranından ilk odayı oluşturabilirsin.</div>`;
    target.querySelectorAll<HTMLElement>("[data-real-room]").forEach((card) => card.addEventListener("click", () => navigate(`/room?room=${encodeURIComponent(card.dataset.realRoom ?? "")}`)));
  } catch { target.innerHTML = `<div class="empty-state">Odalar şu anda yüklenemedi.</div>`; }
}

function bindRoomDirectory(): void {
  document.querySelector<HTMLSelectElement>("[data-room-select]")?.addEventListener("change", (event) => navigate(`/room?room=${encodeURIComponent((event.currentTarget as HTMLSelectElement).value)}`));
  document.querySelector<HTMLButtonElement>("[data-new-room]")?.addEventListener("click", () => navigate("/room?new=1"));
}

function accountView(user: CurrentUser): string {
  const safe = escapeHTML(user.username); const email = escapeHTML(user.email); const initials = safe.slice(0, 2).toLocaleUpperCase("tr-TR");
  return `<div class="route-page auth-page"><main class="auth-layout"><section class="auth-pitch"><span class="eyebrow"><i></i> GERÇEK HESAP</span><h1>${safe}</h1><p>Profil bilgileri doğrulanmış oturumdan yüklenmiştir.</p></section><section class="auth-screen"><span class="profile-big-avatar">${initials}</span><h2>${safe}</h2><p>${email}</p><p>Hesap rolü: ${user.role}</p><button class="join" data-logout>Çıkış yap</button></section></main></div>`;
}
function unavailableView(route: string, user: CurrentUser): string {
  return `<div class="route-page auth-page"><main class="auth-layout"><section class="auth-pitch"><span class="eyebrow"><i></i> ${escapeHTML(route.toLocaleUpperCase("tr-TR"))}</span><h1>Henüz kullanıma açık değil.</h1><p>Bu özellik için gerçek production backend sözleşmesi tamamlanmadan sahte veri veya sahte başarı gösterilmiyor.</p><a class="join" href="/room">Odaya dön →</a></section><section class="auth-screen"><h2>${escapeHTML(user.username)}</h2><p>Oturum doğrulandı.</p><button class="join" data-logout>Çıkış yap</button></section></main></div>`;
}

function bindAuthenticatedChrome(): void {
  document.querySelector<HTMLButtonElement>("[data-logout]")?.addEventListener("click", async () => {
    if (roomCleanup) { await roomCleanup(); roomCleanup = undefined; }
    await api<void>("/api/auth/logout", { method: "POST", headers: { "content-type": "application/json" }, body: "{}" }).catch(() => undefined);
    currentUser = null;
    navigate("/auth");
  });
}

function bindRoomCreation(): void {
  document.querySelector<HTMLFormElement>("[data-room-create]")?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget as HTMLFormElement;
    const feedback = form.querySelector<HTMLElement>("[data-room-create-feedback]");
    const data = new FormData(form);
    try {
      const result = await jsonRequest<{ room: RoomInfo }>("/api/rooms", "POST", { title: String(data.get("title") ?? ""), category: String(data.get("category") ?? ""), description: String(data.get("description") ?? "") });
      navigate(`/room?room=${encodeURIComponent(result.room.slug)}`);
    } catch (error) { if (feedback) { feedback.textContent = error instanceof Error ? error.message : "Oda oluşturulamadı."; feedback.classList.add("is-error"); } }
  });
}
let currentUser: CurrentUser | null = null;
let roomCleanup: (() => Promise<void>) | undefined;
window.addEventListener("hashchange", () => { void renderRoute(); });
window.addEventListener("popstate", () => { void renderRoute(); });
void getSession().then(({ user }) => { currentUser = user; return renderRoute(); }).catch(() => renderRoute());
