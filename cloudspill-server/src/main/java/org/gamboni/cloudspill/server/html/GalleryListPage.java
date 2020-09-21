package org.gamboni.cloudspill.server.html;

import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.server.query.Java8SearchCriteria;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.Csv;
import org.gamboni.cloudspill.shared.api.ItemCredentials;

import java.util.List;

/**
 * @author tendays
 */
public class GalleryListPage extends AbstractRenderer<GalleryListPage.Model> {

    public static class Model extends OutputModel {
        public final String title;
        public final List<Element> elements;

        public Model(ItemCredentials credentials, String title, List<Element> elements) {
            super(credentials);
            this.title = title;
            this.elements = elements;
        }
    }

    public static class Element {

        public static final Csv<Element> CSV = new Csv.Impl<Element>()
                .add("relativeUrl", e -> e.relativeUrl, (e, u) -> e.relativeUrl = u)
                .add("title", e -> e.title, (e, title) -> e.title = title)
                /* Temporary backward compatibility */
                .add("id", e -> (e.relativeUrl.startsWith("/gallery/") ? e.relativeUrl.substring("/gallery/".length()) : null),
                        (e, ignore) -> {})
                .add("sample", e -> (e.sample == null) ? null : Long.toString(e.sample),
                        (e, sample) -> e.sample = (sample == null) ? null : Long.valueOf(sample))
                .add("sampleKey", e -> e.sampleKey, (e, sampleKey) -> e.sampleKey = sampleKey);

        private String relativeUrl;
        private String title;
        /** ServerId of the Item to display as a thumbnail for the gallery */
        private Long sample;
        /** Checksum to use to load the sample thumbnail */
        private String sampleKey;

        /** Empty constructor for use when decoding CSV */
        public Element() {}

        public Element(Java8SearchCriteria<?> gallery, Long sample, String sampleKey) {
            this.relativeUrl = gallery.getUrl(new CloudSpillApi("")).substring(1);
            this.title = gallery.buildTitle();
            this.sample = sample;
            this.sampleKey = sampleKey;
        }
    }

    public GalleryListPage(BackendConfiguration configuration) {
        super(configuration);
    }

    @Override
    protected String getTitle(Model model) {
        return model.title;
    }

    @Override
    protected String getPageUrl(Model model) {
        return configuration.getPublicUrl() + "public/gallery/";
    }

    @Override
    protected HtmlFragment getBody(Model model) {
        return HtmlFragment.concatenate(model.elements
                .stream()
                .map(element -> {
                    final String elementUrl = api.getBaseUrl() + element.relativeUrl;
                    System.out.println("Loading " + elementUrl);

                    final String aAttributes = "class='galleryLink' href="+ quote(elementUrl);
                    final HtmlFragment span = tag("span", element.title);
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
