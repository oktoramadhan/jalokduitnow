package com.okto.jalokduitnow.share;

import com.okto.jalokduitnow.exception.ShareException;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.util.function.Consumer;

/** "Shares" by copying the text to the clipboard. The writer is injectable so tests need no JavaFX. */
public class ClipboardSharer implements Sharer {
    private final Consumer<String> writer;

    public ClipboardSharer(Consumer<String> writer) {
        this.writer = writer;
    }

    /** The system clipboard. Must be used from the FX thread. */
    public static ClipboardSharer system() {
        return new ClipboardSharer(text -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            Clipboard.getSystemClipboard().setContent(content);
        });
    }

    @Override public String name() { return "Clipboard"; }

    @Override
    public void share(String text) throws ShareException {
        if (text == null || text.isBlank()) throw new ShareException("Tidak ada teks untuk disalin");
        writer.accept(text);
    }
}
