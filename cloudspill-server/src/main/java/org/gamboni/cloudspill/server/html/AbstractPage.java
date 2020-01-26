package org.gamboni.cloudspill.server.html;

import java.util.Optional;

/**
 * @author tendays
 */
public abstract class AbstractPage {
    protected static String tag(String name, String attributes, String content) {
        return "<" + name + " " + attributes + ">" + content + "</" + name + ">";
    }

    protected static String tag(String name, String content) {
        return "<" + name + ">" + content + "</" + name + ">";
    }

    protected static String slashedTag(String name) {
        return "<" + name + "/>";
    }

    protected static String unclosedTag(String name) {
        return "<" + name + ">";
    }

    protected static String meta(String property, String content) {
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

    protected AbstractPage(String css) {
        this.css = css;
    }

    protected abstract String getTitle();

    protected abstract String getPageUrl();

    protected abstract String getBody();

    protected Optional<String> getThumbnailUrl() {
        return Optional.empty();
    }

    public String getHtml() {
        return tag("html", "prefix=\"og: http://ogp.me/ns#\"",
                tag("head",
                        tag("title", getTitle()) +
                                meta("og:title", getTitle()) +
                                meta("og:type", "article") +
                                meta("og:url", getPageUrl()) +
                                getThumbnailUrl().map(url -> meta("og:image", url)).orElse("") +
                                slashedTag("link rel=\"stylesheet\" type=\"text/css\" href=" +
                                        quote(css))
                ) +
                        tag("body",
                                tag("h1", "", getTitle()) +
                                getBody()));
    }

}