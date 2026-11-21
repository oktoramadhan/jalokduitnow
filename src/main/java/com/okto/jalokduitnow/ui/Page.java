package com.okto.jalokduitnow.ui;

/** The pages reachable from the sidebar. */
public enum Page {
    HOME("dashboard", "Beranda"),
    TRANSACTIONS("transactions", "Transaksi"),
    CATEGORIES("categories", "Kategori"),
    COLUMNS("columns", "Kolom"),
    QUESTS("trophy", "Quest & Badge"),
    SETTINGS("settings", "Pengaturan");

    private final String iconKey;
    private final String title;

    Page(String iconKey, String title) {
        this.iconKey = iconKey;
        this.title = title;
    }

    public String iconKey() { return iconKey; }
    public String title() { return title; }
}
