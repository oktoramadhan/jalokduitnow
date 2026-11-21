package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.storage.BackupDto;
import com.okto.jalokduitnow.storage.ColumnDto;

import java.time.Instant;

/**
 * The numbers shown in the restore preview, taken from a backup (or from a snapshot of the current data, so the two can be
 * compared side by side). The game profile is read through GameProfile.restore, so level and XP are what the app would show.
 */
public record BackupPreview(Instant exportedAt, String appVersion, int formatVersion, int transactions, int customColumns,
                            int categories, int badges, int level, long xp, int goals, int recipients,
                            boolean hasDonorData, boolean hasReminderSettings) {

    public static BackupPreview of(BackupDto b) {
        var profile = b.game().profile().toProfile();
        int custom = (int) b.columns().stream().filter(c -> !SchemaService.DEFAULT_COLUMN_ID.equals(c.id())).count();
        return new BackupPreview(b.exportedAt(), b.appVersion(), b.formatVersion(), b.transactions().size(), custom,
                b.categories().size(), b.game().badges() == null ? 0 : b.game().badges().size(), profile.getLevel(),
                profile.getXp(), b.game().goals().size(), b.recipients().size(), b.hasDonorData(), b.reminderSettings() != null);
    }
}
