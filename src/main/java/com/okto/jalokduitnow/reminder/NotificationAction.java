package com.okto.jalokduitnow.reminder;

/** What the button on a notification does. The UI maps each one to a page or dialog. */
public enum NotificationAction {
    NONE("", "info"),
    ADD_TRANSACTION("Tambah Transaksi", "plus"),
    OPEN_REPORT("Buka Laporan", "report"),
    OPEN_CATEGORIES("Lihat Kategori", "categories");

    private final String label;
    private final String iconKey;

    NotificationAction(String label, String iconKey) {
        this.label = label;
        this.iconKey = iconKey;
    }

    public String label() { return label; }
    public String iconKey() { return iconKey; }
    public boolean hasButton() { return this != NONE; }
}
