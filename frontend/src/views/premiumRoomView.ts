const seatMarkup = (): string => Array.from({ length: 12 }, (_, index) => `
  <button class="seat empty" type="button" data-seat-id="${index + 1}" aria-label="Boş mikrofon koltuğu ${index + 1}">
    <span>+</span><b>Mic ${index + 1}</b><small>Katıl</small>
  </button>`).join("");

export function premiumRoomView(): string {
  return `<div class="route-page room-page premium-room-page">
    <div class="room-aurora room-aurora-a"></div><div class="room-aurora room-aurora-b"></div><div class="room-stars"></div>
    <header class="premium-room-nav">
      <a class="premium-brand" href="#top"><i></i><span>Ses<span>On</span><small>SESİNLE BAĞLAN</small></span></a>
      <nav><a href="#top">Ana Sayfa</a><a class="active" href="/room">Odalar</a><a href="#">Etkinlikler</a><a href="#">Hediyeler</a></nav>
      <div class="room-online"><i></i><span>Çevrimiçi<small><b data-participant-count>—</b> bu odada</small></span></div>
    </header>

    <main class="premium-room-shell">
      <section class="premium-room-main">
        <header class="premium-room-head">
          <div><span class="premium-live"><i></i> CANLI · MÜZİK & SOHBET</span><h1>Geceye Bir <em>Şarkı</em></h1><p>Seslerin birbirine karıştığı, gecenin en sıcak sohbeti.</p></div>
          <div class="premium-host-card"><span class="host-avatar">ME</span><span><small>ODA SAHİBİ</small><b>Mert Eren</b><em>♛ HOST</em></span><button type="button" aria-label="Host seçenekleri">•••</button></div>
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
          <div class="member-summary"><span class="member-stack"><i>ME</i><i>LD</i><i>ES</i><i>+9</i></span><span><b><span data-member-total>—</span> kişi</b><small><span data-speaker-total>—</span> konuşmacı · canlı oda</small></span></div>
          <div class="member-list" data-member-list>
            <article><span class="member-avatar cyan">ME</span><span><b>Mert Eren</b><small>Oda sahibi</small></span><em class="role-host">♛ HOST</em></article>
            <article><span class="member-avatar violet">ES</span><span><b>Ece Su</b><small>Konuşmacı</small></span><em class="role-mod">✦ MOD</em></article>
            <article><span class="member-avatar pink">LD</span><span><b>Lara Deniz</b><small>Dinliyor</small></span><em>♫</em></article>
          </div>
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
        <button class="dock-join" type="button" data-room-join><i>↗</i><span>Odaya katıl<small>Listener olarak bağlan</small></span></button>
        <button class="dock-mic" type="button" data-mic-toggle aria-pressed="false" disabled title="Mikrofon">♫<small>Mikrofon</small></button>
        <button class="dock-secondary" type="button" data-leave-seat title="Mic’ten in">↓<small>Mic’ten in</small></button>
        <button class="dock-secondary" type="button" title="Ayarlar">•••<small>Daha fazla</small></button>
        <button class="leave-button dock-leave" type="button" disabled>↙<small>Ayrıl</small></button>
      </div>
    </div>
    <div data-remote-audio hidden></div>
  </div>`;
}
