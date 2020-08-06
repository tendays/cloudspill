package org.gamboni.cloudspill.server.html;

import com.google.common.base.Stopwatch;

import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.server.config.ServerConfiguration;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.ItemCredentials;

import java.util.Optional;

import static org.gamboni.cloudspill.server.html.HtmlFragment.escape;

/**
 * @author tendays
 */
public abstract class AbstractPage {

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

    protected static String quote(String text) {
        return "\"" + text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace("\"", "&quot;")
                + "\"";
    }

    protected final BackendConfiguration configuration;
    protected final CloudSpillApi api;
    protected final ItemCredentials credentials;

    protected AbstractPage(BackendConfiguration configuration, ItemCredentials credentials) {
        this.configuration = configuration;
        this.api = new CloudSpillApi(configuration.getPublicUrl());
        this.credentials = credentials;
    }

    protected abstract String getTitle();

    protected abstract String getPageUrl();

    protected abstract HtmlFragment getBody(ItemCredentials.AuthenticationStatus authStatus);

    protected Optional<String> getThumbnailUrl() {
        return Optional.empty();
    }

    public HtmlFragment getHtml() {
        return tag("html", "prefix=\"og: http://ogp.me/ns#\"",
                tag("head",
                        slashedTag("meta name='robots' content='noindex'"),
                        tag("title", getTitle()),
                        meta("og:title", getTitle()),
                        meta("og:type", "article"),
                        meta("og:url", getPageUrl()),
                        getThumbnailUrl().map(url -> meta("og:image", url)).orElse(HtmlFragment.EMPTY),
                        slashedTag("link rel=\"stylesheet\" type=\"text/css\" href=" +
                                quote(api.css())),
                        (credentials.getAuthStatus() == ItemCredentials.AuthenticationStatus.LOGGED_IN ?
                        tag("script", "type='text/javascript' src=" + quote(api.uploadJS()), "") : HtmlFragment.EMPTY),
                        scripts()
                ),
                tag("body", bodyAttributes(credentials),
                        tag("h1", getTitle()),
                        getBody(credentials.getAuthStatus()),
                        tag("div", "id='drawer' class='drawer' style='display:none'",
                                tag("div", "class='drawer-title'", "Newly added items")),
                        tag("div", "class='debug'", "Page rendered in " + requestStart.get())));
    }

    private String bodyAttributes(ItemCredentials user) {
        String onLoad = onLoad(user);
        if (user.getAuthStatus() == ItemCredentials.AuthenticationStatus.LOGGED_IN) {
            onLoad += (onLoad.isEmpty() ? "" : "; ") + "setupDnd('"+
                    /* imageUrlPattern, hrefPattern */
                    api.getThumbnailUrl("%d", user, CloudSpillApi.Size.GALLERY_THUMBNAIL) +"', '"+
                    api.getImagePageUrl("%d", user)
                    +"')";
        }
        return onLoad.isEmpty() ? "" : "onLoad="+ quote(onLoad);
    }

    protected String onLoad(ItemCredentials user) {
        return "";
    }

    protected HtmlFragment scripts() {
        return HtmlFragment.EMPTY;
    }

    public String toString() {
        return getHtml().toString();
    }
}