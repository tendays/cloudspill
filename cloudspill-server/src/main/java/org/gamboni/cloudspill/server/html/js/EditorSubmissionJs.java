package org.gamboni.cloudspill.server.html.js;

import org.gamboni.cloudspill.server.config.BackendConfiguration;

/**
 * @author tendays
 */
public class EditorSubmissionJs extends AbstractJs {
    public EditorSubmissionJs(BackendConfiguration configuration) {
        super(configuration);
    }

    protected void build() {
        function("saveDescription", () -> {
            String id = param("id");
            String description = param("description");
            String callback = param("callback");
            remoteApi.setItemDescription("${"+ id +"}", send(description, () -> {
                appendLine(callback +"();");
            }));
        });
    }
}
