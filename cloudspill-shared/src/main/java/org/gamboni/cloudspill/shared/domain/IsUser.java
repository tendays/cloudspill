package org.gamboni.cloudspill.shared.domain;

import org.gamboni.cloudspill.shared.util.Supplier;

/**
 * @author tendays
 */
public interface IsUser {
    String getName();

    /** Verify if the given password String is valid, and throw an InvalidPasswordException if it is not. */
    void verifyPassword(String password) throws InvalidPasswordException;

    /** Verify that the user belongs to the given group, throw a PermissionDeniedException if they do not. */
    void verifyGroup(String group) throws PermissionDeniedException;

    /** True if the user belongs to the given group, false if not. */
    boolean hasGroup(String group);
}
