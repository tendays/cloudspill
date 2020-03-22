package org.gamboni.cloudspill.server.html;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.server.ServerConfiguration;
import org.gamboni.cloudspill.server.query.Java8SearchCriteria;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;

/**
 * @author tendays
 */
public class GalleryPage extends AbstractPage {

    /* 60 is the smallest multiple of 2, 3, 4, 5 and 6. So as long as there are six or fewer images per row, the last result row will be full */
    private static final int PAGE_SIZE = 60;
    private final Domain domain;
    private final Java8SearchCriteria criteria;

    public GalleryPage(ServerConfiguration configuration, Domain domain, Java8SearchCriteria criteria) {
        super(configuration, configuration.getCss());

        this.domain = domain;
        this.criteria = criteria;
    }

    @Override
    protected String getTitle() {
        return criteria.buildTitle();
    }

    @Override
    protected String getPageUrl(User user) {
        return configuration.getPublicUrl() + criteria.getUrl();
    }

    @Override
    protected HtmlFragment getBody(User user) {
        final Domain.Query<Item> itemQuery = criteria.applyTo(domain.selectItem());

        int pageNumber = criteria.getOffset() / PAGE_SIZE;
        long totalCount = itemQuery.getTotalCount();
        return HtmlFragment.concatenate(
                tag("div", "class='description'", criteria.getDescription()),
                pageLink(pageNumber - 1, "<", totalCount),
                HtmlFragment.concatenate(
                        itemQuery.offset(criteria.getOffset()).limit(PAGE_SIZE).list().stream().map(item ->
                                tag("a", "href=" + quote(
                                        configuration.getPublicUrl() +
                                                (user == null ?
                                                        CloudSpillApi.getPublicImagePageUrl(item) :
                                                        CloudSpillApi.getLoggedInImagePageUrl(item))),
                                        unclosedTag("img class='thumb' src=" +
                                                quote(configuration.getPublicUrl() + CloudSpillApi.getThumbnailUrl(item, CloudSpillApi.Size.IMAGE_THUMBNAIL))))
                        ).toArray(HtmlFragment[]::new)),
                pageLink(pageNumber + 1, ">", totalCount));
    }

    private HtmlFragment pageLink(int pageNumber, String label, long totalCount) {
        int offset = pageNumber * PAGE_SIZE;
        if (offset >= 0 && offset < totalCount) {
            return tag("a", "class='pagerLink' href="+ quote(configuration.getPublicUrl() + criteria.atOffset(offset).getUrl()),
                    label);
        } else {
            return HtmlFragment.EMPTY;
        }
    }
}
