package org.gamboni.cloudspill.shared.domain;

/**
 * @author tendays
 */
public class PermissionDeniedException extends AccessDeniedException {
    public PermissionDeniedException(String message) {
        super("Permission denied: "+ message);
    }
}
