package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.exception.StorageException;
import com.okto.jalokduitnow.service.BackupService;
import com.okto.jalokduitnow.service.CategoryService;
import com.okto.jalokduitnow.event.GameEventBus;
import com.okto.jalokduitnow.model.XpPolicy;
import com.okto.jalokduitnow.service.DonorSettings;
import com.okto.jalokduitnow.service.FinanceService;
import com.okto.jalokduitnow.reminder.AppNotification;
import com.okto.jalokduitnow.service.AutoBackupService;
import com.okto.jalokduitnow.service.DataReloader;
import com.okto.jalokduitnow.service.PeriodRange;
import com.okto.jalokduitnow.service.RestoreService;
import com.okto.jalokduitnow.service.DeleteAction;
import com.okto.jalokduitnow.service.DeleteDataService;
import com.okto.jalokduitnow.service.DeletePostActions;
import com.okto.jalokduitnow.storage.FileSwapper;
import com.okto.jalokduitnow.reminder.NotificationAction;
import com.okto.jalokduitnow.service.NotificationCenter;
import com.okto.jalokduitnow.service.RecipientService;
import com.okto.jalokduitnow.service.ReminderContextProvider;
import com.okto.jalokduitnow.service.ReminderLog;
import com.okto.jalokduitnow.service.ReminderSettingsService;
import com.okto.jalokduitnow.storage.NotificationDto;
import com.okto.jalokduitnow.storage.ReminderLogDto;
import com.okto.jalokduitnow.storage.ReminderSettingsDto;
import com.okto.jalokduitnow.thread.ReminderService;
import com.okto.jalokduitnow.util.SystemClock;
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
import java.time.Instant;
import java.util.TimeZone;
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
    private ReminderSettingsService reminderSettings;
    private ReminderLog reminderLog;
    private NotificationCenter notificationCenter;
    private ReminderService reminderService;
    private SystemTrayNotifier trayNotifier;
    private AutoBackupService autoBackups;
    private BackupService backupService;
    private ToastHost toastHost;
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
        Clock clock = new SystemClock();           // one clock for the whole app; it re-reads the time zone each time (tests inject a fixed one)
        GameEventBus bus = new GameEventBus();
        service = new FinanceService(new JsonRepository<>(dir.resolve("transactions.json"), TransactionDto.class), clock);
        service.setEventBus(bus);
        schemaService = new SchemaService(new JsonRepository<>(dir.resolve("schema.json"), ColumnDto.class), service);
        categoryService = new CategoryService(new JsonRepository<>(dir.resolve("categories.json"), CategoryDto.class), service);
        recipientService = new RecipientService(new JsonRepository<>(dir.resolve("recipients.json"), RecipientDto.class));
        donorSettings = new DonorSettings(new JsonRepository<>(dir.resolve("donor-settings.json"), DonorSettingsDto.class));
        reminderSettings = new ReminderSettingsService(new JsonRepository<>(dir.resolve("reminders.json"), ReminderSettingsDto.class));
        reminderLog = new ReminderLog(new JsonRepository<>(dir.resolve("reminder-log.json"), ReminderLogDto.class));
        notificationCenter = new NotificationCenter(new JsonRepository<>(dir.resolve("notifications.json"), NotificationDto.class));
        autoBackups = new AutoBackupService(dir.resolve("backups"), clock);
        gameService = new GameService(new JsonRepository<>(dir.resolve("game.json"), GameStateDto.class),
                service, categoryService, schemaService, bus, new XpPolicy(), clock);
        // save failures happen on the auto-save thread: show them on the FX thread
        service.setErrorHandler(e -> Platform.runLater(() -> showError(e.getMessage())));
        schemaService.setErrorHandler(e -> Platform.runLater(() -> showError(e.getMessage())));
        categoryService.setErrorHandler(e -> Platform.runLater(() -> showError(e.getMessage())));
        gameService.setErrorHandler(e -> Platform.runLater(() -> showError(e.getMessage())));
        recipientService.setErrorHandler(e -> Platform.runLater(() -> showError(e.getMessage())));
        donorSettings.setErrorHandler(e -> Platform.runLater(() -> showError(e.getMessage())));
        reminderSettings.setErrorHandler(e -> Platform.runLater(() -> showError(e.getMessage())));
        reminderLog.setErrorHandler(e -> Platform.runLater(() -> showError(e.getMessage())));
        notificationCenter.setErrorHandler(e -> Platform.runLater(() -> showError(e.getMessage())));
        try {
            schemaService.load();                          // schema first: it tells how to read custom values
            categoryService.load();                        // then categories: transactions point at their ids
            service.load(schemaService.getSchema(), categoryService);
            recipientService.load();
            donorSettings.load();
            reminderSettings.load();
            reminderLog.load();
            notificationCenter.load();
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
        // reminders: the ticker thread only keeps time; each check runs on the FX thread (Platform::runLater)
        reminderService = new ReminderService(reminderSettings, reminderLog, notificationCenter,
                new ReminderContextProvider(service, gameService, categoryService, recipientService, donorSettings, clock),
                bus, clock, Platform::runLater, ReminderService.DEFAULT_INTERVAL,
                () -> TimeZone.setDefault(null));   // forget the cached zone so a changed system time zone is noticed
        boolean traySupported = new AwtTrayBackend().isSupported();
        backupService = new BackupService(service, schemaService, categoryService, gameService,
                recipientService, donorSettings, reminderSettings, clock);   // reminder settings are part of a backup too
        // restore: validate, make a safety backup, swap the files atomically, then reload every service on the FX thread
        DataReloader dataReloader = new DataReloader(service, schemaService, categoryService, gameService, recipientService,
                donorSettings, reminderSettings);
        RestoreService restoreService = new RestoreService(dir, autoBackups, new FileSwapper(), Platform::runLater, () -> {
            dataReloader.reloadAll();
            state.selected.set(null);                                      // objects from before the restore no longer exist
            state.period.set(PeriodRange.defaultMonth(service.summaryByMonth().keySet(), clock));
        });
        Runnable flushAll = () -> {
            service.flush();
            schemaService.flush();
            categoryService.flush();
            gameService.flush();
            recipientService.flush();
            donorSettings.flush();
            reminderSettings.flush();
        };
        // delete data: safety backup, atomic swap, reload every service; rewards are suspended and obsolete reminders removed
        DeleteDataService deleteService;
        try {
            deleteService = new DeleteDataService(dir, autoBackups, new FileSwapper(), Platform::runLater, () -> {
                dataReloader.reloadAll();
                state.selected.set(null);
                state.period.set(PeriodRange.defaultMonth(service.summaryByMonth().keySet(), clock));
            }, new DeletePostActions(notificationCenter, reminderLog, gameService, action -> {
                if (action == DeleteAction.FACTORY) Theme.resetToDefault();
            }), clock);
        } catch (StorageException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        Runnable openRestoreOfNewest = () -> openRestore(restoreService, flushAll, clock, autoBackups.list().stream()
                .filter(f -> f.kind() == AutoBackupService.Kind.SAFETY).max(java.util.Comparator.comparing(AutoBackupService.BackupFile::modified))
                .map(AutoBackupService.BackupFile::path).orElse(null));
        javafx.scene.Node dangerZone = new DangerZonePanel(action -> DeleteDataDialog.show(action, deleteService, backupService, flushAll,
                () -> notificationCenter.getAll().size(), result -> announceDeletion(result, clock),
                result -> openRestore(restoreService, flushAll, clock, result.safetyBackup())));
        this.restoreOpener = openRestoreOfNewest;
        javafx.scene.Node restoreCard = new RestorePanel(restoreService, backupService, autoBackups, flushAll, clock,
                result -> announceRestore(result, clock));
        pages.put(Page.SETTINGS, scrolling(new SettingsView(gameService, recipientService, donorSettings,
                categoryService, backupService, state, clock, reminderSettings, reminderService, traySupported,
                restoreCard, dangerZone)));

        sidebar = new Sidebar(this::show, actions::add, gameService);
        DetailPanel detail = new DetailPanel(service, categoryService, schemaService, state, actions);
        detailHost = new VBox(detail);
        detailHost.setPadding(new Insets(PAGE_PADDING, PAGE_PADDING, PAGE_PADDING, 0));

        VBox center = new VBox(24, header(state, clock, notificationCenter, a -> handleNotificationAction(a, actions)), pageHost);
        center.setPadding(new Insets(PAGE_PADDING));
        VBox.setVgrow(pageHost, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setLeft(sidebar);
        root.setCenter(center);
        root.setRight(detailHost);

        // toasts float above everything and let clicks through
        ToastHost toasts = new ToastHost();
        toastHost = toasts;
        GameToasts.install(bus, toasts);
        reminderService.addSink(n -> Platform.runLater(() -> toasts.show(n.getTitle(), n.getMessage(),
                NotificationBell.iconFor(n), NotificationBell.colorFor(n))));
        trayNotifier = new SystemTrayNotifier(new AwtTrayBackend(), () -> reminderSettings.current().isTrayEnabled());
        reminderService.addSink(trayNotifier);
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
        reminderService.start();                 // checks right away, so reminders missed earlier today fire now
        makeDailyBackup(clock);
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

    private Node header(UiState state, Clock clock, NotificationCenter notifications,
                        java.util.function.Consumer<NotificationAction> onAction) {
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
        HBox header = new HBox(16, new VBox(4, greeting, today), spacer, searchBox,
                new NotificationBell(notifications, clock, onAction), themeToggle);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private Runnable restoreOpener;

    private void openRestore(RestoreService restoreService, Runnable flushAll, Clock clock, java.nio.file.Path preselected) {
        RestoreDialog.show(restoreService, backupService, autoBackups, flushAll, clock, r -> announceRestore(r, clock), preselected);
    }

    /** A toast and a notification (with a time-limited "Pulihkan" button) after a successful deletion. */
    private void announceDeletion(DeleteDataService.DeleteResult result, Clock clock) {
        String message = result.action().title() + " selesai. Cadangan pengaman: " + result.safetyBackup().getFileName();
        notificationCenter.add(AppNotification.create("Data dihapus", message, Instant.now(clock), NotificationAction.OPEN_RESTORE, null));
        if (toastHost != null) toastHost.show(result.action().title() + " selesai", "Cadangan pengaman: " + result.safetyBackup().getFileName(), "delete", "#DC2626");
    }

    /** A toast and an entry in the notification center after a successful restore. */
    private void announceRestore(RestoreService.RestoreResult result, Clock clock) {
        String message = "Data dipulihkan dari cadangan. Salinan pengaman data sebelumnya: " + result.safetyBackup().getFileName();
        notificationCenter.add(AppNotification.create("Data dipulihkan", message, Instant.now(clock), NotificationAction.NONE, null));
        if (toastHost != null) toastHost.show("Data dipulihkan", "Semua tampilan sudah diperbarui", "restore", "#16A34A");
    }

    /** One automatic backup per day, written in the background from a snapshot taken here on the FX thread. */
    private void makeDailyBackup(Clock clock) {
        try {
            String json = backupService.backupJson(true);
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    autoBackups.writeDailyIfMissing(json);
                } catch (StorageException e) {
                    System.err.println("Cadangan otomatis gagal: " + e.getMessage());   // never bother the user: it is a convenience
                }
            });
        } catch (StorageException e) {
            System.err.println("Cadangan otomatis gagal: " + e.getMessage());
        }
    }

    /** What the button on a notification does. */
    private void handleNotificationAction(NotificationAction action, TransactionActions actions) {
        switch (action) {
            case ADD_TRANSACTION -> actions.add();
            case OPEN_REPORT -> show(Page.REPORT);
            case OPEN_CATEGORIES -> show(Page.CATEGORIES);
            case OPEN_RESTORE -> { if (restoreOpener != null) restoreOpener.run(); }
            case NONE -> { }
        }
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
        if (reminderService != null) reminderService.stop();     // the ticker thread ends before the app does
        if (trayNotifier != null) trayNotifier.close();           // a tray icon would keep the process alive
        if (reminderSettings != null) reminderSettings.flush();
        if (reminderLog != null) reminderLog.flush();
        if (notificationCenter != null) notificationCenter.flush();
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
