package org.gamboni.cloudspill.server.html.js;

import org.gamboni.cloudspill.server.config.BackendConfiguration;

/**
 * @author tendays
 */
public class TokenValidationJs extends AbstractJs {
    public TokenValidationJs(BackendConfiguration configuration) {
        super(configuration);
    }

    @Override
    protected void build() {
        function("validateToken", () -> {
            String name = param("name");
            String id = param("id");
            String callback = param("callback");
            remoteApi.validateToken("${"+ name +"}", "${"+ id +"}", send("", () -> {
                appendLine(callback +"();");
            }));
        });
        function("deleteToken", () -> {
            String name = param("name");
            String id = param("id");
            String callback = param("callback");
            remoteApi.deleteToken("${"+ name +"}", "${"+ id +"}", send("", () -> {
                appendLine(callback +"();");
            }));
        });
    }
}
