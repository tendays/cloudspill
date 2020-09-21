package org.gamboni.cloudspill.server.html;

import org.gamboni.cloudspill.shared.api.ItemCredentials;

/**
 * @author tendays
 */
public interface Renderer<T extends OutputModel> {
    HtmlFragment render(T model);
}
