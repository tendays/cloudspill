package org.gamboni.cloudspill.server.html.js;

import com.google.common.collect.ImmutableMap;

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

        function("submitMassTagging", () -> {
            String ids = param("ids");
            String tagSpec = param("tagSpec");
            String callback = param("callback");
            remoteApi.putTags(send(stringify(ImmutableMap.of(
                    /* see: MassTagging.java */
                    "ids", ids,
                    "tags", tagSpec
            )), () -> {
                appendLine(callback +"();");
            }));
        });
    }
}
