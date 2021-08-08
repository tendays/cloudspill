package org.gamboni.cloudspill.shared.domain;

import org.gamboni.cloudspill.shared.api.ItemCredentials;

/** {@link IsUser} implementation containing just a username, suitable to create a {@link ItemCredentials.UserCredentials}
 * credential object.
 *
 * @author tendays
 */
public class ClientUser implements IsUser {

    private final String name;

    public ClientUser(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void verifyPassword(String password) throws InvalidPasswordException {
        /* This object is used in the forwarder for users which aren't in the database, so we should reject logins using such a user */
        throw new InvalidPasswordException("User does not exist");
    }

    @Override
    public void verifyGroup(String group) throws PermissionDeniedException {
        throw new PermissionDeniedException("User does not exist");
    }

    @Override
    public boolean hasGroup(String group) {
        return false;
    }
}
