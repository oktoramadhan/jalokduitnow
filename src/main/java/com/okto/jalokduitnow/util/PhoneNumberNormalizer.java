package com.okto.jalokduitnow.util;

import com.okto.jalokduitnow.exception.InvalidRecipientException;

/**
 * Turns a phone number as people type it into the digits-only international form wa.me needs.
 * Spaces, dashes, dots, parentheses and "+" are removed; a leading "0" becomes Indonesia's country code "62"
 * (and a leading "00" is the international call prefix, so it is just dropped).
 * "+62 0812..." (country code followed by the local zero) is repaired to "62812...".
 * Anything else that is not a digit, or a number that is too short or too long, is refused.
 */
public final class PhoneNumberNormalizer {
    public static final String INDONESIA = "62";
    public static final int MIN_DIGITS = 10;
    public static final int MAX_DIGITS = 15;   // the E.164 limit

    private PhoneNumberNormalizer() {}

    public static String normalize(String raw) throws InvalidRecipientException {
        if (raw == null || raw.isBlank()) throw new InvalidRecipientException("Nomor telepon wajib diisi");

        String digits = raw.trim().replaceAll("[\\s\\-().+]", "");
        if (!digits.matches("[0-9]+")) throw new InvalidRecipientException("Nomor telepon hanya boleh berisi angka");

        if (digits.startsWith("00")) digits = digits.substring(2);                 // international call prefix
        else if (digits.startsWith("0")) digits = INDONESIA + digits.substring(1); // local format
        if (digits.startsWith(INDONESIA + "0")) digits = INDONESIA + digits.substring(3); // "62" + trunk zero

        if (digits.length() < MIN_DIGITS) {
            throw new InvalidRecipientException("Nomor telepon terlalu pendek (minimal " + MIN_DIGITS + " angka)");
        }
        if (digits.length() > MAX_DIGITS) {
            throw new InvalidRecipientException("Nomor telepon terlalu panjang (maksimal " + MAX_DIGITS + " angka)");
        }
        return digits;
    }
}
