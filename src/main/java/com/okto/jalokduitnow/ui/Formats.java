package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.util.Rupiah;

import java.util.Locale;

/** Shared Indonesian formatting helpers (Locale id-ID). */
public final class Formats {
    public static final Locale ID = Locale.forLanguageTag("id-ID");

    private Formats() {}

    /** "Rp 25.000"; Rupiah has no cents. */
    public static String rupiah(long amount) {
        return Rupiah.format(amount);
    }

    /** "+Rp 25.000" for money in, "-Rp 25.000" for money out. */
    public static String signedRupiah(long signedAmount) {
        return (signedAmount >= 0 ? "+" : "-") + rupiah(Math.abs(signedAmount));
    }
}
