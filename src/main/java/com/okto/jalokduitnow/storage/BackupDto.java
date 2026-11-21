package com.okto.jalokduitnow.storage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A full backup in one JSON file. It reuses the same records the app saves its own files with, so a backup is
 * just those files side by side plus a small header; the {@code formatVersion} lets a restore recognize it.
 *
 * <p>Newer fields are optional so older backups still restore: {@code appVersion} (which build wrote it),
 * {@code includesDonorData} (false when the user left donors out: a restore then keeps the current donors instead of
 * erasing them) and {@code reminderSettings} (absent: a restore keeps the current reminder settings).
 */
public record BackupDto(String app, int formatVersion, Instant exportedAt,
                        List<ColumnDto> columns, List<CategoryDto> categories, List<TransactionDto> transactions,
                        GameStateDto game, List<RecipientDto> recipients, List<UUID> donorCategoryIds,
                        String appVersion, Boolean includesDonorData, ReminderSettingsDto reminderSettings) {

    public static final String APP_NAME = "JalokDuitNow";
    public static final int CURRENT_VERSION = 1;

    /** A backup without the optional newer fields. */
    public BackupDto(String app, int formatVersion, Instant exportedAt,
                     List<ColumnDto> columns, List<CategoryDto> categories, List<TransactionDto> transactions,
                     GameStateDto game, List<RecipientDto> recipients, List<UUID> donorCategoryIds) {
        this(app, formatVersion, exportedAt, columns, categories, transactions, game, recipients, donorCategoryIds,
                null, null, null);
    }

    /** True unless the backup says it was made without donor data (older backups always contain it). */
    public boolean hasDonorData() {
        return !Boolean.FALSE.equals(includesDonorData);
    }
}
