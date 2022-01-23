package org.gamboni.cloudspill.server.html;

import com.google.common.collect.ImmutableList;

import org.gamboni.cloudspill.domain.BackendItem;
import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.server.html.js.EditorSubmissionJs;
import org.gamboni.cloudspill.server.query.ItemSet;
import org.gamboni.cloudspill.server.query.Java8SearchCriteria;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.api.ItemSecurity;
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
            super(ImmutableList.of(credentials));
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
        return model.criteria.getUrl(api).toString();
    }

    @Override
    protected HtmlFragment scripts() {
        return HtmlFragment.concatenate(
                tag("script", "type='text/javascript' src=" + quote(api.tagwidgetJS()), ""),
                tag("script", "type='text/javascript' src="+ quote(api.galleryJS()), ""),
                tag("script", "type='text/javascript' src="+ quote(api.getUrl(new EditorSubmissionJs(configuration))), ""));
    }

    @Override
    protected String onLoad(Model model) {
        // Don't directly pass %s because it would get escaped as %25s
        String fakePlaceholder = "__ITEM_KEY__";
        if (model.itemSet.totalCount > PAGE_SIZE && model.criteria.getRange().offset == 0) {
            final ItemCredentials credentials;
            if (model.getAuthStatus() == ItemCredentials.AuthenticationStatus.LOGGED_IN) {
                credentials = new ItemCredentials.UserPassword();
            } else {
                credentials = model.criteria.getCredentialForPattern(fakePlaceholder);
            }
            return "createPlaceholders('"+ model.criteria.withRange(QueryRange.ALL).getUrl(api) +"', '"+
                    api.getThumbnailUrl("%d",
                            credentials, CloudSpillApi.Size.IMAGE_THUMBNAIL.pixels).replace(fakePlaceholder, "%s") +"', '"+
                    api.getImagePageUrl("%d", model.criteria, credentials).replace(fakePlaceholder, "%s") +"', "+
                    PAGE_SIZE +", "+ model.itemSet.totalCount +")";
        } else {
            return super.onLoad(model);
        }
    }

    @Override
    protected HtmlFragment getBody(Model model) {
        int pageNumber = model.criteria.getRange().offset / PAGE_SIZE;
        return HtmlFragment.concatenate(
                (model.getAuthStatus() == ItemCredentials.AuthenticationStatus.LOGGED_IN ?
                tag("div", "class='toolbar'",
                        tag("div", "class='button' onclick="+ quote("selectionMode('"+
                                api.knownTags()
                                +"')"), "Select"),
                        tag("div", "class='tags editable' style='display:none'", "Tags of selected photos:")
                )
        : HtmlFragment.EMPTY),
                tag("div", "class='description'", model.itemSet.description),
                tag("div", "id='items'",
                pageLink(model, pageNumber - 1, "<", model.itemSet.totalCount),
                HtmlFragment.concatenate(
                        model.itemSet.rows.stream().map(item -> renderItem(model, item)
                        ).toArray(HtmlFragment[]::new)),
                pageLink(model, pageNumber + 1, ">", model.itemSet.totalCount)));
    }

    private HtmlFragment renderItem(Model model, BackendItem item) {
        ItemCredentials credentials = ItemSecurity.getItemCredentials(item, model.getAuthStatus());
        return tag("a", "data-id="+ quote(String.valueOf(item.getServerId())) +" " +
                        "data-tags="+ quote(String.join(",", item.getTags())) +" " +
                        "class='itemLink' href=" + quote(
                                api.getImagePageUrl(item.getServerId(), model.criteria, credentials)),
                unclosedTag("img class='thumb' src=" +
                        quote(api.getThumbnailUrl(item.getServerId(), credentials, CloudSpillApi.Size.IMAGE_THUMBNAIL))));
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
