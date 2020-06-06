package org.gamboni.cloudspill.shared.domain;

import org.gamboni.cloudspill.shared.util.Supplier;

/**
 * @author tendays
 */
public interface IsUser {
    String getName();

    /** Verify if the given password String is valid, and throw an InvalidPasswordException if it is not. */
    void verifyPassword(String password) throws InvalidPasswordException;
}
