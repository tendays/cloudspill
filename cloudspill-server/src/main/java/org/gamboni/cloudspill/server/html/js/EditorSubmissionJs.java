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
            remoteApi.setItemDescription("${"+ id +"}", send(description, () -> {
                appendLine("console.log('Saved description successfully');");
                // should use a 'busy' css class like with tags
            }));
        });
    }
}
