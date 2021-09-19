package org.gamboni.cloudspill.server.html.js;

import com.google.common.collect.ImmutableMap;

import org.gamboni.cloudspill.server.Csrf;
import org.gamboni.cloudspill.server.config.BackendConfiguration;

/**
 * @author tendays
 */
public class CommentSubmissionJs extends AbstractJs {
    public CommentSubmissionJs(BackendConfiguration configuration) {
        super(configuration);
    }

    @Override
    protected void build() {
        function("postComment", () -> {
            String id = param("id");
            String author = param("author");
            String text = param("text");
            String csrfToken = "document.cookie.split('; ').find(c => c.startsWith('" +
                    Csrf.COOKIE + "=')).substring(" + (Csrf.COOKIE.length() + 1) + ")";
            String callback = param("callback");
            remoteApi.postComment("${"+ id +"}", send( stringify(
                    ImmutableMap.of("author", author,
                            "text", text)),
                    req -> {
                        appendLine(callback +"("+ req +")");
                    }).withHeader(Csrf.HEADER, csrfToken));
        });
    }
}
