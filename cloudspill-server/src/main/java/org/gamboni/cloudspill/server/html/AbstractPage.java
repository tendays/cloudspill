package org.gamboni.cloudspill.server.html;

import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.server.ServerConfiguration;

import java.util.Optional;

import static org.gamboni.cloudspill.server.html.HtmlFragment.escape;

/**
 * @author tendays
 */
public abstract class AbstractPage {
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

    private final String css;
    protected final ServerConfiguration configuration;

    protected AbstractPage(ServerConfiguration configuration, String css) {
        this.configuration = configuration;
        this.css = css;
    }

    protected abstract String getTitle();

    protected abstract String getPageUrl(User user);

    protected abstract HtmlFragment getBody(User user);

    protected Optional<String> getThumbnailUrl() {
        return Optional.empty();
    }

    public HtmlFragment getHtml(User user) {
        return tag("html", "prefix=\"og: http://ogp.me/ns#\"",
                tag("head",
                        tag("title", getTitle()),
                                meta("og:title", getTitle()),
                                meta("og:type", "article"),
                                meta("og:url", getPageUrl(user)),
                                getThumbnailUrl().map(url -> meta("og:image", url)).orElse(HtmlFragment.EMPTY),
                                slashedTag("link rel=\"stylesheet\" type=\"text/css\" href=" +
                                        quote(css))
                ),
                        tag("body",
                                tag("h1", "", getTitle()),
                                getBody(user)));
    }

}