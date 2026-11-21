package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.reminder.AppNotification;
import com.okto.jalokduitnow.storage.NotificationDto;
import com.okto.jalokduitnow.storage.Repository;
import com.okto.jalokduitnow.thread.AutoSaveService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The list behind the bell icon: the most recent notifications, newest first, with a read flag.
 * It keeps only the last {@value #MAX_KEPT} (older ones fall off) and is saved (notifications.json), so unread
 * notifications are still there after a restart. Used on the FX thread; listeners refresh the bell and its list.
 */
public class NotificationCenter {
    public static final int MAX_KEPT = 50;

    private final Repository<NotificationDto> repository;
    private final AutoSaveService<NotificationDto> autoSave;
    private final List<AppNotification> items = new ArrayList<>();   // newest first
    private final List<Runnable> listeners = new ArrayList<>();

    public NotificationCenter(Repository<NotificationDto> repository) {
        this.repository = repository;
        this.autoSave = new AutoSaveService<>(repository, 300);
    }

    public void addListener(Runnable listener) { listeners.add(listener); }

    public void setErrorHandler(Consumer<StorageException> handler) { autoSave.setErrorHandler(handler); }

    public void flush() { autoSave.flush(); }

    public void load() throws StorageException {
        items.clear();
        for (NotificationDto dto : repository.findAll()) items.add(dto.toNotification());
        items.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        while (items.size() > MAX_KEPT) items.remove(items.size() - 1);
    }

    public List<AppNotification> getAll() {
        return List.copyOf(items);
    }

    public int unreadCount() {
        return (int) items.stream().filter(n -> !n.isRead()).count();
    }

    public void add(AppNotification notification) {
        items.add(0, notification);
        while (items.size() > MAX_KEPT) items.remove(items.size() - 1);
        changed();
    }

    public void markRead(UUID id) {
        boolean any = false;
        for (AppNotification n : items) {
            if (n.getId().equals(id) && !n.isRead()) {
                n.markRead();
                any = true;
            }
        }
        if (any) changed();
    }

    public void markAllRead() {
        boolean any = false;
        for (AppNotification n : items) {
            if (!n.isRead()) {
                n.markRead();
                any = true;
            }
        }
        if (any) changed();
    }

    public void clear() {
        if (items.isEmpty()) return;
        items.clear();
        changed();
    }

    private void changed() {
        autoSave.save(items.stream().map(NotificationDto::from).toList());
        listeners.forEach(Runnable::run);
    }
}
