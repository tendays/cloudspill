package org.gamboni.cloudspill.server.html;

import com.google.common.collect.Streams;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author tendays
 */
public class HtmlFragment {
    public static final HtmlFragment EMPTY = new HtmlFragment("");
    private final String text;

    public HtmlFragment(String text) {
        this.text = text;
    }

    public static HtmlFragment escape(String text) {
        return new HtmlFragment(text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;"));
    }

    public static HtmlFragment concatenate(Stream<HtmlFragment> content) {
        return new HtmlFragment(content.map(h -> h.toString()).collect(Collectors.joining()));
    }

    public static HtmlFragment concatenate(HtmlFragment... content) {
        return concatenate(Arrays.stream(content));
    }

    public String toString() {
        return text;
    }
}
