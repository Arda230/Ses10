import type { CurrentUser, RoomInfo } from "../api/client";

const seatMarkup = (): string => Array.from({ length: 12 }, (_, index) => `
  <button class="seat empty" type="button" data-seat-id="${index + 1}" aria-label="Boş mikrofon koltuğu ${index + 1}">
    <span>+</span><b>Mic ${index + 1}</b><small>Katıl</small>
  </button>`).join("");

export function premiumRoomView(room: RoomInfo | undefined, user: CurrentUser, rooms: RoomInfo[] = []): string {
  const title = room ? escapeHtml(room.title) : "Yeni Ses Odası";
  const category = room ? escapeHtml(room.category) : "SES10";
  const description = room ? escapeHtml(room.description || "Sesin bu odada hayat bulur.") : "İlk gerçek odanı oluşturarak 12 mic sahnesini başlat.";
  const owner = room ? escapeHtml(room.owner.username) : escapeHtml(user.username);
  const ownerInitials = initials(owner);
  return `<div class="route-page room-page premium-room-page">
    <div class="room-aurora room-aurora-a"></div><div class="room-aurora room-aurora-b"></div><div class="room-stars"></div>
    <header class="premium-room-nav">
      <a class="premium-brand" href="#top"><i></i><span>Ses<span>On</span><small>SESİNLE BAĞLAN</small></span></a>
      <nav><a href="#top">Ana Sayfa</a><label class="room-picker"><span>Odalar</span><select data-room-select>${rooms.map((item) => `<option value="${escapeHtml(item.slug)}" ${item.slug === room?.slug ? "selected" : ""}>${escapeHtml(item.title)}</option>`).join("")}</select></label><button type="button" data-new-room>＋ Yeni oda</button><a href="#">Etkinlikler</a></nav>
      <div class="room-user"><span class="room-user-avatar">${initials(user.username)}</span><span><b>${escapeHtml(user.username)}</b><small>${escapeHtml(user.email)}</small></span><button type="button" data-logout>Çıkış</button></div><div class="room-online"><i></i><span>Çevrimiçi<small><b data-participant-count>—</b> bu odada</small></span></div>
    </header>

    <main class="premium-room-shell">
      <section class="premium-room-main">
        <header class="premium-room-head">
          <div><span class="premium-live"><i></i> CANLI · ${category}</span><h1 data-room-title>${title}</h1><p data-room-description>${description}</p></div>
          <div class="premium-host-card"><span class="host-avatar" data-host-avatar>${ownerInitials}</span><span><small>ODA SAHİBİ</small><b data-host-name>${owner}</b><em>♛ HOST</em></span><button type="button" aria-label="Host seçenekleri">•••</button></div>
        </header>

        <div class="mic-stage">
          <div class="stage-orbit orbit-one"></div><div class="stage-orbit orbit-two"></div>
          <div class="stage-mic"><i class="stage-mic-capsule"></i><i class="stage-mic-stem"></i><span></span></div>
          <div class="stage-copy"><span>12 MIC · AUDIO ONLY</span><b>Sesin bu odada hayat bulur</b></div>
          <div class="stage-eq">${Array.from({ length: 18 }, (_, index) => `<i style="--bar:${index}"></i>`).join("")}</div>
        </div>

        <div class="seat-section-head"><span><small>KONUŞMACI SAHNESİ</small><b>Bir mic seç, sohbete katıl</b></span><span class="seat-legend"><i></i> Konuşuyor <i></i> Mic kapalı</span></div>
        <div class="seat-grid premium-seat-grid" data-seat-grid>${seatMarkup()}</div>

        <div class="host-seat-actions premium-host-actions" data-host-controls hidden>
          <span><i>♛</i><b>Host kontrolleri</b><small>Önce bir mic seç</small></span>
          <button type="button" data-host-lock>⌁ Kilitle / aç</button><button type="button" data-host-mute>◉ Mute</button><button type="button" data-host-remove>↓ Mic’ten indir</button>
        </div>
      </section>

      <aside class="premium-room-side">
        <section class="side-glass member-panel">
          <header><span><small>ODA İÇİ</small><b>Topluluk</b></span><em><i></i> CANLI</em></header>
          <div class="member-summary"><span class="member-stack" data-member-stack><i>${ownerInitials}</i></span><span><b><span data-member-total>—</span> kişi</b><small><span data-speaker-total>—</span> konuşmacı · canlı oda</small></span></div>
          <div class="member-list" data-member-list><div class="member-loading">Odaya katıldığında gerçek katılımcılar burada görünecek.</div></div>
          <button class="side-text-button" type="button">Tüm katılımcıları gör <span>→</span></button>
        </section>

        <section class="side-glass room-chat-shell">
          <header><span><small>TOPLULUK</small><b>Oda sohbeti</b></span><em>YAKINDA</em></header>
          <div class="chat-placeholder"><span>✦</span><b>Sohbet için yer hazır</b><p>Mesajlar ve tepkiler sonraki aşamada burada akacak.</p></div>
          <div class="chat-composer-placeholder"><span>Mesajını yaz...</span><button type="button" disabled>↑</button></div>
        </section>

        <section class="gift-teaser"><span>🎁</span><div><small>SESON HEDİYELERİ</small><b>Sahneye enerjini gönder</b></div><button type="button" disabled>Yakında</button></section>
      </aside>
    </main>

    <div class="premium-control-zone">
      <div class="room-connection" role="status" aria-live="polite" data-room-status>Ses odasına henüz bağlı değilsin.</div>
      <div class="premium-control-dock">
        <button class="dock-secondary" type="button" title="Sohbet yakında">◌<small>Sohbet</small></button>
        <button class="dock-secondary" type="button" title="Hediyeler yakında">◇<small>Hediye</small></button>
        <button class="dock-join" type="button" data-room-join ${room ? "" : "disabled"}><i>↗</i><span>Odaya katıl<small>Listener olarak bağlan</small></span></button>
        <button class="dock-mic" type="button" data-mic-toggle aria-pressed="false" disabled title="Mikrofon">♫<small>Mikrofon</small></button>
        <button class="dock-secondary" type="button" data-leave-seat title="Mic’ten in">↓<small>Mic’ten in</small></button>
        <button class="dock-secondary" type="button" title="Ayarlar">•••<small>Daha fazla</small></button>
        <button class="leave-button dock-leave" type="button" disabled>↙<small>Ayrıl</small></button>
      </div>
    </div>
    <div data-remote-audio hidden></div>
    ${room ? "" : roomSetup()}
  </div>`;
}


function roomSetup(): string {
  return `<div class="room-setup-overlay"><form class="room-setup-card" data-room-create><span class="eyebrow"><i></i> YENİ CANLI ODA</span><h2>12 mic sahneni oluştur</h2><p>Oda ve host bilgileri PostgreSQL’de gerçek hesabına bağlı olarak saklanır.</p><label>Oda adı<input name="title" minlength="3" maxlength="100" required placeholder="Örn. Gece Sohbeti" /></label><label>Kategori<input name="category" minlength="2" maxlength="60" required placeholder="Müzik & Sohbet" /></label><label>Açıklama<textarea name="description" maxlength="280" placeholder="Odanı kısaca anlat"></textarea></label><div class="auth-feedback" data-room-create-feedback></div><button class="dock-join" type="submit"><i>＋</i><span>Odayı oluştur<small>Sen host olacaksın</small></span></button></form></div>`;
}
function initials(name: string): string { return name.split(/\s+/).slice(0, 2).map((part) => part[0]?.toLocaleUpperCase("tr-TR") ?? "").join(""); }
function escapeHtml(value: string): string { return value.replace(/[&<>"']/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[character] ?? character); }
