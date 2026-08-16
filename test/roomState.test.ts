import assert from "node:assert/strict";
import { test } from "node:test";

import { RoomStateStore } from "../src/rooms/roomState.js";

test("first participant is host and room has twelve fixed seats", () => {
  const store = new RoomStateStore();
  const host = store.join("test-room", "host-1", "Host");
  const snapshot = store.snapshot("test-room", host.identity);
  assert.equal(snapshot.self.role, "host");
  assert.deepEqual(snapshot.seats.map((seat) => seat.id), [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]);
});

test("seat collision, lock, leave and host removal are enforced", () => {
  const store = new RoomStateStore();
  const host = store.join("test-room", "host-1", "Host");
  const first = store.join("test-room", "user-1", "Birinci");
  const second = store.join("test-room", "user-2", "İkinci");
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
