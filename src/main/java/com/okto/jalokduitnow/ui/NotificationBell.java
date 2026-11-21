package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.reminder.AppNotification;
import com.okto.jalokduitnow.reminder.NotificationAction;
import com.okto.jalokduitnow.service.NotificationCenter;
import com.okto.jalokduitnow.util.TimeAgo;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.stage.Popup;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * The bell in the header: an unread count and, when clicked, the notification center. It lists recent notifications
 * (unread ones highlighted), lets the user mark everything as read or clear the list, and shows each notification's
 * action button (for example "Tambah Transaksi"). It only displays NotificationCenter; all changes go through it.
 */
public class NotificationBell extends StackPane {
    private final NotificationCenter center;
    private final Clock clock;
    private final Consumer<NotificationAction> onAction;
    private final Label badge = new Label();
    private final Popup popup = new Popup();
    private final VBox list = new VBox(10);
    private final Label unreadLabel = new Label();

    public NotificationBell(NotificationCenter center, Clock clock, Consumer<NotificationAction> onAction) {
        this.center = center;
        this.clock = clock;
        this.onAction = onAction;

        Button bell = new Button();
        bell.setGraphic(IconCatalog.icon("bell", 18));
        bell.getStyleClass().add("icon-button");
        bell.setOnAction(e -> toggle(bell));
        Motion.grow(bell);

        badge.getStyleClass().add("badge-count");
        badge.setMouseTransparent(true);
        StackPane.setAlignment(badge, Pos.TOP_RIGHT);
        getChildren().addAll(bell, badge);

        buildPopup();
        center.addListener(this::refresh);
        refresh();
    }

    // ---- the bell itself ----

    private void refresh() {
        int unread = center.unreadCount();
        badge.setText(unread > 9 ? "9+" : String.valueOf(unread));
        badge.setVisible(unread > 0);
        unreadLabel.setText(unread == 0 ? "Semua sudah dibaca" : unread + " belum dibaca");
        renderList();
    }

    private void toggle(Button bell) {
        if (popup.isShowing()) {
            popup.hide();
            return;
        }
        renderList();                                   // refresh the "5 menit lalu" texts
        Bounds b = bell.localToScreen(bell.getBoundsInLocal());
        popup.show(bell, b.getMaxX() - 392, b.getMaxY() + 10);
    }

    // ---- the popup ----

    private void buildPopup() {
        Label title = new Label("Notifikasi");
        title.getStyleClass().add("title-2");
        unreadLabel.getStyleClass().add("muted");
        Button markAll = new Button("Tandai semua dibaca");
        markAll.getStyleClass().add("ghost");
        markAll.setOnAction(e -> center.markAllRead());
        Button clear = new Button("Hapus semua");
        clear.getStyleClass().addAll("ghost", "danger-text");
        clear.setOnAction(e -> center.clear());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(4, markAll, spacer, clear);

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(340);
        scroll.setMaxHeight(380);

        VBox card = new VBox(12, new HBox(10, title, unreadLabel), actions, scroll);
        ((HBox) card.getChildren().get(0)).setAlignment(Pos.BASELINE_LEFT);
        card.getStyleClass().add("card");
        card.setPrefWidth(400);
        card.setMaxWidth(400);

        popup.getContent().add(card);
        popup.setAutoHide(true);
        popup.setOnShown(e -> Theme.apply(popup.getScene()));   // the popup has its own scene: give it the theme too
    }

    private void renderList() {
        list.getChildren().clear();
        if (center.getAll().isEmpty()) {
            Label none = new Label("Belum ada notifikasi.");
            none.getStyleClass().add("muted");
            list.getChildren().add(none);
            return;
        }
        for (AppNotification n : center.getAll()) list.getChildren().add(row(n));
    }

    private HBox row(AppNotification n) {
        Label title = new Label(n.getTitle());
        title.setStyle("-fx-font-weight: bold;");
        Label message = new Label(n.getMessage());
        message.setWrapText(true);
        message.setMaxWidth(270);
        Label time = new Label(TimeAgo.format(n.getCreatedAt(), Instant.now(clock), clock.getZone()));
        time.getStyleClass().add("caption");

        HBox titleRow = new HBox(8, title);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        if (!n.isRead()) {
            Region dot = new Region();
            dot.getStyleClass().add("unread-dot");
            titleRow.getChildren().add(dot);
        }
        VBox text = new VBox(4, titleRow, message, time);
        if (n.getAction().isAvailable(n.getCreatedAt(), clock.instant())) {
            Button action = new Button(n.getAction().label(), IconCatalog.icon(n.getAction().iconKey(), 14));
            action.getStyleClass().add("primary");
            action.setOnAction(e -> {
                center.markRead(n.getId());
                popup.hide();
                onAction.accept(n.getAction());
            });
            text.getChildren().add(action);
        }

        HBox row = new HBox(12, Tiles.tile(iconFor(n), n.getReminderId() == null ? "#9CA3AF" : colorFor(n), 36), text);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("notification-row");
        if (!n.isRead()) row.getStyleClass().add("unread");
        row.setOnMouseClicked(e -> center.markRead(n.getId()));     // opening a notification marks it read
        return row;
    }

    /** Which vector icon represents a notification. */
    static String iconFor(AppNotification n) {
        String id = n.getReminderId();
        if (id == null) return "info";
        if (id.startsWith("streak")) return "flame";
        if (id.startsWith("donor")) return "donor";
        if (id.startsWith("budget")) return "wallet";
        return "bell";
    }

    static String colorFor(AppNotification n) {
        String id = n.getReminderId();
        if (id == null) return "#9CA3AF";
        if (id.startsWith("streak")) return "#F97316";
        if (id.startsWith("donor")) return "#EC4899";
        if (id.startsWith("budget")) return "#EF4444";
        return "#3B82F6";
    }
}
