export type Room = { title: string; host: string; listeners: string; tone: string; initials: string; topic: string };
export type Person = { name: string; handle: string; score: string; initials: string; tone: string };

export function roomCard(room: Room): string {
  return `<article class="room-card"><div class="room-image ${room.tone}"><span class="room-orb">${room.initials}</span><span class="live"><i></i> CANLI</span><span class="listeners">◉ ${room.listeners}</span></div><div class="room-info"><span>${room.topic}</span><h3>${room.title}</h3><p><b>${room.host}</b> · konuşuyor</p></div></article>`;
}

export function rankingRow(person: Person, index: number): string {
  return `<li><span class="rank">0${index + 1}</span><span class="person-avatar ${person.tone}">${person.initials}</span><span class="person"><b>${person.name}</b><small>${person.handle}</small></span><strong>${person.score}</strong></li>`;
}
