package org.gamboni.cloudspill.server.html;

import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.api.ItemSecurity;

import java.util.List;

/**
 * @author tendays
 */
public class OutputModel {
    public final List<ItemCredentials> credentials;

    public OutputModel(List<ItemCredentials> credentials) {
        this.credentials = credentials;
    }

    public ItemCredentials.AuthenticationStatus getAuthStatus() {
        return ItemSecurity.mostPowerful(credentials).getAuthStatus();
    }
}
