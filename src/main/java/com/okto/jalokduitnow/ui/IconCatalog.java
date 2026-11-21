package com.okto.jalokduitnow.ui;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignB;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignE;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignG;
import org.kordamp.ikonli.materialdesign2.MaterialDesignH;
import org.kordamp.ikonli.materialdesign2.MaterialDesignI;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import org.kordamp.ikonli.materialdesign2.MaterialDesignM;
import org.kordamp.ikonli.materialdesign2.MaterialDesignN;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;
import org.kordamp.ikonli.materialdesign2.MaterialDesignV;
import org.kordamp.ikonli.materialdesign2.MaterialDesignW;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps stable string keys to vector icons (Material Design Icons through Ikonli).
 * Saved data (categories, badges) stores only the key, so the icon pack can change without touching files.
 * The icon font is bundled in the jar, so it looks the same on macOS and Windows.
 */
public final class IconCatalog {
    public static final String FALLBACK = "other";

    /** Icons offered when picking a category icon, in display order. */
    private static final Map<String, Ikon> CATEGORY_ICONS = new LinkedHashMap<>();
    /** Icons used by the app itself (navigation, buttons, badges...). */
    private static final Map<String, Ikon> APP_ICONS = new LinkedHashMap<>();

    static {
        // ---- category icons ----
        category("food", MaterialDesignF.FOOD);
        category("coffee", MaterialDesignC.COFFEE);
        category("transport", MaterialDesignB.BUS);
        category("car", MaterialDesignC.CAR);
        category("home", MaterialDesignH.HOME);
        category("school", MaterialDesignS.SCHOOL);
        category("health", MaterialDesignH.HEART_PULSE);
        category("savings", MaterialDesignP.PIGGY_BANK);
        category("shopping", MaterialDesignS.SHOPPING);
        category("bills", MaterialDesignR.RECEIPT);
        category("entertainment", MaterialDesignG.GAMEPAD_VARIANT);
        category("movie", MaterialDesignM.MOVIE);
        category("music", MaterialDesignM.MUSIC);
        category("sports", MaterialDesignD.DUMBBELL);
        category("travel", MaterialDesignA.AIRPLANE);
        category("phone", MaterialDesignC.CELLPHONE);
        category("gift", MaterialDesignG.GIFT);
        category("pets", MaterialDesignP.PAW);
        category("work", MaterialDesignB.BRIEFCASE);
        category("salary", MaterialDesignC.CASH);
        category("freelance", MaterialDesignL.LAPTOP);
        category("card", MaterialDesignC.CREDIT_CARD);
        category("bank", MaterialDesignB.BANK);
        category(FALLBACK, MaterialDesignD.DOTS_HORIZONTAL);

        // ---- app icons ----
        app("flame", MaterialDesignF.FIRE);
        app("shield", MaterialDesignS.SHIELD_CHECK);
        app("star", MaterialDesignS.STAR);
        app("trophy", MaterialDesignT.TROPHY);
        app("medal", MaterialDesignM.MEDAL);
        app("target", MaterialDesignT.TARGET);
        app("bolt", MaterialDesignL.LIGHTNING_BOLT);
        app("settings", MaterialDesignC.COG);
        app("dashboard", MaterialDesignV.VIEW_DASHBOARD);
        app("transactions", MaterialDesignS.SWAP_HORIZONTAL);
        app("categories", MaterialDesignT.TAG);
        app("columns", MaterialDesignV.VIEW_COLUMN);
        app("income", MaterialDesignA.ARROW_DOWN_BOLD);
        app("expense", MaterialDesignA.ARROW_UP_BOLD);
        app("wallet", MaterialDesignW.WALLET);
        app("logo", MaterialDesignW.WALLET);
        app("search", MaterialDesignM.MAGNIFY);
        app("moon", MaterialDesignW.WEATHER_NIGHT);
        app("sun", MaterialDesignW.WHITE_BALANCE_SUNNY);
        app("plus", MaterialDesignP.PLUS);
        app("edit", MaterialDesignP.PENCIL);
        app("delete", MaterialDesignD.DELETE);
        app("share", MaterialDesignS.SHARE_VARIANT);
        app("close", MaterialDesignC.CLOSE);
        app("check", MaterialDesignC.CHECK_CIRCLE);
        app("lock", MaterialDesignL.LOCK);
        app("calendar", MaterialDesignC.CALENDAR);
        app("bell", MaterialDesignB.BELL_RING);
        app("trend", MaterialDesignT.TRENDING_UP);
        app("builder", MaterialDesignV.VIEW_COLUMN);
        app("palette", MaterialDesignP.PALETTE);
        app("up", MaterialDesignC.CHEVRON_UP);
        app("down", MaterialDesignC.CHEVRON_DOWN);
        app("eye", MaterialDesignE.EYE);
        app("eye-off", MaterialDesignE.EYE_OFF);
        app("text", MaterialDesignF.FORMAT_TEXT);
        app("number", MaterialDesignN.NUMERIC);
        app("list", MaterialDesignF.FORMAT_LIST_BULLETED);
        app("toggle", MaterialDesignT.TOGGLE_SWITCH);
        app("alert", MaterialDesignA.ALERT);
        app("info", MaterialDesignI.INFORMATION);
    }

    private IconCatalog() {}

    private static void category(String key, Ikon icon) {
        CATEGORY_ICONS.put(key, icon);
    }

    private static void app(String key, Ikon icon) {
        APP_ICONS.put(key, icon);
    }

    /** True if the key is known. */
    public static boolean exists(String key) {
        return key != null && (CATEGORY_ICONS.containsKey(key) || APP_ICONS.containsKey(key));
    }

    /** The key itself if known, otherwise "other". */
    public static String normalize(String key) {
        return exists(key) ? key : FALLBACK;
    }

    /** Keys shown in the category icon picker. */
    public static List<String> categoryKeys() {
        return List.copyOf(CATEGORY_ICONS.keySet());
    }

    public static Ikon ikon(String key) {
        Ikon icon = CATEGORY_ICONS.get(key);
        if (icon == null) icon = APP_ICONS.get(key);
        return icon != null ? icon : CATEGORY_ICONS.get(FALLBACK);
    }

    /** A new vector icon; unknown keys fall back to "other". Color comes from CSS (-fx-icon-color). */
    public static FontIcon icon(String key, int size) {
        FontIcon icon = new FontIcon(ikon(key));
        icon.setIconSize(size);
        return icon;
    }

    /**
     * A white icon (for colored tiles). Done with a style class, not setStyle(): Ikonli keeps the icon font in the
     * node's inline style, and replacing that style would turn the icon into a placeholder glyph.
     */
    public static FontIcon whiteIcon(String key, int size) {
        FontIcon icon = icon(key, size);
        icon.getStyleClass().add("icon-white");
        return icon;
    }
}
