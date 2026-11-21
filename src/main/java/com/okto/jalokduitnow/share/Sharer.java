package com.okto.jalokduitnow.share;

import com.okto.jalokduitnow.exception.ShareException;

/** A way to get a piece of text out of the app: WhatsApp, the clipboard, later Telegram or email. */
public interface Sharer {
    /** Name for the UI, e.g. "WhatsApp". */
    String name();

    void share(String text) throws ShareException;
}
