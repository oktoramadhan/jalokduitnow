package com.okto.jalokduitnow.util;

import java.text.NumberFormat;
import java.util.Locale;

/** Rupiah text with Locale id-ID, usable outside the UI (reports, quest titles). Rupiah has no cents. */
public final class Rupiah {
    public static final Locale ID = Locale.forLanguageTag("id-ID");

    private Rupiah() {}

    /** "Rp 25.000" */
    public static String format(long amount) {
        return "Rp " + NumberFormat.getIntegerInstance(ID).format(amount);
    }
}
