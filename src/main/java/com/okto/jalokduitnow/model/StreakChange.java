package com.okto.jalokduitnow.model;

/**
 * How the streak moved.
 *
 * @param before          streak length before
 * @param after           streak length after
 * @param freezeTokensUsed tokens spent to save missed days
 * @param broken          true if the streak was reset to 0 because days were missed
 */
public record StreakChange(int before, int after, int freezeTokensUsed, boolean broken) {
    public boolean changed() {
        return before != after || freezeTokensUsed > 0;
    }
}
