package dev.emerald.core.data;

/** Raised when persisted data is missing a field or has the wrong shape. */
public class DataException extends RuntimeException {
    public DataException(String message) {
        super(message);
    }
}
