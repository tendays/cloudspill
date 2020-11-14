package org.gamboni.cloudspill.server.html;

import org.gamboni.cloudspill.domain.BackendItem;
import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.server.query.ItemSet;
import org.gamboni.cloudspill.server.query.Java8SearchCriteria;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.query.QueryRange;

/**
 * @author tendays
 */
public class GalleryPage extends AbstractRenderer<GalleryPage.Model> {

    public static class Model extends OutputModel {
        public final Java8SearchCriteria<? extends BackendItem> criteria;
        public final ItemSet itemSet;
        final boolean experimental;

        public Model(ItemCredentials credentials, Java8SearchCriteria<? extends BackendItem> criteria, ItemSet itemSet, boolean experimental) {
            super(credentials);
            this.criteria = criteria;
            this.itemSet = itemSet;
            this.experimental = experimental;
        }
    }

    /* 60 is the smallest multiple of 2, 3, 4, 5 and 6. So as long as there are six or fewer images per row, the last result row will be full */
    public static final int PAGE_SIZE = 60;

    public GalleryPage(BackendConfiguration configuration) {
        super(configuration);
    }

    @Override
    protected String getTitle(Model model) {
        return model.itemSet.title;
    }

    @Override
    protected String getPageUrl(Model model) {
        return model.criteria.getUrl(api);
    }

    @Override
    protected HtmlFragment scripts() {
        return tag("script", "type='text/javascript' src="+ quote(api.lazyLoadJS()), "");
    }

    @Override
    protected String onLoad(Model model) {
        if (model.itemSet.totalCount > PAGE_SIZE && model.criteria.getRange().offset == 0) {
            return "createPlaceholders('"+ model.criteria.getUrl(api) +"', '"+
                    api.getThumbnailUrl("%d", new ItemCredentials.ItemKey("%s"), CloudSpillApi.Size.IMAGE_THUMBNAIL.pixels) +"', '"+
                    api.getImagePageUrl("%d", model.criteria, new ItemCredentials.ItemKey("%s")) +"', "+
                    PAGE_SIZE +", "+ model.itemSet.totalCount +")";
        } else {
            return super.onLoad(model);
        }
    }

    @Override
    protected HtmlFragment getBody(Model model) {
        int pageNumber = model.criteria.getRange().offset / PAGE_SIZE;
        return HtmlFragment.concatenate(
                tag("div", "class='description'", model.itemSet.description),
                pageLink(model, pageNumber - 1, "<", model.itemSet.totalCount),
                HtmlFragment.concatenate(
                        model.itemSet.rows.stream().map(item ->
                                tag("a", "href=" + quote(
                                                        api.getImagePageUrl(item.getServerId(), model.criteria, model.credentials.getAuthStatus().credentialsFor(item))),
                                        unclosedTag("img class='thumb' src=" +
                                                quote(api.getThumbnailUrl(item, CloudSpillApi.Size.IMAGE_THUMBNAIL))))
                        ).toArray(HtmlFragment[]::new)),
                pageLink(model, pageNumber + 1, ">", model.itemSet.totalCount));
    }

    private HtmlFragment pageLink(Model model, int pageNumber, String label, long totalCount) {
        int offset = pageNumber * PAGE_SIZE;
        if (offset >= 0 && offset < totalCount) {

            String id = (label.equals(">")) ? "id='marker' " : "";

            return tag("a", id + "class='pagerLink' href="+ quote(model.criteria.withRange(QueryRange.offset(offset)).getUrl(api)),
                    label);
        } else {
            return HtmlFragment.EMPTY;
        }
    }
}
