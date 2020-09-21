package org.gamboni.cloudspill.server.html;

import org.gamboni.cloudspill.shared.api.ItemCredentials;

/**
 * @author tendays
 */
public class OutputModel {
    public final ItemCredentials credentials;

    public OutputModel(ItemCredentials credentials) {
        this.credentials = credentials;
    }
}
