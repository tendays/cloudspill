package org.gamboni.cloudspill.server.html;

import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.shared.api.ItemCredentials;

/**
 * @author tendays
 */
public class LabPage extends AbstractPage {
    public LabPage(BackendConfiguration configuration, ItemCredentials credentials) {
        super(configuration, credentials);
    }

    @Override
    protected String getTitle() {
        return "CloudSpill LAB";
    }

    @Override
    protected String getPageUrl() {
        return api.getBaseUrl() +"lab";
    }

    @Override
    protected HtmlFragment getBody(ItemCredentials.AuthenticationStatus authStatus) {
        return tag("div", "page used for experimental stuff");
    }
}
