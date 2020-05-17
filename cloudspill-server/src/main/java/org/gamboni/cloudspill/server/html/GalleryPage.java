package org.gamboni.cloudspill.server.html;

import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.server.query.GalleryRequest;
import org.gamboni.cloudspill.server.query.ItemSet;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.ItemCredentials;

/**
 * @author tendays
 */
public class GalleryPage extends AbstractPage {

    /* 60 is the smallest multiple of 2, 3, 4, 5 and 6. So as long as there are six or fewer images per row, the last result row will be full */
    public static final int PAGE_SIZE = 60;
    private final GalleryRequest criteria;
    private final ItemSet itemSet;
    private final boolean experimental;

    public GalleryPage(BackendConfiguration configuration, GalleryRequest criteria, ItemSet itemSet, boolean experimental) {
        super(configuration);
        this.criteria = criteria;
        this.itemSet = itemSet;
        this.experimental = experimental;
    }

    @Override
    protected String getTitle() {
        return itemSet.title;
    }

    @Override
    protected String getPageUrl() {
        return criteria.getUrl(api);
    }

    @Override
    protected HtmlFragment scripts() {
        return tag("script", "type='text/javascript' src="+ quote(api.js()), "");
    }

    @Override
    protected String bodyAttributes() {
        if (itemSet.totalCount > PAGE_SIZE && criteria.getOffset() == 0) {
            return "onload="+ quote("createPlaceholders('"+ criteria.getUrl(api) +"', '"+
                    api.getThumbnailUrl("%d", new ItemCredentials.ItemKey("%s"), CloudSpillApi.Size.IMAGE_THUMBNAIL.pixels) +"', '"+
                    api.getImagePageUrl("%d", new ItemCredentials.ItemKey("%s")) +"', "+
                    PAGE_SIZE +", "+ itemSet.totalCount +")");
        } else {
            return super.bodyAttributes();
        }
    }

    @Override
    protected HtmlFragment getBody(ItemCredentials.AuthenticationStatus authStatus) {
        int pageNumber = criteria.getOffset() / PAGE_SIZE;
        return HtmlFragment.concatenate(
                tag("div", "class='description'", itemSet.description),
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

            String id = (label.equals(">")) ? "id='marker' " : "";

            return tag("a", id + "class='pagerLink' href="+ quote(criteria.atOffset(offset).getUrl(api)),
                    label);
        } else {
            return HtmlFragment.EMPTY;
        }
    }
}
