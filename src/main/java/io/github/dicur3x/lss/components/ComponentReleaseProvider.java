package io.github.dicur3x.lss.components;

import java.util.function.BooleanSupplier;

@FunctionalInterface
public interface ComponentReleaseProvider {
    ComponentRelease latest(BooleanSupplier cancellationRequested) throws ComponentException;
}
