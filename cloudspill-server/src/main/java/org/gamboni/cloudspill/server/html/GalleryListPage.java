package org.gamboni.cloudspill.server.html;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.server.ServerConfiguration;

/**
 * @author tendays
 */
public class GalleryListPage extends AbstractPage {

    private final Domain domain;

    public GalleryListPage(ServerConfiguration configuration, Domain domain) {
        super(configuration);
        this.domain = domain;
    }

    @Override
    protected String getTitle() {
        return configuration.getRepositoryName();
    }

    @Override
    protected String getPageUrl(User user) {
        return configuration.getPublicUrl() + "/public/gallery/";
    }

    @Override
    protected HtmlFragment getBody(User user) {
        return HtmlFragment.concatenate(domain.selectGalleryPart().list()
                .stream()
                .map(gp -> tag("a", "href="+ quote(configuration.getPublicUrl() + gp.getUrl()), gp.getTitle()))
                .toArray(HtmlFragment[]::new));
    }
}
