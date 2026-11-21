package com.okto.jalokduitnow.share;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

/**
 * Opens addresses with {@code Desktop.browse}, which works the same on macOS and Windows.
 * If the platform has no browse support, a fallback opener (the JavaFX host services in the app) is used instead.
 */
public class DesktopUriOpener implements UriOpener {
    private final UriOpener fallback;

    public DesktopUriOpener(UriOpener fallback) {
        this.fallback = fallback;
    }

    @Override
    public void open(URI uri) throws IOException {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(uri);
        } else if (fallback != null) {
            fallback.open(uri);
        } else {
            throw new IOException("Peramban tidak tersedia di sistem ini");
        }
    }
}
