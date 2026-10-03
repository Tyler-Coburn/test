package dev.emerald.core.request;

import dev.emerald.core.item.ItemCounter;
import dev.emerald.core.item.ItemStore;
import dev.emerald.core.testing.TestData;
import dev.emerald.core.world.ItemIds;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RequestBoardTest {
    final UUID builder = UUID.randomUUID();
    final UUID courier = UUID.randomUUID();

    @Test
    void lifecycleOpenClaimPickupDeliver() {
        RequestBoard board = new RequestBoard();
        ItemCounter wh = new ItemCounter();
        wh.insert(ItemIds.OAK_PLANKS, 20);
        ResourceRequest r = board.open(builder, ItemIds.OAK_PLANKS, 16, null, null, 0);
        board.resolve(wh, id -> null, 1);
        assertEquals(ResourceRequest.State.OPEN, r.state());
        assertTrue(board.nextClaimable(wh).isPresent());

        board.claim(r.id(), courier, 2);
        assertEquals(ResourceRequest.State.CLAIMED, r.state());
        assertEquals(16, board.reserved(ItemIds.OAK_PLANKS));
        assertEquals(4, board.available(wh, ItemIds.OAK_PLANKS));
        assertThrows(IllegalStateException.class, () -> board.deliver(r.id(), 3), "cannot deliver before pickup");

        board.markPickedUp(r.id(), 3);
        assertEquals(0, board.reserved(ItemIds.OAK_PLANKS));
        board.deliver(r.id(), 4);
        assertEquals(ResourceRequest.State.DELIVERED, r.state());
        assertThrows(IllegalStateException.class, () -> board.claim(r.id(), courier, 5));
    }

    @Test
    void missingStockBlocksAndRestockReopens() {
        RequestBoard board = new RequestBoard();
        ItemCounter wh = new ItemCounter();
        wh.insert(ItemIds.OAK_PLANKS, 4);
        ResourceRequest r = board.open(builder, ItemIds.OAK_PLANKS, 16, null, null, 0);
        board.resolve(wh, id -> null, 1);
        assertEquals(ResourceRequest.State.BLOCKED, r.state());
        assertTrue(r.reason().contains("4/16"), r.reason());

        wh.insert(ItemIds.OAK_PLANKS, 12);
        board.resolve(wh, id -> null, 2);
        assertEquals(ResourceRequest.State.OPEN, r.state());
    }

    @Test
    void alreadyHeldCancels() {
        RequestBoard board = new RequestBoard();
        ItemCounter held = new ItemCounter();
        held.insert(ItemIds.OAK_PLANKS, 16);
        ResourceRequest r = board.open(builder, ItemIds.OAK_PLANKS, 16, null, null, 0);
        board.resolve(new ItemCounter(), id -> id.equals(builder) ? held : null, 1);
        assertEquals(ResourceRequest.State.CANCELLED, r.state());
    }

    @Test
    void reservationPreventsDoubleClaimOfTheSameStock() {
        RequestBoard board = new RequestBoard();
        ItemStore wh = new ItemCounter();
        wh.insert(ItemIds.OAK_PLANKS, 16);
        ResourceRequest a = board.open(builder, ItemIds.OAK_PLANKS, 16, null, null, 0);
        ResourceRequest b = board.open(UUID.randomUUID(), ItemIds.OAK_PLANKS, 16, null, null, 0);
        board.claim(a.id(), courier, 1);
        assertTrue(board.nextClaimable(wh).isEmpty(), "second request must not see reserved stock");
        board.resolve(wh, id -> null, 2);
        assertEquals(ResourceRequest.State.BLOCKED, b.state());
    }

    @Test
    void roundTrips() {
        RequestBoard board = new RequestBoard();
        ResourceRequest r = board.open(builder, ItemIds.OAK_PLANKS, 16, null, UUID.randomUUID(), 5);
        board.block(r.id(), "no stock", 6);
        RequestBoard back = new RequestBoard();
        @SuppressWarnings("unchecked")
        var list = (java.util.List<Map<String, Object>>) (Object) TestData.reserialize(board.toList());
        back.loadFrom(list);
        ResourceRequest b = back.get(r.id()).orElseThrow();
        assertEquals(ResourceRequest.State.BLOCKED, b.state());
        assertEquals("no stock", b.reason());
        assertEquals(r.projectId(), b.projectId());
    }
}
