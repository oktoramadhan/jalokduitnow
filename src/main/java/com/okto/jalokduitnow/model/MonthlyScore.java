package com.okto.jalokduitnow.model;

/**
 * A grade for one month from the balance/income ratio (how much of the income was kept):
 * A at 30% or more, B at 15%, C at 0% (broke even), D below that. No activity gives NONE.
 */
public record MonthlyScore(Grade grade, long income, long expense) {
    public enum Grade { A, B, C, D, NONE }

    public static MonthlyScore of(long income, long expense) {
        if (income == 0 && expense == 0) return new MonthlyScore(Grade.NONE, 0, 0);
        if (income <= 0) return new MonthlyScore(Grade.D, income, expense); // spending with no income
        double ratio = (double) (income - expense) / income;
        Grade grade = ratio >= 0.30 ? Grade.A : ratio >= 0.15 ? Grade.B : ratio >= 0 ? Grade.C : Grade.D;
        return new MonthlyScore(grade, income, expense);
    }

    /** balance / income, or 0 when there is no income. */
    public double ratio() {
        return income <= 0 ? 0 : (double) (income - expense) / income;
    }
}
