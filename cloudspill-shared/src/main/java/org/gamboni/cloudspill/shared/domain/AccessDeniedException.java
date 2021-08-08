package org.gamboni.cloudspill.shared.domain;

/**
 * @author tendays
 */
public class AccessDeniedException extends Exception {
    public AccessDeniedException(String message) {
        super(message);
    }
}
