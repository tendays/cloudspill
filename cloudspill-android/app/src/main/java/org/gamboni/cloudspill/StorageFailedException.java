package org.gamboni.cloudspill;

/**
 * @author tendays
 */

public class StorageFailedException extends Exception {
    public StorageFailedException(String message) {
        super(message);
    }

    public StorageFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
