package com.okto.jalokduitnow.share;

import com.okto.jalokduitnow.exception.ShareException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SharerTest {
    private final List<URI> opened = new ArrayList<>();
    private final UriOpener recording = opened::add;

    // ---- wa.me address ----

    @Test
    void addressWithANumberAndEncodedText() throws Exception {
        URI uri = WhatsAppSharer.buildUri("6281234567890", "Halo Pak Budi,\nSaldo: Rp 1.000 & lebih");
        assertEquals("https://wa.me/6281234567890?text=Halo%20Pak%20Budi%2C%0ASaldo%3A%20Rp%201.000%20%26%20lebih", uri.toString());
    }

    @Test
    void addressWithoutANumberKeepsTheOldBehavior() throws Exception {
        assertEquals("https://wa.me/?text=Halo%20dunia", WhatsAppSharer.buildUri(null, "Halo dunia").toString());
    }

    @Test
    void spacesAreEncodedAsPercent20NotPlus() throws Exception {
        String uri = WhatsAppSharer.buildUri("6281234567890", "a b c").toString();
        assertTrue(uri.endsWith("?text=a%20b%20c"));
        assertFalse(uri.contains("+"));
    }

    @Test
    void textIsEncodedAsUtf8SoSpecialCharactersSurvive() throws Exception {
        URI uri = WhatsAppSharer.buildUri("6281234567890", "café 100% #1 ?x=y");
        assertTrue(uri.toString().endsWith("?text=caf%C3%A9%20100%25%20%231%20%3Fx%3Dy"), uri.toString());
        // and decodes back to the original
        assertEquals("café 100% #1 ?x=y", java.net.URLDecoder.decode(uri.getRawQuery().substring("text=".length()), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void theWholeReportRoundTripsThroughTheAddress() throws Exception {
        String report = "Halo Pak Budi,\n\nDana diterima dari donor: Rp 3.000.000\n- Makan: Rp 600.000 (75%)\n";
        URI uri = WhatsAppSharer.buildUri("6281234567890", report);
        String back = java.net.URLDecoder.decode(uri.getRawQuery().substring("text=".length()), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(report, back);
        assertEquals("wa.me", uri.getHost());
        assertEquals("https", uri.getScheme());
        assertEquals("/6281234567890", uri.getPath());
    }

    @Test
    void anInvalidNumberIsRefused() {
        assertThrows(ShareException.class, () -> WhatsAppSharer.buildUri("0812-345", "x"));
        assertThrows(ShareException.class, () -> WhatsAppSharer.buildUri("12", "x"));
        assertThrows(ShareException.class, () -> WhatsAppSharer.buildUri("62812345678901234567", "x"));
    }

    // ---- sharing ----

    @Test
    void sharingOpensTheAddressInTheBrowser() throws Exception {
        new WhatsAppSharer(recording, "6281234567890").share("Halo");
        assertEquals(List.of(URI.create("https://wa.me/6281234567890?text=Halo")), opened);
    }

    @Test
    void sharingWithoutANumberOpensTheNumberlessAddress() throws Exception {
        new WhatsAppSharer(recording).share("Halo");
        assertEquals(List.of(URI.create("https://wa.me/?text=Halo")), opened);
        new WhatsAppSharer(recording, "  ").share("Halo");        // a blank number means none
        assertEquals(URI.create("https://wa.me/?text=Halo"), opened.get(1));
    }

    @Test
    void forNumberAimsTheSameSharerAtAnotherRecipient() throws Exception {
        WhatsAppSharer base = new WhatsAppSharer(recording);
        base.forNumber("6281298765432").share("Hi");
        base.share("Hi");
        assertEquals("https://wa.me/6281298765432?text=Hi", opened.get(0).toString());
        assertEquals("https://wa.me/?text=Hi", opened.get(1).toString());
        assertEquals("WhatsApp", base.name());
    }

    @Test
    void blankTextIsRefusedAndNothingOpens() {
        WhatsAppSharer s = new WhatsAppSharer(recording, "6281234567890");
        assertThrows(ShareException.class, () -> s.share(null));
        assertThrows(ShareException.class, () -> s.share("   "));
        assertTrue(opened.isEmpty());
    }

    @Test
    void aMessageThatIsTooLongIsRefusedWithAHint() {
        WhatsAppSharer s = new WhatsAppSharer(recording, "6281234567890");
        ShareException e = assertThrows(ShareException.class, () -> s.share("kata ".repeat(3_000)));
        assertTrue(e.getMessage().contains("Salin"));
        assertTrue(opened.isEmpty());
    }

    @Test
    void aBrowserFailureBecomesAShareException() {
        WhatsAppSharer s = new WhatsAppSharer(uri -> { throw new IOException("tidak ada peramban"); }, "6281234567890");
        ShareException e = assertThrows(ShareException.class, () -> s.share("Halo"));
        assertTrue(e.getMessage().contains("tidak ada peramban"));
        assertNotNull(e.getCause());
    }

    // ---- clipboard ----

    @Test
    void theClipboardSharerWritesTheExactText() throws Exception {
        List<String> written = new ArrayList<>();
        ClipboardSharer sharer = new ClipboardSharer(written::add);
        sharer.share("Halo\nDunia");
        assertEquals(List.of("Halo\nDunia"), written);
        assertEquals("Clipboard", sharer.name());
        assertThrows(ShareException.class, () -> sharer.share(" "));
        assertEquals(1, written.size());
    }

    @Test
    void bothSharersAreUsedThroughTheSameInterface() throws Exception {
        List<String> clip = new ArrayList<>();
        List<Sharer> sharers = List.of(new WhatsAppSharer(recording), new ClipboardSharer(clip::add));
        for (Sharer s : sharers) s.share("Laporan");
        assertEquals(1, opened.size());
        assertEquals(List.of("Laporan"), clip);
    }
}
