package org.gamboni.cloudspill.server.html;

import com.google.common.collect.Streams;

import java.util.Arrays;
import java.util.stream.Collectors;

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

    public static HtmlFragment concatenate(HtmlFragment... content) {
        return new HtmlFragment(Arrays.stream(content).map(h -> h.toString()).collect(Collectors.joining()));
    }

    public String toString() {
        return text;
    }
}
