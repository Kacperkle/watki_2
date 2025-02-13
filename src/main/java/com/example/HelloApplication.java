package com.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class HelloApplication extends Application {

    private TextField titleField = new TextField();
    private TextField authorField = new TextField();
    private TextArea jsonInput = new TextArea();
    private ProgressBar progressBar = new ProgressBar(0);
    private Button startButton = new Button("Pobierz");
    private Button stopButton = new Button("Anuluj");
    private ImageView imageView = new ImageView();

    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Task<Void> downloadTask;

    @Override
    public void start(Stage primaryStage) {
        imageView.setFitWidth(300);
        imageView.setPreserveRatio(true);

        primaryStage.setTitle("pobieracz");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setVgap(10);
        grid.setHgap(10);

        grid.add(startButton, 1, 0);
        stopButton.setDisable(true);
        grid.add(stopButton, 1, 1);

        progressBar.setPrefWidth(200);
        grid.add(progressBar, 1, 2);

        grid.add(new Label("tytuł:"), 0, 3);
        grid.add(titleField, 1, 3);
        titleField.setEditable(false);

        grid.add(new Label("autor:"), 0, 4);
        grid.add(authorField, 1, 4);
        authorField.setEditable(false);

        grid.add(new Label("JSON:"), 0, 5);
        jsonInput.setPrefRowCount(10);
        grid.add(jsonInput, 1, 5);

        grid.add(imageView, 1, 6);

        startButton.setOnAction(e -> startProcessing());
        stopButton.setOnAction(e -> stopProcessing());

        primaryStage.setScene(new Scene(grid, 550, 500));
        primaryStage.show();
    }

    private void startProcessing() {
        String jsonText = jsonInput.getText();
        if (jsonText.isEmpty()) {
            showAlert("stop", "pole na JSON musi być pełne.");
            return;
        }

        startButton.setDisable(true);
        stopButton.setDisable(false);

        downloadTask = new Task<>() {
            @Override
            protected Void call() {
                try {
                    String title = extractValue(jsonText, "\"title\"\\s*:\\s*\"(.*?)\"");
                    String author = extractValue(jsonText, "\"copyright\"\\s*:\\s*\"(.*?)\"");
                    String imageUrl = extractValue(jsonText, "\"url\"\\s*:\\s*\"(.*?)\"");

                    for (int i = 0; i <= 100; i += 10) {
                        if (isCancelled()) return null;
                        Thread.sleep(100);
                        updateProgress(i, 100);
                    }

                    if (!isCancelled()) {
                        Platform.runLater(() -> {
                            titleField.setText(title.isEmpty() ? "Unknown" : title);
                            authorField.setText(author.isEmpty() ? "Unknown" : author);
                        });
                        downloadImage(imageUrl);
                    }
                } catch (Exception ex) {
                    showAlert("stop", "zatrzymano pobieranie");
                } finally {
                    Platform.runLater(() -> {
                        startButton.setDisable(false);
                        stopButton.setDisable(true);
                    });
                }
                return null;
            }
        };

        progressBar.progressProperty().bind(downloadTask.progressProperty());
        executorService.submit(downloadTask);
    }

    private void stopProcessing() {
        if (downloadTask != null && downloadTask.isRunning()) {
            downloadTask.cancel();
        }
        startButton.setDisable(false);
        stopButton.setDisable(true);
        progressBar.progressProperty().unbind();
        progressBar.setProgress(0);
    }

    private void downloadImage(String imageUrl) {
        executorService.submit(() -> {
            try {
                String imgFolder = "img";
                Files.createDirectories(Paths.get(imgFolder));
                String fileName = imgFolder + "/" + imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
                try (InputStream in = new URL(imageUrl).openStream();
                     OutputStream out = new FileOutputStream(fileName)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        if (downloadTask.isCancelled()) return;
                        out.write(buffer, 0, bytesRead);
                    }
                }
                Platform.runLater(() -> updateImageView(fileName));
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Stop", ""));
            }
        });
    }

    private void updateImageView(String imagePath) {
        File file = new File(imagePath);
        if (file.exists()) {
            Image newImage = new Image(file.toURI().toString());
            imageView.setImage(newImage);
        }
    }

    private String extractValue(String json, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    @Override
    public void stop() {
        executorService.shutdownNow();
    }

    public static void main(String[] args) {
        launch(args);
    }
}