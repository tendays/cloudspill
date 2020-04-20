package org.gamboni.cloudspill.server.html;

import org.gamboni.cloudspill.domain.GalleryPart;
import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.Csv;
import org.gamboni.cloudspill.shared.api.ItemCredentials;

import java.util.List;

/**
 * @author tendays
 */
public class GalleryListPage extends AbstractPage {

    private final List<Element> elements;
    private final String title;

    public static class Element {

        public static final Csv<Element> CSV = new Csv.Impl<Element>()
                .embed(GalleryPart.CSV, e -> e.gallery)
                .add("sample", e -> (e.sample == null) ? null : Long.toString(e.sample),
                        (e, sample) -> e.sample = (sample == null) ? null : Long.valueOf(sample))
                .add("sampleKey", e -> e.sampleKey, (e, sampleKey) -> e.sampleKey = sampleKey);

        /** Information about the gallery part for which to show a link (used for Title and Url) */
        private final GalleryPart gallery;
        /** ServerId of the Item to display as a thumbnail for the gallery */
        private Long sample;
        /** Checksum to use to load the sample thumbnail */
        private String sampleKey;

        public Element(GalleryPart gallery, Long sample, String sampleKey) {
            this.gallery = gallery;
            this.sample = sample;
            this.sampleKey = sampleKey;
        }

        public Element(GalleryPart gallery) {
            this.gallery = gallery;
            this.sample = null;
            this.sampleKey = null;
        }
    }

    public GalleryListPage(BackendConfiguration configuration, String title, List<Element> elements) {
        super(configuration);
        this.title = title;
        this.elements = elements;
    }

    @Override
    protected String getTitle() {
        return title;
    }

    @Override
    protected String getPageUrl() {
        return configuration.getPublicUrl() + "public/gallery/";
    }

    @Override
    protected HtmlFragment getBody(ItemCredentials.AuthenticationStatus authStatus) {
        return HtmlFragment.concatenate(elements
                .stream()
                .map(element -> {
                    System.out.println("Loading "+ element.gallery.getUrl(api));

                    final String aAttributes = "class='galleryLink' href="+ quote(element.gallery.getUrl(api));
                    final HtmlFragment span = tag("span", element.gallery.buildTitle());
                    if (element.sample == null) {
                        return tag("a", aAttributes,
                                span);
                    } else {
                        return tag("a", aAttributes,
                                span,
                                unclosedTag("img src="+ quote(
                                                api.getThumbnailUrl(
                                                        element.sample,
                                                        new ItemCredentials.ItemKey(element.sampleKey),
                                                        CloudSpillApi.Size.GALLERY_THUMBNAIL)))
                                );
                    }
                })
                .toArray(HtmlFragment[]::new));
    }
}
