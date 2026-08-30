package io.github.dicur3x.lss.translation;

import java.util.function.BooleanSupplier;

/** Replaceable local or explicitly enabled cloud translation backend. */
@FunctionalInterface
public interface TranslationEngine {
    TranslationBatchResult translate(
            TranslationBatch batch,
            BooleanSupplier cancellationRequested
    ) throws TranslationException;
}
