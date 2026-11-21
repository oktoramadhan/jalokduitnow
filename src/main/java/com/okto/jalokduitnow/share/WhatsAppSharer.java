package com.okto.jalokduitnow.share;

import com.okto.jalokduitnow.exception.ShareException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Shares through WhatsApp's click-to-chat link. The browser (and then WhatsApp) opens with the message already
 * typed; the user still has to press Send, so nothing is ever sent behind their back.
 * <ul>
 *   <li>With a number: {@code https://wa.me/<number>?text=<url-encoded text>}</li>
 *   <li>Without one: {@code https://wa.me/?text=<url-encoded text>} (WhatsApp asks who to send it to)</li>
 * </ul>
 */
public class WhatsAppSharer implements Sharer {
    private static final String BASE = "https://wa.me/";
    /** Very long links are refused by browsers; keep a safe margin. */
    static final int MAX_URI_LENGTH = 8000;

    private final UriOpener opener;
    private final String phoneNumber;   // normalized digits, or null for "choose in WhatsApp"

    public WhatsAppSharer(UriOpener opener) {
        this(opener, null);
    }

    /** @param phoneNumber digits only with country code (as PhoneNumberNormalizer returns), or null */
    public WhatsAppSharer(UriOpener opener, String phoneNumber) {
        this.opener = opener;
        this.phoneNumber = phoneNumber == null || phoneNumber.isBlank() ? null : phoneNumber;
    }

    /** The same sharer aimed at a specific number (null aims at "no number"). */
    public WhatsAppSharer forNumber(String number) {
        return new WhatsAppSharer(opener, number);
    }

    @Override public String name() { return "WhatsApp"; }

    @Override
    public void share(String text) throws ShareException {
        if (text == null || text.isBlank()) throw new ShareException("Tidak ada teks untuk dibagikan");
        URI uri = buildUri(phoneNumber, text);
        if (uri.toString().length() > MAX_URI_LENGTH) {
            throw new ShareException("Pesan terlalu panjang untuk dibuka di WhatsApp. "
                    + "Matikan detail transaksi atau gunakan tombol Salin.");
        }
        try {
            opener.open(uri);
        } catch (IOException | RuntimeException e) {
            throw new ShareException("Gagal membuka WhatsApp: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the click-to-chat address. Text is UTF-8 percent-encoded with spaces as %20
     * (URLEncoder would use '+', which WhatsApp shows literally).
     */
    public static URI buildUri(String number, String text) throws ShareException {
        if (number != null && !number.matches("[0-9]{8,15}")) {
            throw new ShareException("Nomor tidak valid untuk WhatsApp: " + number);
        }
        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
        return URI.create(BASE + (number == null ? "" : number) + "?text=" + encoded);
    }
}
