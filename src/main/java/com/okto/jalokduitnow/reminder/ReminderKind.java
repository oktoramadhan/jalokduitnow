package com.okto.jalokduitnow.reminder;

/** The kinds of reminder the user can switch on and off, with the text the settings page shows. */
public enum ReminderKind {
    DAILY_LOG("Pengingat mencatat", "Mengingatkan di jam pilihanmu kalau belum ada catatan hari ini.", true),
    STREAK_RISK("Penjaga streak", "Di malam hari, mengingatkan agar streak yang sedang berjalan tidak terputus.", true),
    DONOR_REPORT("Laporan donor bulanan", "Sekali sebulan di tanggal pilihanmu, mengingatkan untuk menyiapkan laporan donor.", false),
    BUDGET("Batas budget", "Memberi tahu saat pengeluaran sebuah kategori mendekati dan mencapai budget bulanannya.", true);

    private final String title;
    private final String description;
    private final boolean enabledByDefault;

    ReminderKind(String title, String description, boolean enabledByDefault) {
        this.title = title;
        this.description = description;
        this.enabledByDefault = enabledByDefault;
    }

    public String title() { return title; }
    public String description() { return description; }
    public boolean enabledByDefault() { return enabledByDefault; }
}
