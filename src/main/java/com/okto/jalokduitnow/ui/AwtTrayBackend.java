package com.okto.jalokduitnow.ui;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * The real system tray through java.awt.SystemTray (macOS menu bar, Windows notification area).
 * The tray icon is drawn in code (a blue rounded square), so there is no image file or platform font to ship.
 * Every call may fail on some systems; the notifier around it catches everything.
 */
public class AwtTrayBackend implements TrayBackend {
    private TrayIcon icon;

    @Override
    public boolean isSupported() {
        return !GraphicsEnvironment.isHeadless() && SystemTray.isSupported();
    }

    @Override
    public synchronized void show(String title, String message) {
        if (icon == null) {
            icon = new TrayIcon(drawIcon(), "JalokDuitNow");
            icon.setImageAutoSize(true);
            try {
                SystemTray.getSystemTray().add(icon);
            } catch (AWTException e) {
                icon = null;
                throw new IllegalStateException("Ikon tray tidak bisa ditambahkan", e);
            }
        }
        icon.displayMessage(title, message, TrayIcon.MessageType.INFO);
    }

    @Override
    public synchronized void close() {
        if (icon == null) return;
        try {
            SystemTray.getSystemTray().remove(icon);
        } finally {
            icon = null;
        }
    }

    private static Image drawIcon() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0x3B82F6));
            g.fill(new RoundRectangle2D.Float(2, 2, 28, 28, 10, 10));
            g.setColor(Color.WHITE);
            g.fillRoundRect(9, 11, 14, 11, 4, 4);       // a simple wallet shape
            g.setColor(new Color(0x3B82F6));
            g.fillOval(18, 14, 4, 4);
        } finally {
            g.dispose();
        }
        return image;
    }
}
