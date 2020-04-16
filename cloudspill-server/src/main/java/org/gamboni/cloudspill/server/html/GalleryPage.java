package org.gamboni.cloudspill.server.html;

import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.server.config.ServerConfiguration;
import org.gamboni.cloudspill.server.query.ItemSet;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.ItemCredentials;

/**
 * @author tendays
 */
public class GalleryPage extends AbstractPage {

    /* 60 is the smallest multiple of 2, 3, 4, 5 and 6. So as long as there are six or fewer images per row, the last result row will be full */
    private static final int PAGE_SIZE = 60;
    private final ItemSet set;

    public GalleryPage(BackendConfiguration configuration, ItemSet set) {
        super(configuration, configuration.getCss());

        this.set = set;
    }

    @Override
    protected String getTitle() {
        return set.getTitle();
    }

    @Override
    protected String getPageUrl() {
        return set.getUrl(api);
    }

    @Override
    protected HtmlFragment getBody(ItemCredentials.AuthenticationStatus authStatus) {
        int pageNumber = set.getOffset() / PAGE_SIZE;
        long totalCount = set.itemCount();
        return HtmlFragment.concatenate(
                tag("div", "class='description'", set.getDescription()),
                pageLink(pageNumber - 1, "<", totalCount),
                HtmlFragment.concatenate(
                        set.getSlice(PAGE_SIZE).stream().map(item ->
                                tag("a", "href=" + quote(
                                                        api.getImagePageUrl(item.getServerId(), authStatus.credentialsFor(item))),
                                        unclosedTag("img class='thumb' src=" +
                                                quote(api.getThumbnailUrl(item, CloudSpillApi.Size.IMAGE_THUMBNAIL))))
                        ).toArray(HtmlFragment[]::new)),
                pageLink(pageNumber + 1, ">", totalCount));
    }

    private HtmlFragment pageLink(int pageNumber, String label, long totalCount) {
        int offset = pageNumber * PAGE_SIZE;
        if (offset >= 0 && offset < totalCount) {
            return tag("a", "class='pagerLink' href="+ quote(set.atOffset(offset).getUrl(api)),
                    label);
        } else {
            return HtmlFragment.EMPTY;
        }
    }
}
