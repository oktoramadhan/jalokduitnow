package com.okto.jalokduitnow.ui;

import com.okto.jalokduitnow.exception.InvalidRecipientException;
import com.okto.jalokduitnow.model.Recipient;
import com.okto.jalokduitnow.service.RecipientService;
import com.okto.jalokduitnow.util.PhoneNumberNormalizer;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * Add or edit a donor. Validation and phone normalization are done by Recipient / RecipientService; this dialog
 * shows how the number will be stored while typing and puts the service's error messages inline.
 */
public final class RecipientDialog {
    private RecipientDialog() {}

    /** @param existing the recipient to edit, or null to add one */
    public static void show(RecipientService service, Recipient existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Tambah Donor" : "Ubah Donor");
        ButtonType save = new ButtonType("Simpan", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, new ButtonType("Batal", ButtonBar.ButtonData.CANCEL_CLOSE));
        Theme.styleDialog(dialog.getDialogPane());

        TextField name = new TextField(existing == null ? "" : existing.getName());
        name.setPromptText("contoh: Pak Budi");
        TextField phone = new TextField(existing == null ? "" : existing.displayPhone());
        phone.setPromptText("contoh: 0812-3456-7890");
        TextField note = new TextField(existing == null ? "" : existing.getNote());
        note.setPromptText("opsional");
        CheckBox makeDefault = new CheckBox("Jadikan penerima utama");
        makeDefault.setSelected(existing != null && existing.isDefault());
        for (TextField f : new TextField[]{name, phone, note}) f.setPrefWidth(300);

        Label preview = new Label();
        preview.getStyleClass().add("caption");
        Runnable showNormalized = () -> {
            String raw = phone.getText();
            if (raw == null || raw.isBlank()) {
                preview.setText("Nomor ditulis dengan awalan 0 atau +62.");
                return;
            }
            try {
                preview.setText("Akan disimpan sebagai +" + PhoneNumberNormalizer.normalize(raw));
            } catch (InvalidRecipientException e) {
                preview.setText(e.getMessage());
            }
        };
        phone.textProperty().addListener((o, a, b) -> showNormalized.run());
        showNormalized.run();

        Label error = new Label();
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setMaxWidth(420);

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(14);
        grid.addRow(0, new Label("Nama"), name);
        grid.addRow(1, new Label("Nomor WhatsApp"), phone);
        grid.add(preview, 1, 2);
        grid.addRow(3, new Label("Catatan"), note);
        grid.add(makeDefault, 1, 4);
        VBox content = new VBox(14, grid, error);
        content.setPadding(new Insets(16));
        dialog.getDialogPane().setContent(content);

        Button ok = (Button) dialog.getDialogPane().lookupButton(save);
        ok.getStyleClass().add("primary");
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                if (existing == null) {
                    service.add(name.getText(), phone.getText(), note.getText(), makeDefault.isSelected());
                } else {
                    service.update(existing.getId(), name.getText(), phone.getText(), note.getText());
                    if (makeDefault.isSelected() && !existing.isDefault()) service.setDefault(existing.getId());
                }
            } catch (InvalidRecipientException ex) {
                error.setText(ex.getMessage());
                ev.consume();
            }
        });
        dialog.showAndWait();
    }
}
