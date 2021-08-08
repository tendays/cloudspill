package org.gamboni.cloudspill.shared.domain;

/**
 * @author tendays
 */
public class InvalidPasswordException extends AccessDeniedException {
    public InvalidPasswordException(String message) {
        super("Invalid password or token: "+ message);
    }
}
