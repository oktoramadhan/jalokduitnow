package com.okto.jalokduitnow.service;

/** The three things the "Hapus data" section can do, from smallest to largest. */
public enum DeleteAction {
    TRANSACTIONS("Hapus semua transaksi"),
    GAME("Reset progres game"),
    FACTORY("Reset pabrik");

    private final String title;

    DeleteAction(String title) {
        this.title = title;
    }

    public String title() { return title; }
}
