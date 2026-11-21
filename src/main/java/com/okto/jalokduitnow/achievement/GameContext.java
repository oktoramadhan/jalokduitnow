package com.okto.jalokduitnow.achievement;

import com.okto.jalokduitnow.model.Category;
import com.okto.jalokduitnow.model.GameProfile;
import com.okto.jalokduitnow.model.SavingsGoal;
import com.okto.jalokduitnow.model.Transaction;
import com.okto.jalokduitnow.schema.TableSchema;

import java.time.Clock;
import java.util.List;

/** Everything an achievement may look at to decide whether it is unlocked. */
public record GameContext(GameProfile profile, List<Transaction> transactions, TableSchema schema,
                          List<Category> categories, List<SavingsGoal> goals, Clock clock, boolean donorReportShared) {

    /** A context where no donor report has been shared yet. */
    public GameContext(GameProfile profile, List<Transaction> transactions, TableSchema schema,
                       List<Category> categories, List<SavingsGoal> goals, Clock clock) {
        this(profile, transactions, schema, categories, goals, clock, false);
    }
}
