package com.okto.jalokduitnow.event;

import com.okto.jalokduitnow.event.GameEvent.LevelUp;
import com.okto.jalokduitnow.event.GameEvent.XpGained;
import com.okto.jalokduitnow.model.LevelUpResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameEventBusTest {
    @Test
    void listenersOnlyReceiveTheirOwnEventType() {
        GameEventBus bus = new GameEventBus();
        List<Integer> xp = new ArrayList<>();
        List<LevelUpResult> levels = new ArrayList<>();
        bus.subscribe(XpGained.class, e -> xp.add(e.amount()));      // typed: e is an XpGained, no cast
        bus.subscribe(LevelUp.class, e -> levels.add(e.result()));

        bus.publish(new XpGained(10, "a"));
        bus.publish(new LevelUp(new LevelUpResult(1, 2, "Jalok Pemula")));
        bus.publish(new XpGained(5, "b"));

        assertEquals(List.of(10, 5), xp);
        assertEquals(1, levels.size());
        assertEquals(2, levels.get(0).toLevel());
    }

    @Test
    void subscribingToTheBaseTypeReceivesEverything() {
        GameEventBus bus = new GameEventBus();
        List<GameEvent> all = new ArrayList<>();
        bus.subscribe(GameEvent.class, all::add);
        bus.publish(new XpGained(1, "x"));
        bus.publish(new LevelUp(new LevelUpResult(1, 2, "t")));
        assertEquals(2, all.size());
    }

    @Test
    void unsubscribeStopsDelivery() {
        GameEventBus bus = new GameEventBus();
        List<GameEvent> got = new ArrayList<>();
        Runnable off = bus.subscribe(XpGained.class, got::add);
        bus.publish(new XpGained(1, "x"));
        off.run();
        bus.publish(new XpGained(2, "y"));
        assertEquals(1, got.size());
    }

    @Test
    void aFailingListenerDoesNotStopTheOthers() {
        GameEventBus bus = new GameEventBus();
        List<GameEvent> got = new ArrayList<>();
        bus.subscribe(XpGained.class, e -> { throw new IllegalStateException("boom"); });
        bus.subscribe(XpGained.class, got::add);
        bus.publish(new XpGained(1, "x"));
        assertEquals(1, got.size());
    }
}
