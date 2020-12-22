package org.gamboni.cloudspill.server.html;

import com.google.common.collect.Iterables;

import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.api.ItemSecurity;

import java.util.List;
import java.util.Optional;

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

    public Optional<ItemCredentials.UserCredentials> getUserCredentials() {
        for (ItemCredentials c : credentials) {
            if (c instanceof ItemCredentials.UserCredentials) {
                return Optional.of((ItemCredentials.UserCredentials)c);
            }
        }
        return Optional.empty();
    }
}
