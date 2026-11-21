package com.okto.jalokduitnow.storage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A full backup in one JSON file. It reuses the same records the app saves its own files with, so a backup is
 * just those files side by side plus a small header; the {@code formatVersion} lets a future restore recognize it.
 * Recipients and donor settings are empty when the user chose not to include donor data.
 */
public record BackupDto(String app, int formatVersion, Instant exportedAt,
                        List<ColumnDto> columns, List<CategoryDto> categories, List<TransactionDto> transactions,
                        GameStateDto game, List<RecipientDto> recipients, List<UUID> donorCategoryIds) {

    public static final String APP_NAME = "JalokDuitNow";
    public static final int CURRENT_VERSION = 1;
}
