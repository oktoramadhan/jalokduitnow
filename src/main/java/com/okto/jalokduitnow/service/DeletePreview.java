package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.storage.BackupDto;

import java.util.ArrayList;
import java.util.List;

/**
 * What an action would delete and what it keeps, with the real counts, for the confirmation dialog
 * ("320 transaksi, 3 kolom kustom"). Built from a snapshot of the current data, so the numbers are exact.
 *
 * @param willDelete   one line per kind of data that goes away
 * @param kept         what stays
 * @param backupFiles  how many automatic and safety backups exist (only a factory reset may delete them, if asked)
 */
public record DeletePreview(DeleteAction action, List<String> willDelete, List<String> kept, int backupFiles) {

    public static DeletePreview of(DeleteAction action, BackupDto current, int notifications, int backupFiles) {
        BackupPreview p = BackupPreview.of(current);
        int streak = current.game().profile().streak();
        int quests = current.game().quests() == null ? 0 : current.game().quests().size();

        List<String> delete = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        switch (action) {
            case TRANSACTIONS -> {
                delete.add(p.transactions() + " transaksi");
                kept.add("kolom kustom (" + p.customColumns() + "), kategori (" + p.categories() + "), donor (" + p.recipients() + ")");
                kept.add("progres game (Lv " + p.level() + ", " + p.xp() + " XP) dan semua pengaturan");
            }
            case GAME -> {
                delete.add("XP dan level (Lv " + p.level() + ", " + p.xp() + " XP)");
                delete.add("streak (" + streak + " hari) dan freeze token");
                delete.add(p.badges() + " badge");
                delete.add(quests + " quest minggu ini");
                delete.add(p.goals() + " target tabungan");
                kept.add(p.transactions() + " transaksi, kolom kustom, kategori, donor, dan semua pengaturan");
            }
            case FACTORY -> {
                delete.add(p.transactions() + " transaksi, " + p.customColumns() + " kolom kustom, " + p.categories() + " kategori");
                delete.add(p.recipients() + " donor dan pengaturan donor");
                delete.add("progres game (Lv " + p.level() + ", " + p.xp() + " XP, " + p.badges() + " badge, " + p.goals() + " target tabungan)");
                delete.add("pengaturan pengingat, tema, dan " + notifications + " notifikasi");
                kept.add("cadangan yang sudah ada (kecuali kamu memilih menghapusnya) dan file yang kamu simpan sendiri");
                kept.add("aplikasi kembali ke pengaturan awal, seperti pertama kali dibuka");
            }
        }
        return new DeletePreview(action, List.copyOf(delete), List.copyOf(kept), backupFiles);
    }
}
