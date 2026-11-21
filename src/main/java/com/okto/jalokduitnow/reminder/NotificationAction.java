package com.okto.jalokduitnow.reminder;

/** What the button on a notification does. The UI maps each one to a page or dialog. */
public enum NotificationAction {
    NONE("", "info"),
    ADD_TRANSACTION("Tambah Transaksi", "plus"),
    OPEN_REPORT("Buka Laporan", "report"),
    OPEN_CATEGORIES("Lihat Kategori", "categories"),
    /** "Restore it" after data was deleted: only offered for a short time. */
    OPEN_RESTORE("Pulihkan", "restore", java.time.Duration.ofMinutes(15));

    private final String label;
    private final String iconKey;
    private final java.time.Duration validFor;   // null: the button never expires

    NotificationAction(String label, String iconKey) {
        this(label, iconKey, null);
    }

    NotificationAction(String label, String iconKey, java.time.Duration validFor) {
        this.label = label;
        this.iconKey = iconKey;
        this.validFor = validFor;
    }

    /** False once a time-limited action is older than its window; the notification stays, its button goes. */
    public boolean isAvailable(java.time.Instant createdAt, java.time.Instant now) {
        return hasButton() && (validFor == null || now.isBefore(createdAt.plus(validFor)));
    }

    public String label() { return label; }
    public String iconKey() { return iconKey; }
    public boolean hasButton() { return this != NONE; }
}
