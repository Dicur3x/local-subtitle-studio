package io.github.dicur3x.lss.ui;

import io.github.dicur3x.lss.components.ComponentCheck;
import io.github.dicur3x.lss.components.ComponentRelease;
import io.github.dicur3x.lss.components.InstalledComponent;
import io.github.dicur3x.lss.components.ManagedComponent;
import io.github.dicur3x.lss.components.ManagedToolsService;
import io.github.dicur3x.lss.components.OperationProgress;
import io.github.dicur3x.lss.models.InstalledModelBundle;
import io.github.dicur3x.lss.models.WhisperModelProfile;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class ComponentsDialog {
    private final ManagedToolsService toolsService;
    private final Consumer<String> openLink;
    private final Map<ManagedComponent, ComponentRow> rows = new EnumMap<>(ManagedComponent.class);
    private final Map<ManagedComponent, ComponentRelease> latestReleases = new EnumMap<>(ManagedComponent.class);
    private final Map<ManagedComponent, Boolean> installAvailable = new EnumMap<>(ManagedComponent.class);
    private final AtomicReference<Thread> activeThread = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Button checkUpdates = new Button("Check versions");
    private final Button updateComponents = new Button("Update FFmpeg + whisper.cpp");
    private final Button setupRecommended = new Button("Set up recommended tools + model (~700 MB)");
    private final Button cancel = new Button("Cancel download");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label operationStatus = new Label("No network check has been made yet.");
    private final ComboBox<WhisperModelProfile> modelProfiles = new ComboBox<>();
    private final Label modelDescription = new Label();
    private final Label modelStatus = new Label();
    private final Button installModel = new Button("Install selected model + VAD");

    public ComponentsDialog(ManagedToolsService toolsService, Consumer<String> openLink) {
        this.toolsService = Objects.requireNonNull(toolsService, "toolsService");
        this.openLink = Objects.requireNonNull(openLink, "openLink");
    }

    public void showAndWait(Window owner) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Local Subtitle Studio components");
        dialog.setHeaderText("One-click local components");
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(860, 650);
        dialog.getDialogPane().getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/io/github/dicur3x/lss/app.css"), "app.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().addAll("settings-dialog", "components-dialog");

        Label explanation = new Label(
                "The app downloads components from trusted project sources into your Local AppData folder and applies "
                        + "their paths automatically. Integrity details are recorded. Nothing is updated silently."
        );
        explanation.setWrapText(true);
        explanation.getStyleClass().add("muted");

        VBox components = new VBox(12);
        for (ManagedComponent component : ManagedComponent.values()) {
            ComponentRow row = createComponentRow(component);
            rows.put(component, row);
            components.getChildren().add(row.card());
        }

        setupRecommended.getStyleClass().add("primary-button");
        setupRecommended.setOnAction(event -> setupRecommended());
        updateComponents.getStyleClass().add("quiet-button");
        updateComponents.setOnAction(event -> updateComponents());
        checkUpdates.getStyleClass().add("quiet-button");
        checkUpdates.setOnAction(event -> checkAllComponents());
        cancel.getStyleClass().add("quiet-button");
        cancel.setVisible(false);
        cancel.setManaged(false);
        cancel.setOnAction(event -> cancelActiveOperation());
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);
        progressBar.setManaged(false);
        operationStatus.setWrapText(true);
        operationStatus.getStyleClass().add("muted");

        FlowPane componentActions = new FlowPane(10, 10,
                setupRecommended, updateComponents, checkUpdates, cancel);
        componentActions.setAlignment(Pos.CENTER_LEFT);
        VBox downloadState = new VBox(8, componentActions, progressBar, operationStatus);

        VBox modelCard = createModelCard();
        VBox content = new VBox(18, explanation, components, downloadState, modelCard);
        content.setPadding(new Insets(8));

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("components-scroll");
        dialog.getDialogPane().setContent(scrollPane);

        loadLocalState();
        dialog.setOnHidden(event -> {
            closed.set(true);
            Thread thread = activeThread.getAndSet(null);
            if (thread != null) {
                thread.interrupt();
            }
        });
        dialog.showAndWait();
    }

    private ComponentRow createComponentRow(ManagedComponent component) {
        Label title = new Label(component.displayName());
        title.getStyleClass().add("component-title");
        Label status = new Label("Checking local installation…");
        status.setWrapText(true);
        status.getStyleClass().add("muted");
        Label license = new Label(component == ManagedComponent.FFMPEG
                ? "Windows essentials build · GPLv3 · SHA-256 verified before installation"
                : "Official stable Windows x64 release · MIT · published SHA-256 verified when available");
        license.setWrapText(true);
        license.getStyleClass().add("component-license");

        Button install = new Button("Install");
        install.getStyleClass().add("primary-button");
        install.setDisable(true);
        Button notes = new Button("Release notes");
        notes.getStyleClass().add("quiet-button");
        notes.setDisable(true);
        notes.setOnAction(event -> Optional.ofNullable(latestReleases.get(component))
                .ifPresent(release -> new ReleaseNotesDialog(openLink).showAndWait(
                        notes.getScene().getWindow(), release)));
        install.setOnAction(event -> installComponent(component));

        HBox heading = new HBox(12, title, spacer(), install, notes);
        heading.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(7, heading, status, license);
        card.getStyleClass().add("component-card");
        return new ComponentRow(card, status, install, notes);
    }

    private VBox createModelCard() {
        Label title = new Label("Recognition model + voice detection");
        title.getStyleClass().add("component-title");
        Label source = new Label(
                "Official converted OpenAI Whisper weights and Silero VAD · MIT · exact size and SHA-256 checks"
        );
        source.setWrapText(true);
        source.getStyleClass().add("component-license");
        Label modelUpdates = new Label(
                "Model weights are fixed artifacts, so they do not have a traditional changelog. "
                        + "A changed recommended profile will be shown as a new catalog choice."
        );
        modelUpdates.setWrapText(true);
        modelUpdates.getStyleClass().add("component-license");

        modelProfiles.setItems(FXCollections.observableArrayList(WhisperModelProfile.values()));
        modelProfiles.getSelectionModel().select(WhisperModelProfile.BALANCED);
        modelProfiles.setMaxWidth(Double.MAX_VALUE);
        modelProfiles.valueProperty().addListener((observable, oldProfile, newProfile) -> updateModelChoice());
        modelDescription.setWrapText(true);
        modelDescription.getStyleClass().add("muted");
        modelStatus.setWrapText(true);
        modelStatus.getStyleClass().add("muted");

        installModel.getStyleClass().add("primary-button");
        installModel.setOnAction(event -> installSelectedModel());
        Button modelSource = new Button("Model source");
        modelSource.getStyleClass().add("quiet-button");
        modelSource.setOnAction(event -> openLink.accept("https://huggingface.co/ggerganov/whisper.cpp"));
        HBox actions = new HBox(10, installModel, modelSource);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(9, title, modelProfiles, modelDescription, modelStatus, source, modelUpdates, actions);
        card.getStyleClass().add("component-card");
        updateModelChoice();
        return card;
    }

    private void loadLocalState() {
        for (ManagedComponent component : ManagedComponent.values()) {
            try {
                Optional<InstalledComponent> installed = toolsService.current(component);
                rows.get(component).status().setText(installed
                        .map(value -> "Managed version " + value.version() + " is installed. Check for updates when ready.")
                        .orElse("Not installed by the app. The current manual or PATH configuration is still used."));
            } catch (Exception exception) {
                rows.get(component).status().setText(exception.getMessage());
            }
        }
        try {
            Optional<InstalledModelBundle> current = toolsService.currentModel();
            if (current.isPresent()) {
                WhisperModelProfile profile = current.orElseThrow().profile();
                modelProfiles.getSelectionModel().select(profile);
                modelStatus.setText("Installed: " + profile.displayName() + " · VAD is ready");
            } else {
                modelStatus.setText("No managed recognition model is installed yet.");
            }
        } catch (Exception exception) {
            modelStatus.setText(exception.getMessage());
        }
        updateModelChoice();
    }

    private void checkAllComponents() {
        runTask(
                "Checking official release sources…",
                () -> {
                    Map<ManagedComponent, CheckOutcome> checks = new EnumMap<>(ManagedComponent.class);
                    for (ManagedComponent component : ManagedComponent.values()) {
                        try {
                            checks.put(component, new CheckOutcome(
                                    toolsService.check(component, Thread.currentThread()::isInterrupted), ""));
                        } catch (CancellationException exception) {
                            throw exception;
                        } catch (Exception exception) {
                            checks.put(component, new CheckOutcome(null,
                                    exception.getMessage() == null ? "Version check failed." : exception.getMessage()));
                        }
                    }
                    return checks;
                },
                checks -> {
                    int failures = 0;
                    for (Map.Entry<ManagedComponent, CheckOutcome> entry : checks.entrySet()) {
                        CheckOutcome outcome = entry.getValue();
                        if (outcome.check() == null) {
                            failures++;
                            rows.get(entry.getKey()).status().setText(outcome.error());
                        } else {
                            ComponentCheck check = outcome.check();
                            latestReleases.put(check.component(), check.latestRelease());
                            renderCheck(check);
                        }
                    }
                    operationStatus.setText(failures == 0
                            ? "Version check complete. Updates are installed only when you choose them."
                            : "Version check completed with " + failures
                            + (failures == 1 ? " source error. Try again later." : " source errors. Try again later."));
                }
        );
    }

    private void renderCheck(ComponentCheck check) {
        ComponentRow row = rows.get(check.component());
        String configured = check.configuredVersion().isBlank()
                ? "not detected" : check.configuredVersion();
        String status = "Configured: " + configured + " · available stable: " + check.latestRelease().version();
        if (!check.updateAvailable()) {
            status += " · up to date";
        }
        installAvailable.put(check.component(), check.updateAvailable());
        row.status().setText(status);
        row.install().setText(check.configuredVersion().isBlank() ? "Install" : "Install / update");
        row.install().setDisable(!check.updateAvailable());
        row.notes().setDisable(false);
    }

    private void installComponent(ManagedComponent component) {
        ComponentRelease release = latestReleases.get(component);
        if (release == null) {
            operationStatus.setText("Check for updates before installing a component.");
            return;
        }
        runTask(
                "Preparing " + component.displayName() + "…",
                () -> toolsService.install(release, progressListener(), Thread.currentThread()::isInterrupted),
                installed -> {
                    ComponentRow row = rows.get(component);
                    row.status().setText("Managed version " + installed.version()
                            + " is installed and active.");
                    installAvailable.put(component, false);
                    operationStatus.setText(component.displayName() + " is ready. Its path was applied automatically.");
                }
        );
    }

    private void setupRecommended() {
        runTask(
                "Checking and preparing the recommended local toolchain…",
                () -> {
                    Map<ManagedComponent, InstalledComponent> installed = new EnumMap<>(ManagedComponent.class);
                    for (ManagedComponent component : ManagedComponent.values()) {
                        ComponentCheck check = toolsService.check(
                                component, Thread.currentThread()::isInterrupted);
                        if (check.updateAvailable() || toolsService.current(component).isEmpty()) {
                            installed.put(component, toolsService.install(
                                    check.latestRelease(), progressListener(), Thread.currentThread()::isInterrupted));
                        }
                    }
                    InstalledModelBundle model = toolsService.installModel(
                            WhisperModelProfile.BALANCED,
                            progressListener(), Thread.currentThread()::isInterrupted);
                    return new SetupResult(installed, model);
                },
                result -> {
                    result.components().forEach((component, installed) -> {
                        ComponentRow row = rows.get(component);
                        row.status().setText("Managed version " + installed.version()
                                + " is installed and active.");
                        installAvailable.put(component, false);
                    });
                    modelProfiles.getSelectionModel().select(WhisperModelProfile.BALANCED);
                    modelStatus.setText("Installed: Balanced (recommended) · VAD is ready");
                    operationStatus.setText("Recommended local components, model, and voice detection are ready.");
                }
        );
    }

    private void updateComponents() {
        runTask(
                "Checking and updating FFmpeg and whisper.cpp…",
                () -> {
                    Map<ManagedComponent, ComponentCheck> checks = new EnumMap<>(ManagedComponent.class);
                    Map<ManagedComponent, InstalledComponent> installed = new EnumMap<>(ManagedComponent.class);
                    Map<ManagedComponent, String> errors = new EnumMap<>(ManagedComponent.class);
                    for (ManagedComponent component : ManagedComponent.values()) {
                        try {
                            ComponentCheck check = toolsService.check(
                                    component, Thread.currentThread()::isInterrupted);
                            checks.put(component, check);
                            if (check.updateAvailable()) {
                                installed.put(component, toolsService.install(
                                        check.latestRelease(), progressListener(),
                                        Thread.currentThread()::isInterrupted));
                            }
                        } catch (CancellationException exception) {
                            throw exception;
                        } catch (Exception exception) {
                            errors.put(component, exception.getMessage() == null
                                    ? "Update failed." : exception.getMessage());
                        }
                    }
                    return new ComponentUpdateResult(checks, installed, errors);
                },
                result -> {
                    result.checks().forEach((component, check) -> {
                        latestReleases.put(component, check.latestRelease());
                        renderCheck(check);
                    });
                    result.components().forEach((component, installed) -> {
                        rows.get(component).status().setText("Managed version " + installed.version()
                                + " is installed and active.");
                        installAvailable.put(component, false);
                    });
                    result.errors().forEach((component, message) -> rows.get(component).status().setText(message));
                    if (!result.errors().isEmpty()) {
                        operationStatus.setText("Some program components could not be updated. The model was not changed.");
                        operationStatus.getStyleClass().add("validation-warning");
                    } else if (result.components().isEmpty()) {
                        operationStatus.setText("FFmpeg and whisper.cpp are already current. The model was not changed.");
                    } else {
                        operationStatus.setText("Program components updated. The selected model was not changed.");
                    }
                }
        );
    }

    private void installSelectedModel() {
        WhisperModelProfile profile = modelProfiles.getValue();
        if (profile == null) {
            return;
        }
        runTask(
                "Preparing " + profile.displayName() + "…",
                () -> toolsService.installModel(profile, progressListener(), Thread.currentThread()::isInterrupted),
                installed -> {
                    modelStatus.setText("Installed: " + profile.displayName()
                            + " · model and Silero VAD paths applied automatically");
                    installModel.setDisable(true);
                    operationStatus.setText("Recognition model and voice detection are ready.");
                }
        );
    }

    private void updateModelChoice() {
        WhisperModelProfile profile = modelProfiles.getValue();
        if (profile == null) {
            return;
        }
        modelDescription.setText(profile.description() + " Download: " + formatSize(profile.sizeBytes()) + ".");
        try {
            boolean alreadyInstalled = toolsService.currentModel()
                    .filter(bundle -> bundle.profileId().equals(profile.id())
                            && bundle.modelSha256().equalsIgnoreCase(profile.sha256()))
                    .isPresent();
            installModel.setDisable(alreadyInstalled || activeThread.get() != null);
            installModel.setText(alreadyInstalled ? "Installed" : "Install selected model + VAD");
        } catch (Exception exception) {
            installModel.setDisable(false);
            installModel.setText("Install selected model + VAD");
        }
    }

    private OperationProgress progressListener() {
        return (phase, completed, total) -> Platform.runLater(() -> {
            if (closed.get()) {
                return;
            }
            progressBar.setProgress(total > 0 ? Math.min(1d, completed / (double) total) : -1d);
            operationStatus.setText(total > 0
                    ? phase + " · " + formatSize(completed) + " / " + formatSize(total)
                    : phase + " · " + formatSize(completed));
        });
    }

    private <T> void runTask(String startingMessage, Callable<T> task, Consumer<T> success) {
        Thread thread = Thread.ofVirtual().unstarted(() -> {
            try {
                T result = task.call();
                Platform.runLater(() -> {
                    if (!closed.get()) {
                        success.accept(result);
                        setBusy(false);
                    }
                });
            } catch (CancellationException exception) {
                Platform.runLater(() -> {
                    if (!closed.get()) {
                        operationStatus.setText("Operation cancelled. No incomplete component was activated.");
                        setBusy(false);
                    }
                });
            } catch (Exception exception) {
                Platform.runLater(() -> {
                    if (!closed.get()) {
                        operationStatus.setText(exception.getMessage() == null
                                ? "The component operation failed." : exception.getMessage());
                        operationStatus.getStyleClass().add("validation-warning");
                        setBusy(false);
                    }
                });
            } finally {
                activeThread.compareAndSet(Thread.currentThread(), null);
            }
        });
        if (!activeThread.compareAndSet(null, thread)) {
            return;
        }
        operationStatus.getStyleClass().remove("validation-warning");
        setBusy(true);
        operationStatus.setText(startingMessage);
        progressBar.setProgress(-1);
        thread.start();
    }

    private void cancelActiveOperation() {
        Thread thread = activeThread.get();
        if (thread != null) {
            thread.interrupt();
            operationStatus.setText("Cancelling…");
        }
    }

    private void setBusy(boolean busy) {
        setupRecommended.setDisable(busy);
        updateComponents.setDisable(busy);
        checkUpdates.setDisable(busy);
        rows.forEach((component, row) -> {
            row.install().setDisable(busy || !Boolean.TRUE.equals(installAvailable.get(component)));
            row.notes().setDisable(busy || !latestReleases.containsKey(component));
        });
        modelProfiles.setDisable(busy);
        if (busy) {
            installModel.setDisable(true);
        } else {
            updateModelChoice();
        }
        cancel.setVisible(busy);
        cancel.setManaged(busy);
        progressBar.setVisible(busy);
        progressBar.setManaged(busy);
    }

    private static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024 * 1024) {
            return String.format(java.util.Locale.ROOT, "%.0f KB", bytes / 1024d);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024d * 1024d));
        }
        return String.format(java.util.Locale.ROOT, "%.1f GB", bytes / (1024d * 1024d * 1024d));
    }

    private record ComponentRow(
            VBox card,
            Label status,
            Button install,
            Button notes
    ) {
    }

    private record CheckOutcome(ComponentCheck check, String error) {
    }

    private record SetupResult(
            Map<ManagedComponent, InstalledComponent> components,
            InstalledModelBundle model
    ) {
        private SetupResult {
            components = Map.copyOf(components);
            Objects.requireNonNull(model, "model");
        }
    }

    private record ComponentUpdateResult(
            Map<ManagedComponent, ComponentCheck> checks,
            Map<ManagedComponent, InstalledComponent> components,
            Map<ManagedComponent, String> errors
    ) {
        private ComponentUpdateResult {
            checks = Map.copyOf(checks);
            components = Map.copyOf(components);
            errors = Map.copyOf(errors);
        }
    }
}
