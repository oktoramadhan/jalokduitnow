package com.okto.jalokduitnow.ui;

import java.text.NumberFormat;
import java.util.Locale;

/** Shared Indonesian formatting helpers. */
public final class Formats {
    public static final Locale ID = Locale.forLanguageTag("id-ID");
    private static final NumberFormat RUPIAH = NumberFormat.getCurrencyInstance(ID);

    private Formats() {}

    public static String rupiah(long amount) {
        return RUPIAH.format(amount);
    }
}
