import assert from "node:assert/strict";
import { EventEmitter } from "node:events";
import type { ServerResponse } from "node:http";
import { test } from "node:test";

import { RoomStateStore } from "../src/rooms/roomState.js";

test("persisted room owner is host and room has twelve fixed seats", () => {
  const store = new RoomStateStore();
  const host = store.join("test-room", "host-user", "host-1", "Host", "host");
  const snapshot = store.snapshot("test-room", host.identity);
  assert.equal(snapshot.self.role, "host");
  assert.deepEqual(snapshot.seats.map((seat) => seat.id), [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]);
});

test("a participant is not removed after 30 seconds merely because it has no SSE subscription", (context) => {
  context.mock.timers.enable(["setTimeout"]);
  const disconnected: string[] = [];
  const store = new RoomStateStore((_roomName, identity) => { disconnected.push(identity); });
  const participant = store.join("android-room", "android-user", "android-identity", "Android", "listener");

  context.mock.timers.tick(30_001);

  assert.equal(store.participant("android-room", participant.identity)?.identity, participant.identity);
  assert.deepEqual(disconnected, []);
});

test("seat collision, lock, leave and host removal are enforced", () => {
  const store = new RoomStateStore();
  const host = store.join("test-room", "host-user", "host-1", "Host", "host");
  const first = store.join("test-room", "user-one", "user-1", "Birinci", "listener");
  const second = store.join("test-room", "user-two", "user-2", "İkinci", "listener");
  assert.equal(store.snapshot("test-room", first.identity).self.role, "listener");
  store.claimSeat("test-room", first.identity, 1);
  assert.throws(() => store.claimSeat("test-room", second.identity, 1), /OCCUPIED/);
  assert.throws(() => store.claimSeat("test-room", first.identity, 2), /ALREADY_SEATED/);
  store.setSeatLock("test-room", host.identity, 2, true);
  assert.throws(() => store.claimSeat("test-room", second.identity, 2), /LOCKED/);
  store.leaveSeat("test-room", host.identity, first.identity);
  assert.equal(store.snapshot("test-room", host.identity).seats[0]?.occupant, null);
  store.claimSeat("test-room", second.identity, 3);
  store.leaveSeat("test-room", second.identity);
  assert.equal(store.snapshot("test-room", second.identity).self.seatId, null);
});

test("seat claim, mute and leave are broadcast to every SSE subscriber", () => {
  const store = new RoomStateStore();
  const first = store.join("sync-room", "user-one", "identity-one", "Birinci", "listener");
  const second = store.join("sync-room", "user-two", "identity-two", "İkinci", "listener");
  const firstStream = new SnapshotStream();
  const secondStream = new SnapshotStream();
  store.subscribe("sync-room", first.identity, firstStream as unknown as ServerResponse);
  store.subscribe("sync-room", second.identity, secondStream as unknown as ServerResponse);
  store.claimSeat("sync-room", first.identity, 4);
  assert.equal(secondStream.latest().seats[3]?.occupant?.identity, first.identity);
  store.setMuted("sync-room", first.identity, first.identity, false);
  assert.equal(secondStream.latest().seats[3]?.occupant?.muted, false);
  store.leaveSeat("sync-room", first.identity);
  assert.equal(secondStream.latest().seats[3]?.occupant, null);
  store.claimSeat("sync-room", first.identity, 4);
  store.disconnect("sync-room", first.identity);
  assert.equal(secondStream.latest().seats[3]?.occupant, null);
  assert.throws(() => store.snapshot("sync-room", first.identity), /UNAUTHORIZED/);
  firstStream.emit("close");
  secondStream.emit("close");
});

test("moderator permissions come from server state and listeners cannot elevate themselves", () => {
  const store = new RoomStateStore();
  const host = store.join("role-room", "host-user", "host-identity", "Host", "host");
  const moderator = store.join("role-room", "mod-user", "mod-identity", "Mod", "moderator");
  const listener = store.join("role-room", "listener-user", "listener-identity", "Listener", "listener");
  store.setSeatLock("role-room", moderator.identity, 4, true);
  assert.equal(store.snapshot("role-room", host.identity).seats[3]?.locked, true);
  assert.throws(() => store.setSeatLock("role-room", listener.identity, 5, true), /FORBIDDEN/);
  assert.throws(() => store.setRole("role-room", listener.identity, moderator.userId, "listener"), /FORBIDDEN/);
  store.setRole("role-room", host.identity, listener.userId, "moderator");
  assert.equal(store.snapshot("role-room", listener.identity).self.role, "moderator");
});


class SnapshotStream extends EventEmitter {
  readonly chunks: string[] = [];
  write(chunk: string) { this.chunks.push(chunk); return true; }
  end() {}
  latest() {
    const data = this.chunks.at(-1)?.split("\n").find((line) => line.startsWith("data: "))?.slice(6);
    assert.ok(data);
    return JSON.parse(data) as { seats: Array<{ occupant: null | { identity: string; muted: boolean } }> };
  }
}
