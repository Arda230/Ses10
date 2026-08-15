export type MockMessage = { from: "me" | "them"; text: string; time: string };
export type MockConversation = { id: string; initials: string; name: string; preview: string; tone: string; messages: MockMessage[] };

export const mockConversations: MockConversation[] = [
  { id: "ece", initials: "ES", name: "Ece Su", preview: "Bu akşam odada görüşürüz!", tone: "violet", messages: [{ from: "them", text: "Selam Lara! Bu akşamki oda için hazır mısın?", time: "20:41" }, { from: "me", text: "Hazırım! Yeni şarkı listesini de hazırladım ✨", time: "20:42" }, { from: "them", text: "Harika, o zaman birazdan görüşürüz.", time: "20:43" }] },
  { id: "mert", initials: "MC", name: "Mert Can", preview: "Yeni bölümü dinledin mi?", tone: "gold", messages: [{ from: "them", text: "Yeni bölümü dinledin mi?", time: "20:12" }] },
  { id: "selin", initials: "SY", name: "Selin Y.", preview: "Çok güzel söyledin ✨", tone: "pink", messages: [{ from: "them", text: "Çok güzel söyledin ✨", time: "19:38" }] },
];

export const mockRoomSeats = ["LD", "MC", "", "ES", "", "SY", "", "ME", ""];
export const mockAdminUsers = [
  { name: "Lara Deniz", type: "Kullanıcı", status: "Aktif", tone: "pink" },
  { name: "Geceye Bir Şarkı", type: "Canlı oda", status: "İnceleniyor", tone: "violet" },
  { name: "Mert Can", type: "Kullanıcı", status: "Aktif", tone: "gold" },
  { name: "Sesin Hikayesi", type: "Etkinlik", status: "Planlandı", tone: "cyan" },
];
