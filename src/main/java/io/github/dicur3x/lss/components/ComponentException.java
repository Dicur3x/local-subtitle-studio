package io.github.dicur3x.lss.components;

public final class ComponentException extends Exception {
    public ComponentException(String message) {
        super(message);
    }

    public ComponentException(String message, Throwable cause) {
        super(message, cause);
    }
}
