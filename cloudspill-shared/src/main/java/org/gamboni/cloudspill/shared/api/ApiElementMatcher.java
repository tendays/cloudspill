package org.gamboni.cloudspill.shared.api;

/**
 * @author tendays
 */
public interface ApiElementMatcher<T> {
    public enum HttpMethod { GET, POST, PUT}

    void match(HttpMethod method, String url, T consumer);
}
