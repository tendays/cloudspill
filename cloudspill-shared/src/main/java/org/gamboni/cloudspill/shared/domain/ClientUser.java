package org.gamboni.cloudspill.shared.domain;

import org.gamboni.cloudspill.shared.util.Supplier;

/** {@link IsUser} implementation containing just a username, suitable to create a {@link org.gamboni.cloudspill.shared.api.ItemCredentials.UserPassword}
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
    public void verifyPassword(String password) {
        throw new UnsupportedOperationException();
    }
}
