import type { Person, Room } from "../components/markup";

export const rooms: Room[] = [
  { title: "Geceye Bir Şarkı", host: "Mert & Ece", listeners: "1.2K", tone: "pink", initials: "ME", topic: "Müzik & sohbet" },
  { title: "Kahve Molası", host: "Deniz Aksoy", listeners: "846", tone: "purple", initials: "DA", topic: "Gündelik" },
  { title: "Bugün Nasılsın?", host: "Selin Y.", listeners: "623", tone: "cyan", initials: "SY", topic: "İyi hisset" },
  { title: "Yeni Sesler", host: "SesOn keşif", listeners: "418", tone: "blue", initials: "SK", topic: "Keşfet" },
];

export const people: Person[] = [
  { name: "Lara Deniz", handle: "@laradeniz", score: "24.8K", initials: "LD", tone: "pink" },
  { name: "Mert Can", handle: "@mertcan", score: "21.3K", initials: "MC", tone: "gold" },
  { name: "Ece Su", handle: "@ecesu", score: "18.7K", initials: "ES", tone: "violet" },
];
