package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.achievement.Achievement;
import com.okto.jalokduitnow.model.GameProfile;
import com.okto.jalokduitnow.model.SavingsGoal;
import com.okto.jalokduitnow.quest.*;
import com.okto.jalokduitnow.service.CategoryService;
import com.okto.jalokduitnow.service.GameService;
import com.okto.jalokduitnow.util.Rupiah;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Quests &amp; Badges page: streak, this week's quests, badge grid and savings goals. */
public class QuestsView extends VBox {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM", Formats.ID);
    private static final DateTimeFormatter FULL_DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Formats.ID);
    private static final Map<String, String> BADGE_COLORS = Map.of(
            "flame", "#F97316", "shield", "#3B82F6", "star", "#F59E0B",
            "trophy", "#EAB308", "columns", "#8B5CF6", "categories", "#EC4899");

    private final GameService game;
    private final CategoryService categories;

    public QuestsView(GameService game, CategoryService categories) {
        this.game = game;
        this.categories = categories;
        setSpacing(20);
        game.addListener(this::render);
        render();
    }

    private void render() {
        getChildren().setAll(streakCard(), section("Quest Minggu Ini", questPeriodText(), questGrid()),
                section("Badge", game.unlockedCount() + " dari " + game.achievements().size() + " terbuka", badgeGrid()),
                goalsSection());
    }

    // ---- streak ----

    private Node streakCard() {
        GameProfile p = game.profile();
        Label number = new Label(String.valueOf(p.getStreak()));
        number.getStyleClass().add("streak-number");
        Label unit = new Label("hari berturut-turut");
        unit.getStyleClass().add("muted");
        VBox count = new VBox(0, number, unit);

        Label record = stat("Rekor", p.getLongestStreak() + " hari", "trophy");
        Label tokens = stat("Freeze token", p.getFreezeTokens() + " / " + GameProfile.MAX_FREEZE_TOKENS, "shield");
        Label hint = new Label("Satu freeze token baru setiap minggu menyelamatkan satu hari yang terlewat.");
        hint.getStyleClass().add("caption");
        hint.setWrapText(true);
        hint.setMaxWidth(320);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(24, Tiles.tile("flame", p.getStreak() > 0 ? "#F97316" : "#9CA3AF", 64), count, spacer,
                new VBox(10, record, tokens), new VBox(hint));
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("card");
        return row;
    }

    private Label stat(String label, String value, String iconKey) {
        Label l = new Label(label + ": " + value, IconCatalog.icon(iconKey, 16));
        l.setGraphicTextGap(8);
        l.setStyle("-fx-font-weight: bold;");
        return l;
    }

    // ---- quests ----

    private String questPeriodText() {
        var quests = game.quests();
        if (quests.isEmpty()) return "";
        QuestPeriod p = quests.get(0).getPeriod();
        return p.start().format(DAY) + " - " + p.end().format(DAY);
    }

    private Node questGrid() {
        FlowPane grid = new FlowPane(16, 16);
        if (game.quests().isEmpty()) {
            Label none = new Label("Belum ada quest. Quest baru dibuat setiap Senin.");
            none.getStyleClass().add("muted");
            return none;
        }
        for (Quest q : game.quests()) grid.getChildren().add(questCard(q));
        return grid;
    }

    private Node questCard(Quest quest) {
        QuestProgress progress = game.progressOf(quest);
        boolean rewarded = game.isRewarded(quest);

        Label title = new Label(quest.getTitle());
        title.setWrapText(true);
        title.setMaxWidth(260);
        title.setStyle("-fx-font-weight: bold;");

        ProgressBar bar = new ProgressBar(progress.fraction());
        bar.setMaxWidth(Double.MAX_VALUE);
        if (progress.isCompleted()) bar.getStyleClass().add("done");
        Label numbers = new Label(describe(quest, progress));
        numbers.getStyleClass().add("caption");

        HBox badge;
        if (rewarded || progress.isCompleted()) badge = chip("done", "check", "+" + quest.getRewardXp() + " XP");
        else if (progress.status() == QuestStatus.FAILED) badge = chip("failed", null, "Gagal");
        else badge = chip("reward", "bolt", "+" + quest.getRewardXp() + " XP");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox foot = new HBox(numbers, spacer, badge);
        foot.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(12, title, bar, foot);
        card.getStyleClass().addAll("card", "compact");
        card.setPrefWidth(300);
        Motion.lift(card);
        return card;
    }

    private static HBox chip(String kind, String iconKey, String text) {
        HBox chip = new HBox(6);
        if (iconKey != null) chip.getChildren().add(IconCatalog.icon(iconKey, 13));
        chip.getChildren().add(new Label(text));
        chip.setAlignment(Pos.CENTER);
        chip.getStyleClass().addAll("chip", kind);
        chip.setMaxWidth(Region.USE_PREF_SIZE);
        return chip;
    }

    /** "x / y" in the unit that fits the quest type. */
    private static String describe(Quest q, QuestProgress p) {
        return switch (q) {
            case CategoryBudgetQuest b -> Rupiah.format(p.current()) + " / " + Rupiah.format(p.target());
            case SavingsQuest s -> Rupiah.format(p.current()) + " / " + Rupiah.format(p.target());
            case ChoiceShareQuest c -> p.current() + "% / maks " + p.target() + "%";
            default -> p.current() + " / " + p.target() + " hari";
        };
    }

    // ---- badges ----

    private Node badgeGrid() {
        FlowPane grid = new FlowPane(16, 16);
        for (Achievement a : game.achievements()) grid.getChildren().add(badgeCard(a));
        return grid;
    }

    private Node badgeCard(Achievement a) {
        var unlocked = game.unlockedOn(a);
        Label name = new Label(a.getName());
        name.setStyle("-fx-font-weight: bold;");
        name.setWrapText(true);
        name.setMaxWidth(150);
        Label detail = new Label(unlocked.map(d -> "Dibuka " + d.format(FULL_DAY)).orElse(a.getDescription()));
        detail.getStyleClass().add("caption");
        detail.setWrapText(true);
        detail.setMaxWidth(150);

        Node tile = unlocked.isPresent()
                ? Tiles.tile(a.getIconKey(), BADGE_COLORS.getOrDefault(a.getIconKey(), "#3B82F6"), 52)
                : Tiles.lockedTile(a.getIconKey(), 52);
        VBox card = new VBox(10, tile, name, detail);
        card.setAlignment(Pos.TOP_LEFT);
        card.getStyleClass().addAll("card", "compact", "badge-card");
        if (unlocked.isEmpty()) {
            card.getStyleClass().add("locked");
            Tooltip.install(card, new Tooltip("Terkunci: " + a.getDescription()));
        }
        card.setPrefWidth(190);
        card.setMinHeight(150);
        Motion.lift(card);
        return card;
    }

    // ---- savings goals ----

    private Node goalsSection() {
        Button add = new Button("Tambah Target", IconCatalog.icon("plus", 16));
        add.getStyleClass().add("primary");
        add.setOnAction(e -> SavingsGoalDialog.show(game, categories, null));

        FlowPane grid = new FlowPane(16, 16);
        if (game.goals().isEmpty()) {
            Label none = new Label("Belum ada target tabungan. Buat satu untuk memantau kemajuanmu.");
            none.getStyleClass().add("muted");
            grid.getChildren().add(none);
        }
        for (SavingsGoal g : game.goals()) grid.getChildren().add(goalCard(g));

        VBox section = section("Target Tabungan", "", grid);
        ((HBox) section.getChildren().get(0)).getChildren().add(add);
        return section;
    }

    private Node goalCard(SavingsGoal goal) {
        SavingsGoal.Progress p = game.goalProgress(goal);
        Label name = new Label(goal.name());
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        Label percent = new Label(p.percent() + "%");
        percent.getStyleClass().addAll("title-2", p.percent() >= 100 ? "income-text" : "accent-text");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(12, categories.find(goal.categoryId()).map(c -> (Node) Tiles.tile(c, 40))
                .orElse(Tiles.tile("savings", "#9CA3AF", 40)), new VBox(2, name, new Label(categories.nameOf(goal.categoryId()))), spacer, percent);
        top.setAlignment(Pos.CENTER_LEFT);
        ((Label) ((VBox) top.getChildren().get(1)).getChildren().get(1)).getStyleClass().add("muted");

        ProgressBar bar = new ProgressBar(p.percent() / 100.0);
        bar.setMaxWidth(Double.MAX_VALUE);
        if (p.percent() >= 100) bar.getStyleClass().add("done");
        Label amounts = new Label(Rupiah.format(p.saved()) + " / " + Rupiah.format(p.target()));
        amounts.getStyleClass().add("caption");

        Button edit = new Button("Ubah", IconCatalog.icon("edit", 14));
        edit.getStyleClass().add("ghost");
        edit.setOnAction(e -> SavingsGoalDialog.show(game, categories, goal));
        Button delete = new Button("Hapus", IconCatalog.icon("delete", 14));
        delete.getStyleClass().addAll("ghost", "danger-text");
        delete.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Hapus target '" + goal.name() + "'? Transaksimu tidak ikut terhapus.");
            Theme.styleDialog(confirm.getDialogPane());
            confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> game.deleteGoal(goal.id()));
        });
        Region foot = new Region();
        HBox.setHgrow(foot, Priority.ALWAYS);
        HBox bottom = new HBox(amounts, foot, edit, delete);
        bottom.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(14, top, bar, bottom);
        card.getStyleClass().addAll("card", "compact");
        card.setPrefWidth(340);
        Motion.lift(card);
        return card;
    }

    // ---- layout helper ----

    private static VBox section(String title, String subtitle, Node content) {
        Label t = new Label(title);
        t.getStyleClass().add("title-2");
        Label s = new Label(subtitle);
        s.getStyleClass().add("muted");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(12, t, s, spacer);
        header.setAlignment(Pos.CENTER_LEFT);
        return new VBox(14, header, content);
    }
}
