package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.reminder.AppNotification;
import com.okto.jalokduitnow.reminder.NotificationAction;
import com.okto.jalokduitnow.storage.JsonRepository;
import com.okto.jalokduitnow.storage.NotificationDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationCenterTest {
    @TempDir Path dir;
    NotificationCenter center;
    Instant base = Instant.parse("2026-11-21T10:00:00Z");

    @BeforeEach
    void setUp() throws Exception {
        center = newCenter();
        center.load();
    }

    private NotificationCenter newCenter() {
        return new NotificationCenter(new JsonRepository<>(dir.resolve("notifications.json"), NotificationDto.class));
    }

    private AppNotification note(String title, int minutesAfterBase) {
        return AppNotification.create(title, "pesan " + title, base.plusSeconds(60L * minutesAfterBase), NotificationAction.NONE, "id-" + title);
    }

    @Test
    void newestComesFirstAndUnreadIsCounted() {
        center.add(note("a", 0));
        center.add(note("b", 1));
        center.add(note("c", 2));
        assertEquals(List.of("c", "b", "a"), center.getAll().stream().map(AppNotification::getTitle).toList());
        assertEquals(3, center.unreadCount());
    }

    @Test
    void markingOneReadOnlyChangesThatOne() {
        AppNotification a = note("a", 0), b = note("b", 1);
        center.add(a);
        center.add(b);
        center.markRead(a.getId());
        assertTrue(a.isRead());
        assertFalse(b.isRead());
        assertEquals(1, center.unreadCount());
        center.markRead(UUID.randomUUID());                              // unknown id: nothing happens
        assertEquals(1, center.unreadCount());
    }

    @Test
    void markAllReadAndClearAll() {
        center.add(note("a", 0));
        center.add(note("b", 1));
        center.markAllRead();
        assertEquals(0, center.unreadCount());
        assertEquals(2, center.getAll().size(), "marking read keeps them in the list");
        center.clear();
        assertTrue(center.getAll().isEmpty());
        assertEquals(0, center.unreadCount());
    }

    @Test
    void onlyTheMostRecentFiftyAreKept() {
        for (int i = 0; i < 60; i++) center.add(note("n" + i, i));
        assertEquals(NotificationCenter.MAX_KEPT, center.getAll().size());
        assertEquals("n59", center.getAll().get(0).getTitle());
        assertEquals("n10", center.getAll().get(49).getTitle(), "the 10 oldest fell off");
    }

    @Test
    void listenersAreToldOnlyWhenSomethingChanged() {
        int[] calls = {0};
        center.addListener(() -> calls[0]++);
        AppNotification a = note("a", 0);
        center.add(a);                                   // 1
        center.markRead(a.getId());                      // 2
        center.markRead(a.getId());                      // already read: no change
        center.markAllRead();                            // nothing unread: no change
        center.clear();                                  // 3
        center.clear();                                  // already empty: no change
        assertEquals(3, calls[0]);
    }

    @Test
    void thePersistedListKeepsOrderReadStateAndActions() throws Exception {
        AppNotification a = AppNotification.create("Laporan donor", "isi", base, NotificationAction.OPEN_REPORT, "donor-report");
        AppNotification b = note("b", 5);
        center.add(a);
        center.add(b);
        center.markRead(a.getId());
        center.flush();

        NotificationCenter again = newCenter();
        again.load();
        assertEquals(2, again.getAll().size());
        assertEquals("b", again.getAll().get(0).getTitle());
        AppNotification loaded = again.getAll().get(1);
        assertEquals(a.getId(), loaded.getId());
        assertTrue(loaded.isRead());
        assertEquals(NotificationAction.OPEN_REPORT, loaded.getAction());
        assertEquals("donor-report", loaded.getReminderId());
        assertEquals(base, loaded.getCreatedAt());
        assertEquals(1, again.unreadCount());
    }

    @Test
    void anUnknownActionInTheFileBecomesNone() throws Exception {
        Files.writeString(dir.resolve("notifications.json"), """
                [{"id":"%s","title":"t","message":"m","createdAt":"2026-11-21T10:00:00Z","read":false,"action":"SOMETHING_NEW","reminderId":null}]
                """.formatted(UUID.randomUUID()));
        NotificationCenter again = newCenter();
        again.load();
        assertEquals(NotificationAction.NONE, again.getAll().get(0).getAction());
    }

    @Test
    void aCorruptFileIsAStorageError() throws Exception {
        Files.writeString(dir.resolve("notifications.json"), "{ nope");
        assertThrows(StorageException.class, newCenter()::load);
    }
}
