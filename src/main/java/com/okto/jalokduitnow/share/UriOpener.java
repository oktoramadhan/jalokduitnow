package com.okto.jalokduitnow.share;

import java.io.IOException;
import java.net.URI;

/** Opens a web address in the user's browser. A small seam so tests do not open real browsers. */
@FunctionalInterface
public interface UriOpener {
    void open(URI uri) throws IOException;
}
