package org.gamboni.cloudspill.server.html;

import org.gamboni.cloudspill.domain.BackendItem;
import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.server.query.ItemSet;
import org.gamboni.cloudspill.server.query.Java8SearchCriteria;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.ItemCredentials;

/**
 * @author tendays
 */
public class GalleryPage extends AbstractPage {

    /* 60 is the smallest multiple of 2, 3, 4, 5 and 6. So as long as there are six or fewer images per row, the last result row will be full */
    public static final int PAGE_SIZE = 60;
    private final Java8SearchCriteria<BackendItem> criteria;
    private final ItemSet itemSet;

    public GalleryPage(BackendConfiguration configuration, Java8SearchCriteria<BackendItem> criteria, ItemSet itemSet) {
        super(configuration, configuration.getCss());
        this.criteria = criteria;
        this.itemSet = itemSet;
    }

    @Override
    protected String getTitle() {
        return criteria.buildTitle();
    }

    @Override
    protected String getPageUrl() {
        return criteria.getUrl(api);
    }

    @Override
    protected HtmlFragment getBody(ItemCredentials.AuthenticationStatus authStatus) {
        int pageNumber = criteria.getOffset() / PAGE_SIZE;
        return HtmlFragment.concatenate(
                tag("div", "class='description'", criteria.getDescription()),
                pageLink(pageNumber - 1, "<", itemSet.totalCount),
                HtmlFragment.concatenate(
                        itemSet.rows.stream().map(item ->
                                tag("a", "href=" + quote(
                                                        api.getImagePageUrl(item.getServerId(), authStatus.credentialsFor(item))),
                                        unclosedTag("img class='thumb' src=" +
                                                quote(api.getThumbnailUrl(item, CloudSpillApi.Size.IMAGE_THUMBNAIL))))
                        ).toArray(HtmlFragment[]::new)),
                pageLink(pageNumber + 1, ">", itemSet.totalCount));
    }

    private HtmlFragment pageLink(int pageNumber, String label, long totalCount) {
        int offset = pageNumber * PAGE_SIZE;
        if (offset >= 0 && offset < totalCount) {
            return tag("a", "class='pagerLink' href="+ quote(criteria.atOffset(offset).getUrl(api)),
                    label);
        } else {
            return HtmlFragment.EMPTY;
        }
    }
}
