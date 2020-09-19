package org.gamboni.cloudspill.server.html;

import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
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
        long itemId = 49201;
        return tag("div", HtmlFragment.escape("page used for experimental stuff"),
                tag("div", "id='drawer' class='drawer'",
                        HtmlFragment.escape("Newly added items"),
                        tag("a", "href=" + quote(
                                api.getImagePageUrl(itemId, null, new ItemCredentials.UserPassword())) +" target='_blank'",
                                unclosedTag("img src="+ quote(
                                        api.getThumbnailUrl(
                                                itemId,
                                                new ItemCredentials.UserPassword(),
                                                CloudSpillApi.Size.GALLERY_THUMBNAIL)))
                        )
                        ));
    }
}
