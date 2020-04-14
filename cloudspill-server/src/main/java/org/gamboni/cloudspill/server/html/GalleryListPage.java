package org.gamboni.cloudspill.server.html;

import org.gamboni.cloudspill.domain.ServerDomain;
import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.server.config.ServerConfiguration;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;

import java.util.List;

/**
 * @author tendays
 */
public class GalleryListPage extends AbstractPage {

    private final ServerDomain domain;
    private final ServerConfiguration serverConfiguration;

    public GalleryListPage(ServerConfiguration configuration, ServerDomain domain) {
        super(configuration);
        this.domain = domain;
        this.serverConfiguration = configuration;
    }

    @Override
    protected String getTitle() {
        return serverConfiguration.getRepositoryName();
    }

    @Override
    protected String getPageUrl(User user) {
        return configuration.getPublicUrl() + "/public/gallery/";
    }

    @Override
    protected HtmlFragment getBody(User user) {
        return HtmlFragment.concatenate(domain.selectGalleryPart().list()
                .stream()
                .map(gp -> {
                    System.out.println("Loading "+ gp.getUrl(api));
                    final List<Item> sample = gp.applyTo(domain.selectItem()).limit(1).list();
                    final String href = "href="+ quote(gp.getUrl(api));
                    if (sample.isEmpty()) {
                        return tag("a", "class='galleryLink' "+ href,
                                gp.buildTitle());
                    } else {
                        return tag("a", href,
                                tag("span", gp.buildTitle()),
                                unclosedTag("img class='galleryLink' src="+ quote(
                                                api.getThumbnailUrl(sample.get(0), CloudSpillApi.Size.GALLERY_THUMBNAIL)))
                                );
                    }
                })
                .toArray(HtmlFragment[]::new));
    }
}
