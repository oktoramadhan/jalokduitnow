package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.service.BackupService;
import com.okto.jalokduitnow.service.CategoryService;
import com.okto.jalokduitnow.event.GameEventBus;
import com.okto.jalokduitnow.model.XpPolicy;
import com.okto.jalokduitnow.service.DonorSettings;
import com.okto.jalokduitnow.service.FinanceService;
import com.okto.jalokduitnow.service.RecipientService;
import com.okto.jalokduitnow.share.ClipboardSharer;
import com.okto.jalokduitnow.share.DesktopUriOpener;
import com.okto.jalokduitnow.share.WhatsAppSharer;
import com.okto.jalokduitnow.storage.DonorSettingsDto;
import com.okto.jalokduitnow.storage.RecipientDto;
import com.okto.jalokduitnow.service.GameService;
import com.okto.jalokduitnow.storage.GameStateDto;
import com.okto.jalokduitnow.thread.DailyScheduler;
import com.okto.jalokduitnow.service.SchemaService;
import com.okto.jalokduitnow.storage.CategoryDto;
import com.okto.jalokduitnow.storage.ColumnDto;
import com.okto.jalokduitnow.storage.JsonRepository;
import com.okto.jalokduitnow.storage.TransactionDto;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;

/**
 * Entry point. Three panes: sidebar (navigation) | center (greeting, search, current page) | detail panel.
 * The detail panel hides itself when the window is narrow.
 */
public class MainApp extends Application {
    /** Below this scene width the right panel is hidden. */
    private static final double SHOW_DETAIL_FROM = 1180;
    private static final double PAGE_PADDING = 28;

    private FinanceService service;
    private SchemaService schemaService;
    private CategoryService categoryService;
    private GameService gameService;
    private RecipientService recipientService;
    private DonorSettings donorSettings;
    private DailyScheduler scheduler;

    private final Map<Page, Node> pages = new EnumMap<>(Page.class);
    private final StackPane pageHost = new StackPane();
    private Page current = Page.HOME;
    private Sidebar sidebar;
    private VBox detailHost;
    private Scene scene;

    @Override
    public void start(Stage stage) {
        Path dir = JsonRepository.defaultDataDir();
        Theme.load(dir);
        Clock clock = Clock.systemDefaultZone();   // one clock for the whole app (tests inject a fixed one)
        GameEventBus bus = new GameEventBus();
        service = new FinanceService(new JsonRepository<>(dir.resolve("transactions.json"), TransactionDto.class), clock);
        service.setEventBus(bus);
        schemaService = new SchemaService(new JsonRepository<>(dir.resolve("schema.json"), ColumnDto.class), service);
        categoryService = new CategoryService(new JsonRepository<>(dir.resolve("categories.json"), CategoryDto.class), service);
        recipientService = new RecipientService(new JsonRepository<>(dir.resolve("recipients.json"), RecipientDto.class));
        donorSettings = new DonorSettings(new JsonRepository<>(dir.resolve("donor-settings.json"), DonorSettingsDto.class));
        gameService = new GameService(new JsonRepository<>(dir.resolve("game.json"), GameStateDto.class),
                service, categoryService, schemaService, bus, new XpPolicy(), clock);
        // save failures happen on the auto-save thread: show them on the FX thread
        service.setErrorHandler(e -> Platform.runLater(() -> showError(e.getMessage())));
        schemaService.setErrorHandler(e -> Platform.runLater(() -> showError(e.getMessage())));
        categoryService.setErrorHandler(e -> Platform.runLater(() -> showError(e.getMessage())));
        gameService.setErrorHandler(e -> Platform.runLater(() -> showError(e.getMessage())));
        recipientService.setErrorHandler(e -> Platform.runLater(() -> showError(e.getMessage())));
        donorSettings.setErrorHandler(e -> Platform.runLater(() -> showError(e.getMessage())));
        try {
            schemaService.load();                          // schema first: it tells how to read custom values
            categoryService.load();                        // then categories: transactions point at their ids
            service.load(schemaService.getSchema(), categoryService);
            recipientService.load();
            donorSettings.load();
            gameService.load();
            gameService.rollDay();                         // catch up on streak, weekly quests and badges
        } catch (StorageException e) {
            showError(e.getMessage());
        }

        UiState state = new UiState(clock);
        TransactionActions actions = new TransactionActions(service, schemaService, categoryService, state, clock);

        pages.put(Page.HOME, scrolling(new DashboardView(service, categoryService, schemaService, state, actions,
                gameService, clock, () -> show(Page.TRANSACTIONS))));
        pages.put(Page.TRANSACTIONS, new TransactionView(service, schemaService, categoryService, state, actions));
        pages.put(Page.CATEGORIES, scrolling(cardAround(CategoryManagerDialog.createView(categoryService))));
        pages.put(Page.COLUMNS, scrolling(ColumnManagerDialog.createView(schemaService)));
        // Desktop.browse opens WhatsApp's link; if the platform cannot, the JavaFX host services are the fallback
        WhatsAppSharer whatsApp = new WhatsAppSharer(new DesktopUriOpener(
                uri -> Platform.runLater(() -> getHostServices().showDocument(uri.toString()))));
        pages.put(Page.REPORT, new ReportView(service, categoryService, schemaService, recipientService, donorSettings,
                state, bus, clock, whatsApp, ClipboardSharer.system()));
        pages.put(Page.QUESTS, scrolling(new QuestsView(gameService, categoryService)));
        BackupService backupService = new BackupService(service, schemaService, categoryService, gameService,
                recipientService, donorSettings, clock);
        pages.put(Page.SETTINGS, scrolling(new SettingsView(gameService, recipientService, donorSettings,
                categoryService, backupService, state, clock)));

        sidebar = new Sidebar(this::show, actions::add, gameService);
        DetailPanel detail = new DetailPanel(service, categoryService, schemaService, state, actions);
        detailHost = new VBox(detail);
        detailHost.setPadding(new Insets(PAGE_PADDING, PAGE_PADDING, PAGE_PADDING, 0));

        VBox center = new VBox(24, header(state, clock), pageHost);
        center.setPadding(new Insets(PAGE_PADDING));
        VBox.setVgrow(pageHost, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setLeft(sidebar);
        root.setCenter(center);
        root.setRight(detailHost);

        // toasts float above everything and let clicks through
        ToastHost toasts = new ToastHost();
        GameToasts.install(bus, toasts);
        StackPane layers = new StackPane(root, toasts);
        StackPane.setAlignment(toasts, Pos.BOTTOM_RIGHT);
        toasts.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        scene = new Scene(layers, 1360, 840);
        Theme.apply(scene);
        // responsive: hide the right panel on narrow windows
        scene.widthProperty().addListener((o, a, b) -> updateDetailVisibility());

        stage.setMinWidth(900);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.setTitle("JalokDuitNow");
        show(Page.HOME);
        stage.show();

        // at every midnight: roll the streak and start new weekly quests (the timer thread only hands over to the FX thread)
        scheduler = new DailyScheduler(clock, gameService::rollDay, Platform::runLater);
        scheduler.start();
    }

    // ---- navigation ----

    private void show(Page page) {
        current = page;
        pageHost.getChildren().setAll(pages.get(page));
        sidebar.select(page);
        updateDetailVisibility();
    }

    private void updateDetailVisibility() {
        boolean relevant = current == Page.HOME || current == Page.TRANSACTIONS;
        boolean visible = relevant && scene.getWidth() >= SHOW_DETAIL_FROM;
        detailHost.setVisible(visible);
        detailHost.setManaged(visible);
    }

    // ---- header ----

    private Node header(UiState state, Clock clock) {
        Label greeting = new Label(greetingText(clock));
        greeting.getStyleClass().add("title-1");
        Label today = new Label(LocalDate.now(clock).format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", Formats.ID)));
        today.getStyleClass().add("muted");

        TextField search = new TextField();
        search.setPromptText("Cari transaksi...");
        search.setPrefWidth(280);
        search.textProperty().bindBidirectional(state.search);
        search.textProperty().addListener((o, a, text) -> {
            if (!text.isBlank() && current != Page.HOME && current != Page.TRANSACTIONS) show(Page.TRANSACTIONS);
        });
        HBox searchBox = new HBox(8, IconCatalog.icon("search", 18), search);
        searchBox.getStyleClass().add("search-box");
        searchBox.setAlignment(Pos.CENTER_LEFT);

        ToggleButton themeToggle = new ToggleButton();
        themeToggle.getStyleClass().add("theme-toggle");
        themeToggle.selectedProperty().bindBidirectional(Theme.darkProperty());
        themeToggle.graphicProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(
                () -> IconCatalog.icon(themeToggle.isSelected() ? "sun" : "moon", 18), themeToggle.selectedProperty()));
        Motion.grow(themeToggle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(16, new VBox(4, greeting, today), spacer, searchBox, themeToggle);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private static String greetingText(Clock clock) {
        int hour = LocalTime.now(clock).getHour();
        if (hour < 11) return "Selamat pagi";
        if (hour < 15) return "Selamat siang";
        if (hour < 18) return "Selamat sore";
        return "Selamat malam";
    }

    // ---- helpers ----

    private static ScrollPane scrolling(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    private static Node cardAround(Node content) {
        VBox card = new VBox(content);
        card.getStyleClass().add("card");
        return card;
    }

    @Override
    public void stop() {
        // write anything still waiting in the auto-save queue
        if (service != null) service.flush();
        if (schemaService != null) schemaService.flush();
        if (scheduler != null) scheduler.stop();
        if (gameService != null) gameService.flush();
        if (recipientService != null) recipientService.flush();
        if (donorSettings != null) donorSettings.flush();
        if (categoryService != null) categoryService.flush();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg);
        Theme.styleDialog(alert.getDialogPane());
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
