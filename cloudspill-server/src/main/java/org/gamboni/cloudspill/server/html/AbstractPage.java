package org.gamboni.cloudspill.server.html;

import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.server.ServerConfiguration;

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
    protected final ServerConfiguration configuration;

    protected AbstractPage(ServerConfiguration configuration, String css) {
        this.configuration = configuration;
        this.css = css;
    }

    protected abstract String getTitle();

    protected abstract String getPageUrl(User user);

    protected abstract String getBody(User user);

    protected Optional<String> getThumbnailUrl() {
        return Optional.empty();
    }

    public String getHtml(User user) {
        return tag("html", "prefix=\"og: http://ogp.me/ns#\"",
                tag("head",
                        tag("title", getTitle()) +
                                meta("og:title", getTitle()) +
                                meta("og:type", "article") +
                                meta("og:url", getPageUrl(user)) +
                                getThumbnailUrl().map(url -> meta("og:image", url)).orElse("") +
                                slashedTag("link rel=\"stylesheet\" type=\"text/css\" href=" +
                                        quote(css))
                ) +
                        tag("body",
                                tag("h1", "", getTitle()) +
                                getBody(user)));
    }

}