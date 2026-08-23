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
import javafx.util.StringConverter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.github.dicur3x.lss.ui.I18n.tr;

public final class ComponentsDialog {
    private final ManagedToolsService toolsService;
    private final Consumer<String> openLink;
    private final Map<ManagedComponent, ComponentRow> rows = new EnumMap<>(ManagedComponent.class);
    private final Map<ManagedComponent, ComponentRelease> latestReleases = new EnumMap<>(ManagedComponent.class);
    private final Map<ManagedComponent, Boolean> installAvailable = new EnumMap<>(ManagedComponent.class);
    private final AtomicReference<Thread> activeThread = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Button checkUpdates = new Button(tr("components.checkVersions"));
    private final Button updateComponents = new Button(tr("components.updatePrograms"));
    private final Button setupRecommended = new Button(tr("components.setupRecommended"));
    private final Button cancel = new Button(tr("components.cancelDownload"));
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label operationPercent = new Label();
    private final Label operationStatus = new Label(tr("components.notChecked"));
    private final ComboBox<WhisperModelProfile> modelProfiles = new ComboBox<>();
    private final Label modelDescription = new Label();
    private final Label modelStatus = new Label();
    private final Button installModel = new Button(tr("components.installModel"));
    private boolean recommendedSetupComplete;
    private boolean versionsChecked;

    public ComponentsDialog(ManagedToolsService toolsService, Consumer<String> openLink) {
        this.toolsService = Objects.requireNonNull(toolsService, "toolsService");
        this.openLink = Objects.requireNonNull(openLink, "openLink");
    }

    public void showAndWait(Window owner) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(tr("components.title"));
        dialog.setHeaderText(tr("components.header"));
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().add(
                new ButtonType(tr("common.close"), javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE));
        dialog.getDialogPane().setPrefSize(860, 650);
        dialog.getDialogPane().getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/io/github/dicur3x/lss/app.css"), "app.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().addAll("settings-dialog", "components-dialog");

        Label explanation = new Label(tr("components.explanation"));
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
        operationPercent.getStyleClass().add("progress-percentage");
        operationPercent.setVisible(false);
        operationPercent.setManaged(false);
        operationStatus.setWrapText(true);
        operationStatus.getStyleClass().add("muted");

        FlowPane componentActions = new FlowPane(10, 10,
                setupRecommended, updateComponents, checkUpdates, cancel);
        componentActions.setAlignment(Pos.CENTER_LEFT);
        HBox progressRow = new HBox(10, progressBar, operationPercent);
        progressRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(progressBar, Priority.ALWAYS);
        VBox downloadState = new VBox(8, componentActions, progressRow, operationStatus);

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
        Label status = new Label(tr("components.checkingLocal"));
        status.setWrapText(true);
        status.getStyleClass().add("muted");
        Label license = new Label(component == ManagedComponent.FFMPEG
                ? tr("components.ffmpegLicense") : tr("components.whisperLicense"));
        license.setWrapText(true);
        license.getStyleClass().add("component-license");

        Button install = new Button(tr("components.install"));
        install.getStyleClass().add("primary-button");
        install.setDisable(true);
        Button notes = new Button(tr("components.releaseNotes"));
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
        Label title = new Label(tr("components.modelTitle"));
        title.getStyleClass().add("component-title");
        Label source = new Label(tr("components.modelSourceText"));
        source.setWrapText(true);
        source.getStyleClass().add("component-license");
        Label modelUpdates = new Label(tr("components.modelUpdates"));
        modelUpdates.setWrapText(true);
        modelUpdates.getStyleClass().add("component-license");

        modelProfiles.setItems(FXCollections.observableArrayList(WhisperModelProfile.values()));
        modelProfiles.setConverter(new StringConverter<>() {
            @Override
            public String toString(WhisperModelProfile profile) {
                return profile == null ? "" : profileName(profile);
            }

            @Override
            public WhisperModelProfile fromString(String value) {
                return modelProfiles.getValue();
            }
        });
        modelProfiles.getSelectionModel().select(WhisperModelProfile.BALANCED);
        modelProfiles.setMaxWidth(Double.MAX_VALUE);
        modelProfiles.valueProperty().addListener((observable, oldProfile, newProfile) -> updateModelChoice());
        modelDescription.setWrapText(true);
        modelDescription.getStyleClass().add("muted");
        modelStatus.setWrapText(true);
        modelStatus.getStyleClass().add("muted");

        installModel.getStyleClass().add("primary-button");
        installModel.setOnAction(event -> installSelectedModel());
        Button modelSource = new Button(tr("components.modelSource"));
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
        boolean programsInstalled = true;
        for (ManagedComponent component : ManagedComponent.values()) {
            try {
                Optional<InstalledComponent> installed = toolsService.current(component);
                programsInstalled &= installed.isPresent();
                rows.get(component).status().setText(installed
                        .map(value -> tr("components.managedInstalled", value.version()))
                        .orElse(tr("components.notManaged")));
            } catch (Exception exception) {
                programsInstalled = false;
                rows.get(component).status().setText(exception.getMessage());
            }
        }
        boolean modelInstalled = false;
        try {
            Optional<InstalledModelBundle> current = toolsService.currentModel();
            modelInstalled = current.isPresent();
            if (current.isPresent()) {
                WhisperModelProfile profile = current.orElseThrow().profile();
                modelProfiles.getSelectionModel().select(profile);
                modelStatus.setText(tr("components.modelInstalled", profileName(profile)));
            } else {
                modelStatus.setText(tr("components.noModel"));
            }
        } catch (Exception exception) {
            modelStatus.setText(exception.getMessage());
        }
        recommendedSetupComplete = programsInstalled && modelInstalled;
        versionsChecked = false;
        updateModelChoice();
        updateActionAvailability(false);
    }

    private void checkAllComponents() {
        runTask(
                tr("components.checkingSources"),
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
                                    exception.getMessage() == null ? tr("components.checkFailed") : exception.getMessage()));
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
                            ? tr("components.checkComplete")
                            : tr("components.sourceErrors", failures));
                    versionsChecked = failures == 0;
                }
        );
    }

    private void renderCheck(ComponentCheck check) {
        ComponentRow row = rows.get(check.component());
        String configured = check.configuredVersion().isBlank()
                ? tr("components.notDetected") : check.configuredVersion();
        String status = tr("components.versionStatus", configured, check.latestRelease().version());
        if (!check.updateAvailable()) {
            status += " " + tr("components.upToDateSuffix");
        }
        installAvailable.put(check.component(), check.updateAvailable());
        row.status().setText(status);
        row.install().setText(check.configuredVersion().isBlank()
                ? tr("components.install") : tr("components.installUpdate"));
        row.install().setDisable(!check.updateAvailable());
        row.notes().setDisable(false);
    }

    private void installComponent(ManagedComponent component) {
        ComponentRelease release = latestReleases.get(component);
        if (release == null) {
            operationStatus.setText(tr("components.checkBeforeInstall"));
            return;
        }
        runTask(
                tr("components.preparing", component.displayName()),
                () -> toolsService.install(release, progressListener(), Thread.currentThread()::isInterrupted),
                installed -> {
                    ComponentRow row = rows.get(component);
                    row.status().setText(tr("components.managedActive", installed.version()));
                    installAvailable.put(component, false);
                    operationStatus.setText(tr("components.componentReady", component.displayName()));
                    refreshRecommendedSetupState();
                }
        );
    }

    private void setupRecommended() {
        runTask(
                tr("components.preparingRecommended"),
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
                        row.status().setText(tr("components.managedActive", installed.version()));
                        installAvailable.put(component, false);
                    });
                    modelProfiles.getSelectionModel().select(WhisperModelProfile.BALANCED);
                    modelStatus.setText(tr("components.modelInstalled", profileName(WhisperModelProfile.BALANCED)));
                    operationStatus.setText(tr("components.recommendedReady"));
                    recommendedSetupComplete = true;
                    versionsChecked = false;
                }
        );
    }

    private void updateComponents() {
        runTask(
                tr("components.updatingPrograms"),
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
                                    ? tr("components.updateFailed") : exception.getMessage());
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
                        rows.get(component).status().setText(tr("components.managedActive", installed.version()));
                        installAvailable.put(component, false);
                    });
                    result.errors().forEach((component, message) -> rows.get(component).status().setText(message));
                    if (!result.errors().isEmpty()) {
                        versionsChecked = false;
                        operationStatus.setText(tr("components.someUpdateFailed"));
                        operationStatus.getStyleClass().add("validation-warning");
                    } else if (result.components().isEmpty()) {
                        versionsChecked = true;
                        operationStatus.setText(tr("components.alreadyCurrent"));
                    } else {
                        versionsChecked = true;
                        operationStatus.setText(tr("components.programsUpdated"));
                    }
                    refreshRecommendedSetupState();
                }
        );
    }

    private void installSelectedModel() {
        WhisperModelProfile profile = modelProfiles.getValue();
        if (profile == null) {
            return;
        }
        runTask(
                tr("components.preparing", profileName(profile)),
                () -> toolsService.installModel(profile, progressListener(), Thread.currentThread()::isInterrupted),
                installed -> {
                    modelStatus.setText(tr("components.modelInstalledApplied", profileName(profile)));
                    installModel.setDisable(true);
                    operationStatus.setText(tr("components.recognitionReady"));
                    refreshRecommendedSetupState();
                }
        );
    }

    private void updateModelChoice() {
        WhisperModelProfile profile = modelProfiles.getValue();
        if (profile == null) {
            return;
        }
        modelDescription.setText(profileDescription(profile) + " "
                + tr("components.downloadSize", formatSize(profile.sizeBytes())));
        try {
            boolean alreadyInstalled = toolsService.currentModel()
                    .filter(bundle -> bundle.profileId().equals(profile.id())
                            && bundle.modelSha256().equalsIgnoreCase(profile.sha256()))
                    .isPresent();
            installModel.setDisable(alreadyInstalled || activeThread.get() != null);
            installModel.setText(alreadyInstalled ? tr("components.installed") : tr("components.installModel"));
        } catch (Exception exception) {
            installModel.setDisable(false);
            installModel.setText(tr("components.installModel"));
        }
    }

    private OperationProgress progressListener() {
        return (phase, completed, total) -> Platform.runLater(() -> {
            if (closed.get()) {
                return;
            }
            progressBar.setProgress(total > 0 ? Math.min(1d, completed / (double) total) : -1d);
            operationPercent.setText(total > 0
                    ? Math.min(100, Math.max(0, Math.round(completed * 100f / total))) + "%"
                    : tr("common.working"));
            String localizedPhase = localizedProgressPhase(phase);
            operationStatus.setText(total > 0
                    ? localizedPhase + " · " + formatSize(completed) + " / " + formatSize(total)
                    : localizedPhase + " · " + formatSize(completed));
        });
    }

    private static String localizedProgressPhase(String phase) {
        if (phase == null || phase.isBlank()) {
            return tr("common.working");
        }
        if (phase.equals("Model and voice detection are ready")) {
            return tr("components.phaseModelReady");
        }
        if (phase.endsWith(" (already verified)")) {
            return tr("components.phaseAlreadyVerified", localizedProgressPhase(
                    phase.substring(0, phase.length() - " (already verified)".length())));
        }
        if (phase.startsWith("Downloading ")) {
            return tr("components.phaseDownloading", localizedProgressTarget(
                    phase.substring("Downloading ".length())));
        }
        if (phase.startsWith("Checking and unpacking ")) {
            return tr("components.phaseChecking", localizedProgressTarget(
                    phase.substring("Checking and unpacking ".length())));
        }
        if (phase.endsWith(" is ready")) {
            return tr("components.phaseReady", localizedProgressTarget(
                    phase.substring(0, phase.length() - " is ready".length())));
        }
        return phase;
    }

    private static String localizedProgressTarget(String target) {
        for (WhisperModelProfile profile : WhisperModelProfile.values()) {
            if (profile.displayName().equals(target)) {
                return profileName(profile);
            }
        }
        return target;
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
                        operationStatus.setText(tr("components.operationCancelled"));
                        setBusy(false);
                    }
                });
            } catch (Exception exception) {
                Platform.runLater(() -> {
                    if (!closed.get()) {
                        operationStatus.setText(exception.getMessage() == null
                                ? tr("components.operationFailed") : exception.getMessage());
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
        operationPercent.setText(tr("common.working"));
        thread.start();
    }

    private void cancelActiveOperation() {
        Thread thread = activeThread.get();
        if (thread != null) {
            thread.interrupt();
            operationStatus.setText(tr("components.cancelling"));
        }
    }

    private void setBusy(boolean busy) {
        updateActionAvailability(busy);
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
        operationPercent.setVisible(busy);
        operationPercent.setManaged(busy);
    }

    private void updateActionAvailability(boolean busy) {
        setupRecommended.setDisable(busy || recommendedSetupComplete);
        updateComponents.setDisable(busy || !versionsChecked || !hasProgramUpdate());
        checkUpdates.setDisable(busy);
    }

    private boolean hasProgramUpdate() {
        return installAvailable.values().stream().anyMatch(Boolean.TRUE::equals);
    }

    private void refreshRecommendedSetupState() {
        try {
            boolean programsInstalled = true;
            for (ManagedComponent component : ManagedComponent.values()) {
                programsInstalled &= toolsService.current(component).isPresent();
            }
            recommendedSetupComplete = programsInstalled && toolsService.currentModel().isPresent();
        } catch (Exception exception) {
            recommendedSetupComplete = false;
        }
    }

    private static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024 * 1024) {
            return String.format(java.util.Locale.ROOT, "%.0f %s", bytes / 1024d, tr("unit.kb"));
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(java.util.Locale.ROOT, "%.1f %s", bytes / (1024d * 1024d), tr("unit.mb"));
        }
        return String.format(java.util.Locale.ROOT, "%.1f %s", bytes / (1024d * 1024d * 1024d), tr("unit.gb"));
    }

    private static String profileName(WhisperModelProfile profile) {
        return tr("model." + profile.id() + ".name");
    }

    private static String profileDescription(WhisperModelProfile profile) {
        return tr("model." + profile.id() + ".description");
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
