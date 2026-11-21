package com.okto.jalokduitnow.service;

/**
 * Reloads every service from the data files, in dependency order (schema, categories, then the transactions that point
 * at them, then donors, reminder settings and the game). Every service tells its listeners, so each view refreshes
 * without restarting the app. Rewards are suspended for the whole reload: restored data is applied as it was saved and
 * must never trigger XP or badges. Used by the restore, on the FX thread.
 */
public class DataReloader implements RestoreService.Reloader {
    private final FinanceService finance;
    private final SchemaService schema;
    private final CategoryService categories;
    private final GameService game;
    private final RecipientService recipients;
    private final DonorSettings donors;
    private final ReminderSettingsService reminders;

    public DataReloader(FinanceService finance, SchemaService schema, CategoryService categories, GameService game,
                        RecipientService recipients, DonorSettings donors, ReminderSettingsService reminders) {
        this.finance = finance;
        this.schema = schema;
        this.categories = categories;
        this.game = game;
        this.recipients = recipients;
        this.donors = donors;
        this.reminders = reminders;
    }

    @Override
    public void reloadAll() throws Exception {
        game.suspendRewards(true);
        try {
            schema.reload();
            categories.reload();
            finance.reload(schema.getSchema(), categories);
            recipients.reload();
            donors.reload();
            reminders.reload();
            game.reload();
        } finally {
            game.suspendRewards(false);
        }
    }
}
