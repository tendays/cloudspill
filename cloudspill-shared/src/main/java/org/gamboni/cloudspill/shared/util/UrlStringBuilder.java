package org.gamboni.cloudspill.shared.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * @author tendays
 */
public class UrlStringBuilder {
    private final StringBuilder base;
    private final StringBuilder queryParams;

    public UrlStringBuilder(String base) {
        this.base = new StringBuilder(base);
        this.queryParams = new StringBuilder();
    }

    public UrlStringBuilder append(Object part) {
        base.append(part);
        return this;
    }

    public UrlStringBuilder appendQueryParam(String key, Object value) {
        try {
            queryParams.append((queryParams.length() == 0) ? '?' : '&').append(key + "=" +
                    URLEncoder.encode(value.toString(), StandardCharsets.UTF_8.name()));
            return this;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public String toString() {
        return base.toString() + queryParams;
    }
}
