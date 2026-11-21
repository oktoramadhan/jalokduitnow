package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.exception.InvalidRecipientException;
import com.okto.jalokduitnow.model.Category;
import com.okto.jalokduitnow.model.CategoryType;
import com.okto.jalokduitnow.model.Recipient;
import com.okto.jalokduitnow.service.CategoryService;
import com.okto.jalokduitnow.service.DonorSettings;
import com.okto.jalokduitnow.service.RecipientService;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** The "Donor" part of Settings: the donor list (add, edit, delete, set default) and which income counts as donor money. */
public class DonorSettingsPanel extends VBox {
    private final RecipientService recipients;
    private final DonorSettings donorSettings;
    private final CategoryService categories;
    private final VBox recipientList = new VBox(10);
    private final VBox categoryList = new VBox(10);

    public DonorSettingsPanel(RecipientService recipients, DonorSettings donorSettings, CategoryService categories) {
        this.recipients = recipients;
        this.donorSettings = donorSettings;
        this.categories = categories;
        setSpacing(20);

        Label title = new Label("Donor");
        title.getStyleClass().add("title-2");
        Label hint = new Label("Penerima laporan, misalnya orang tua atau donatur. Nomor WhatsApp dipakai untuk membuka chat dengan pesan siap kirim.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);
        Button add = new Button("Tambah Donor", IconCatalog.icon("plus", 16));
        add.getStyleClass().add("primary");
        add.setOnAction(e -> RecipientDialog.show(recipients, null));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(12, title, spacer, add);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox donorCard = new VBox(14, header, hint, recipientList);
        donorCard.getStyleClass().add("card");
        donorCard.setMaxWidth(620);

        Label catTitle = new Label("Pemasukan dari donor");
        catTitle.getStyleClass().add("title-2");
        Label catHint = new Label("Pilih kategori pemasukan yang dihitung sebagai dana dari donor di laporan donor.");
        catHint.getStyleClass().add("muted");
        catHint.setWrapText(true);
        VBox catCard = new VBox(14, catTitle, catHint, categoryList);
        catCard.getStyleClass().add("card");
        catCard.setMaxWidth(620);

        getChildren().addAll(donorCard, catCard);
        recipients.addListener(this::renderRecipients);
        categories.addListener(this::renderCategories);
        renderRecipients();
        renderCategories();
    }

    private void renderRecipients() {
        recipientList.getChildren().clear();
        if (recipients.getAll().isEmpty()) {
            Label none = new Label("Belum ada donor. Tambah satu untuk mengirim laporan langsung ke WhatsApp.");
            none.getStyleClass().add("muted");
            none.setWrapText(true);
            recipientList.getChildren().add(none);
            return;
        }
        for (Recipient r : recipients.getAll()) recipientList.getChildren().add(row(r));
    }

    private javafx.scene.Node row(Recipient r) {
        Label name = new Label(r.getName());
        name.getStyleClass().add("name");
        name.setStyle("-fx-font-weight: bold;");
        HBox top = new HBox(8, name);
        if (r.isDefault()) {
            HBox chip = new HBox(6, IconCatalog.icon("star", 13), new Label("Utama"));
            chip.getStyleClass().addAll("chip", "reward");
            chip.setAlignment(Pos.CENTER);
            top.getChildren().add(chip);
        }
        top.setAlignment(Pos.CENTER_LEFT);
        Label phone = new Label(r.displayPhone() + (r.getNote().isEmpty() ? "" : "  -  " + r.getNote()));
        phone.getStyleClass().add("caption");
        phone.setWrapText(true);
        phone.setMaxWidth(320);

        Button star = iconButton("star", r.isDefault() ? "Penerima utama" : "Jadikan penerima utama", r.isDefault(), () -> run(() -> recipients.setDefault(r.getId())));
        Button edit = iconButton("edit", "Ubah", false, () -> RecipientDialog.show(recipients, r));
        Button delete = iconButton("delete", "Hapus", false, () -> confirmDelete(r));
        delete.getStyleClass().add("danger-text");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(14, Tiles.tile("donor", "#EC4899", 40), new VBox(2, top, phone), spacer, star, edit, delete);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("recipient-row");
        return row;
    }

    private void confirmDelete(Recipient r) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Hapus donor '" + r.getName() + "'? Laporan yang pernah dikirim tidak terpengaruh.");
        Theme.styleDialog(confirm.getDialogPane());
        confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> run(() -> recipients.delete(r.getId())));
    }

    private void renderCategories() {
        categoryList.getChildren().clear();
        var income = categories.active(CategoryType.INCOME);
        if (income.isEmpty()) {
            Label none = new Label("Belum ada kategori pemasukan.");
            none.getStyleClass().add("muted");
            categoryList.getChildren().add(none);
            return;
        }
        for (Category c : income) {
            CheckBox box = new CheckBox(c.getName());
            box.setSelected(donorSettings.isDonorCategory(c.getId()));
            box.selectedProperty().addListener((o, was, now) -> donorSettings.setDonorCategory(c.getId(), now));
            HBox row = new HBox(12, Tiles.tile(c, 32), box);
            row.setAlignment(Pos.CENTER_LEFT);
            categoryList.getChildren().add(row);
        }
    }

    @FunctionalInterface
    private interface RecipientAction { void run() throws InvalidRecipientException; }

    private void run(RecipientAction action) {
        try {
            action.run();
        } catch (InvalidRecipientException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, e.getMessage());
            Theme.styleDialog(alert.getDialogPane());
            alert.showAndWait();
        }
    }

    private static Button iconButton(String iconKey, String tip, boolean disabled, Runnable action) {
        Button b = new Button();
        b.setGraphic(IconCatalog.icon(iconKey, 18));
        b.getStyleClass().add("ghost");
        b.setTooltip(new Tooltip(tip));
        b.setDisable(disabled);
        b.setOnAction(e -> action.run());
        return b;
    }
}
