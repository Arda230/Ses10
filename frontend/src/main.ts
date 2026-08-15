import "./style.css";

type NavigationItem = {
  icon: string;
  label: string;
};

const navigation: NavigationItem[] = [
  { icon: "▦", label: "Genel Bakış" },
  { icon: "◴", label: "Ajanda" },
  { icon: "◫", label: "Danışanlar" },
  { icon: "⌁", label: "Seanslar" },
  { icon: "▱", label: "Raporlar" },
];

const appointments = [
  { initials: "AY", name: "Ayşe Yılmaz", time: "10:00", tone: "lavender" },
  { initials: "BK", name: "Berke Kaya", time: "11:30", tone: "peach" },
  { initials: "ZK", name: "Zeynep Koç", time: "14:00", tone: "blue" },
];

const navMarkup = navigation
  .map(
    ({ icon, label }, index) => `
      <button class="nav-item ${index === 0 ? "is-active" : ""}" type="button" data-nav="${label}">
        <span class="nav-icon" aria-hidden="true">${icon}</span>
        <span>${label}</span>
      </button>`,
  )
  .join("");

const appointmentMarkup = appointments
  .map(
    ({ initials, name, time, tone }) => `
      <li class="appointment">
        <span class="avatar avatar-${tone}">${initials}</span>
        <span class="appointment-person"><strong>${name}</strong><small>Bireysel görüşme</small></span>
        <time>${time}</time>
        <button type="button" class="more-button" aria-label="${name} için seçenekler">•••</button>
      </li>`,
  )
  .join("");

document.querySelector<HTMLDivElement>("#app")!.innerHTML = `
  <div class="dashboard-shell">
    <aside class="sidebar">
      <a class="brand" href="#" aria-label="SESON ana sayfa"><span class="brand-mark">S</span><span>SESON</span></a>
      <nav class="main-nav" aria-label="Ana menü">${navMarkup}</nav>
      <div class="sidebar-bottom">
        <button class="nav-item" type="button"><span class="nav-icon">?</span><span>Yardım Merkezi</span></button>
        <button class="profile-card" type="button">
          <span class="avatar avatar-profile">ED</span><span><strong>Elif Demir</strong><small>Psikolog</small></span><span aria-hidden="true">⌄</span>
        </button>
      </div>
    </aside>

    <main class="main-content">
      <header class="topbar">
        <button class="mobile-menu" type="button" aria-label="Menüyü aç">☰</button>
        <div class="welcome"><p>14 Ağustos, Çarşamba</p><h1>Günaydın, Elif <span>✦</span></h1></div>
        <div class="top-actions"><button class="icon-button" type="button" aria-label="Bildirimler">♧<span class="notification-dot"></span></button><button class="add-button" type="button">＋ Yeni seans</button></div>
      </header>

      <section class="overview-grid" aria-label="Günlük özet">
        <article class="metric-card primary-metric"><div><span class="eyebrow">BUGÜNKÜ SEANSLAR</span><strong>08</strong><p>Toplam 10 seansın var</p></div><div class="metric-icon">◷</div></article>
        <article class="metric-card"><span class="eyebrow">AKTİF DANIŞAN</span><strong>42</strong><p><b>+4</b> bu ay eklendi</p></article>
        <article class="metric-card"><span class="eyebrow">BU AYKİ GELİR</span><strong>₺28.400</strong><p><b>+12,5%</b> geçen aya göre</p></article>
      </section>

      <section class="content-grid">
        <article class="panel schedule-panel">
          <div class="panel-heading"><div><span class="eyebrow">BUGÜNÜN AJANDASI</span><h2>Yaklaşan seanslar</h2></div><button class="text-button" type="button">Tüm ajanda <span>→</span></button></div>
          <div class="agenda">
            <div class="time-rail"><span>09:00</span><span>10:00</span><span>11:00</span><span>12:00</span><span>13:00</span></div>
            <div class="agenda-events"><div class="session session-a"><span>09:00 — 09:50</span><strong>Melis Akın</strong><small>Online seans</small></div><div class="session session-b"><span>10:00 — 10:50</span><strong>Ayşe Yılmaz</strong><small>Yüz yüze seans</small></div><div class="session session-c"><span>11:30 — 12:20</span><strong>Berke Kaya</strong><small>Online seans</small></div></div>
          </div>
        </article>

        <article class="panel appointments-panel">
          <div class="panel-heading"><div><span class="eyebrow">YAKLAŞAN</span><h2>Bugünkü randevular</h2></div><button class="icon-button plain" type="button" aria-label="Randevu takvimini aç">⌘</button></div>
          <ul class="appointments">${appointmentMarkup}</ul>
          <button class="view-all" type="button">Tüm randevuları görüntüle</button>
        </article>
      </section>

      <section class="panel insight-panel"><div><span class="eyebrow">HAFTALIK ÖZET</span><h2>Seans ritmin dengeli ilerliyor</h2><p>Geçen haftaya göre %18 daha fazla danışan görüşmesi tamamladın.</p></div><div class="bar-chart" aria-label="Haftalık seans grafiği"><span style="--height:48%"></span><span style="--height:68%"></span><span style="--height:42%"></span><span style="--height:82%"></span><span style="--height:62%"></span><span style="--height:92%"></span><span style="--height:56%"></span></div></section>
    </main>
  </div>
`;

document.querySelectorAll<HTMLButtonElement>("[data-nav]").forEach((item) => {
  item.addEventListener("click", () => {
    document
      .querySelectorAll("[data-nav]")
      .forEach((button) => button.classList.remove("is-active"));
    item.classList.add("is-active");
  });
});
