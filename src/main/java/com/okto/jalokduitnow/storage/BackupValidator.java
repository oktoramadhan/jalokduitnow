package com.okto.jalokduitnow.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.okto.jalokduitnow.exception.*;
import com.okto.jalokduitnow.model.Category;
import com.okto.jalokduitnow.model.CategoryResolver;
import com.okto.jalokduitnow.model.CategoryType;
import com.okto.jalokduitnow.schema.TableSchema;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Checks a backup completely BEFORE any data is touched. Two levels:
 * <ol>
 *   <li>{@link #parseStructure}: is it JSON, is it ours, is the version one this build understands, are all required
 *       sections present, and does the JSON bind to a BackupDto.</li>
 *   <li>{@link #validate}: additionally builds the real objects (columns, categories, transactions, game state, goals,
 *       recipients, reminder settings) in memory, with the same validating constructors the app uses. A transaction that
 *       points at a category the backup does not contain, a duplicate id or an impossible value is found here, so a
 *       restore never starts with data it cannot load.</li>
 * </ol>
 * Everything that is wrong is reported as a StorageException with a plain message; nothing is ever changed.
 */
public final class BackupValidator {
    private BackupValidator() {}

    public static BackupDto parseStructure(String json) throws StorageException {
        ObjectMapper mapper = JsonSupport.newMapper();
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new StorageException("File ini bukan JSON yang valid, jadi bukan cadangan JalokDuitNow", e);
        }
        if (root == null || !root.isObject() || !BackupDto.APP_NAME.equals(root.path("app").asText())) {
            throw new StorageException("Bukan file cadangan JalokDuitNow", null);
        }

        JsonNode version = root.get("formatVersion");
        if (version == null || !version.isInt() || version.asInt() < 1) {
            throw new StorageException("Versi format cadangan tidak ada atau tidak valid", null);
        }
        if (version.asInt() > BackupDto.CURRENT_VERSION) {
            throw new StorageException("Cadangan ini dibuat oleh versi aplikasi yang lebih baru (format " + version.asInt()
                    + "). Perbarui aplikasi untuk memulihkannya.", null);
        }

        requireSection(root, "columns", "kolom (schema)");
        requireSection(root, "categories", "kategori");
        requireSection(root, "transactions", "transaksi");
        requireSection(root, "game", "game");
        requireSection(root, "recipients", "penerima (donor)");
        requireSection(root.get("game"), "profile", "profil game");
        requireSection(root.get("game"), "goals", "target tabungan");

        try {
            return mapper.treeToValue(root, BackupDto.class);
        } catch (JsonProcessingException e) {
            throw new StorageException("Isi cadangan tidak bisa dibaca: " + firstLine(e.getOriginalMessage()), e);
        }
    }

    /** Structure plus a full in-memory load of everything. */
    public static BackupDto validate(String json) throws StorageException {
        BackupDto backup = parseStructure(json);
        try {
            TableSchema schema = new TableSchema();
            for (ColumnDto column : backup.columns()) schema.add(column.toColumn());

            Map<UUID, Category> categories = new HashMap<>();
            for (CategoryDto dto : backup.categories()) {
                Category c = dto.toCategory();
                if (categories.put(c.getId(), c) != null) throw new StorageException("Kategori ganda di dalam cadangan: " + c.getName(), null);
            }

            Set<UUID> ids = new HashSet<>();
            CategoryResolver strict = new CategoryResolver() {
                @Override public UUID idFor(CategoryType type, String legacyName) throws InvalidCategoryException {
                    throw new InvalidCategoryException("transaksi masih memakai nama kategori format lama");
                }

                @Override public UUID resolve(UUID id, CategoryType type) throws InvalidCategoryException {
                    Category c = categories.get(id);
                    if (c == null) throw new InvalidCategoryException("sebuah transaksi memakai kategori yang tidak ada di cadangan");
                    if (c.getType() != type) throw new InvalidCategoryException("tipe sebuah transaksi tidak cocok dengan kategorinya");
                    return id;
                }
            };
            for (TransactionDto dto : backup.transactions()) {
                var t = dto.toTransaction(schema, strict);
                if (!ids.add(t.getId())) throw new StorageException("Transaksi ganda di dalam cadangan", null);
            }

            GameStateDto game = backup.game();
            game.profile().toProfile();
            if (game.quests() != null) game.quests().forEach(GameStateDto.QuestDto::toQuest);
            game.goals().forEach(GameStateDto.GoalDto::toGoal);

            for (RecipientDto dto : backup.recipients()) dto.toRecipient();
            if (backup.reminderSettings() != null) backup.reminderSettings().toSettings();
        } catch (StorageException e) {
            throw e;
        } catch (InvalidColumnException | InvalidCategoryException | InvalidTransactionException
                 | InvalidRecipientException | InvalidSettingException | RuntimeException e) {
            throw new StorageException("Cadangan rusak: " + firstLine(e.getMessage()), e);
        }
        return backup;
    }

    private static void requireSection(JsonNode parent, String name, String label) throws StorageException {
        if (parent == null || !parent.hasNonNull(name)) {
            throw new StorageException("Cadangan tidak lengkap: bagian " + label + " tidak ada", null);
        }
    }

    private static String firstLine(String message) {
        if (message == null) return "data tidak valid";
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
