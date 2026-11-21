package com.okto.jalokduitnow.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A small synchronous event bus with typed listeners.
 * {@code subscribe(GameEvent.LevelUp.class, e -> ...)} only receives LevelUp events; subscribing to
 * {@code GameEvent.class} receives all of them. Listeners run on the publishing thread, so UI code
 * must hop to the FX thread itself (Platform.runLater). A listener that throws does not stop the others.
 */
public final class GameEventBus {
    private record Subscription<E extends GameEvent>(Class<E> type, Consumer<? super E> listener) {
        void deliver(GameEvent event) {
            listener.accept(type.cast(event)); // safe: only called when type.isInstance(event)
        }
    }

    private final List<Subscription<?>> subscriptions = new CopyOnWriteArrayList<>();

    /** @return a handle that removes the listener when run */
    public <E extends GameEvent> Runnable subscribe(Class<E> type, Consumer<? super E> listener) {
        Subscription<E> s = new Subscription<>(type, listener);
        subscriptions.add(s);
        return () -> subscriptions.remove(s);
    }

    public void publish(GameEvent event) {
        for (Subscription<?> s : subscriptions) {
            if (!s.type().isInstance(event)) continue;
            try {
                s.deliver(event);
            } catch (RuntimeException e) {
                System.err.println("Listener gagal untuk " + event.getClass().getSimpleName() + ": " + e);
            }
        }
    }
}
