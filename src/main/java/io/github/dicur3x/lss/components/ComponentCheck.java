package io.github.dicur3x.lss.components;

import java.util.Objects;
import java.util.Optional;

public record ComponentCheck(
        ManagedComponent component,
        String configuredVersion,
        String configuredPath,
        Optional<InstalledComponent> managedInstallation,
        ComponentRelease latestRelease,
        boolean updateAvailable
) {
    public ComponentCheck {
        component = Objects.requireNonNull(component, "component");
        configuredVersion = configuredVersion == null ? "" : configuredVersion.strip();
        configuredPath = configuredPath == null ? "" : configuredPath.strip();
        managedInstallation = Objects.requireNonNull(managedInstallation, "managedInstallation");
        latestRelease = Objects.requireNonNull(latestRelease, "latestRelease");
    }
}
