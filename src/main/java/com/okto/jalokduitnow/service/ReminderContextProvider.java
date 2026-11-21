package com.okto.jalokduitnow.service;

import com.okto.jalokduitnow.model.GameProfile;
import com.okto.jalokduitnow.reminder.ReminderContext;

import java.time.Clock;
import java.time.LocalDate;
import java.util.function.Supplier;

/** Takes a fresh read-only snapshot of the app's data for the reminders. Call it on the FX thread. */
public class ReminderContextProvider implements Supplier<ReminderContext> {
    private final FinanceService finance;
    private final GameService game;
    private final CategoryService categories;
    private final RecipientService recipients;
    private final DonorSettings donorSettings;
    private final Clock clock;

    public ReminderContextProvider(FinanceService finance, GameService game, CategoryService categories,
                                   RecipientService recipients, DonorSettings donorSettings, Clock clock) {
        this.finance = finance;
        this.game = game;
        this.categories = categories;
        this.recipients = recipients;
        this.donorSettings = donorSettings;
        this.clock = clock;
    }

    @Override
    public ReminderContext get() {
        GameProfile p = game.profile();
        return new ReminderContext(LocalDate.now(clock), finance.getAll(), p.getStreak(), p.getFreezeTokens(),
                p.getLastActiveDate(), categories.getAll(), recipients.getAll(), donorSettings.donorCategoryIds());
    }
}
