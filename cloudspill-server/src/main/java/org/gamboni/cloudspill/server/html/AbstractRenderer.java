package org.gamboni.cloudspill.server.html;

import com.google.common.base.Stopwatch;

import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.util.UrlStringBuilder;

import java.util.Optional;

import static org.gamboni.cloudspill.server.html.HtmlFragment.escape;

/**
 * @author tendays
 */
public abstract class AbstractRenderer<T extends OutputModel> implements Renderer<T> {

    private static final ThreadLocal<Stopwatch> requestStart = new ThreadLocal<>();
    public static void recordRequestStart() {
        requestStart.set(Stopwatch.createStarted());
    }

    public static void clearRequestStopwatch() {
        requestStart.remove();
    }

    protected static HtmlFragment tag(String name, String attributes, String content) {
        return tag(name, attributes, escape(content));
    }

    protected static HtmlFragment tag(String name, String attributes, HtmlFragment... content) {
        return new HtmlFragment("<" + name + " " + attributes + ">" + HtmlFragment.concatenate(content) + "</" + name + ">");
    }

    protected static HtmlFragment tag(String name, String content) {
        return tag(name, escape(content));
    }

    protected static HtmlFragment tag(String name, HtmlFragment... content) {
        return new HtmlFragment("<" + name + ">" + HtmlFragment.concatenate(content) + "</" + name + ">");
    }

    protected static HtmlFragment slashedTag(String name) {
        return new HtmlFragment("<" + name + "/>");
    }

    protected static HtmlFragment unclosedTag(String name) {
        return new HtmlFragment("<" + name + ">");
    }

    protected static HtmlFragment meta(String property, String content) {
        return slashedTag("meta property=" + quote(property) + " content=" + quote(content));
    }

    protected static String quote(UrlStringBuilder url) {
        return quote(url.toString());
    }

    protected static String quote(String text) {
        return "\"" + text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace("\"", "&quot;")
                + "\"";
    }

    protected HtmlFragment button(String id, String label, String onClick) {
        return tag("div", "class='button' id='"+ id + "' onclick="+
                quote(onClick), label);
    }

    protected final BackendConfiguration configuration;
    protected final CloudSpillApi api;

    protected AbstractRenderer(BackendConfiguration configuration) {
        this.configuration = configuration;
        this.api = new CloudSpillApi(configuration.getPublicUrl());
    }

    protected abstract String getTitle(T model);

    protected abstract String getPageUrl(T model);

    protected abstract HtmlFragment getBody(T model);

    protected Optional<String> getThumbnailUrl(T model) {
        return Optional.empty();
    }

    public HtmlFragment render(T model) {
        return tag("html", "prefix=\"og: http://ogp.me/ns#\"",
                tag("head",
                        slashedTag("meta name='robots' content='noindex'"),
                        tag("title", getTitle(model)),
                        meta("og:title", getTitle(model)),
                        meta("og:type", "article"),
                        meta("og:url", getPageUrl(model)),
                        getThumbnailUrl(model).map(url -> meta("og:image", url)).orElse(HtmlFragment.EMPTY),
                        slashedTag("link rel=\"stylesheet\" type=\"text/css\" href=" +
                                quote(api.css())),
                        (model.getAuthStatus() == ItemCredentials.AuthenticationStatus.LOGGED_IN ?
                                tag("script", "type='text/javascript' src=" + quote(api.uploadJS()), "") : HtmlFragment.EMPTY),
                        scripts()
                ),
                tag("body", bodyAttributes(model),
                        tag("h1", getTitle(model)),
                        getBody(model),
                        tag("div", "id='drawer' class='drawer' style='display:none'",
                                tag("div", "class='drawer-title'", "Newly added items")),
                        tag("div", "class='copyright'", copyrightNotice(model)),
                        tag("div", "class='debug'", "Page rendered in " + requestStart.get())));
    }

    protected String copyrightNotice(T model) {
        return configuration.copyrightNotice();
    }

    private String bodyAttributes(T model) {
        String onLoad = onLoad(model);
        if (model.getAuthStatus() == ItemCredentials.AuthenticationStatus.LOGGED_IN) {
            onLoad += (onLoad.isEmpty() ? "" : "; ") + "setupDnd('"+
                    /* imageUrlPattern, hrefPattern */
                    api.getThumbnailUrl("%d", new ItemCredentials.UserPassword(), CloudSpillApi.Size.GALLERY_THUMBNAIL) +"', '"+
                    api.getImagePageUrl("%d", null, new ItemCredentials.UserPassword())
                    +"')";
        }
        return onLoad.isEmpty() ? "" : "onLoad="+ quote(onLoad);
    }

    protected String onLoad(T model) {
        return "";
    }

    protected HtmlFragment scripts() {
        return HtmlFragment.EMPTY;
    }
}
