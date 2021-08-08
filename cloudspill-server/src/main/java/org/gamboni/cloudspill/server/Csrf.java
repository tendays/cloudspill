package org.gamboni.cloudspill.server;

import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.shared.util.Log;

import spark.Request;
import spark.Response;

import static org.gamboni.cloudspill.server.Security.newRandomString;

/**
 * @author tendays
 */
public abstract class Csrf {
    public static final String COOKIE = "cloudspill";
    public static final String HEADER = "X-Cloudspill-Csrf";
    private static final ThreadLocal<Boolean> validCsrfCookie = ThreadLocal.withInitial(() -> false);

    public static void handleRequest(BackendConfiguration configuration, Request req, Response res) {
        final String cookie = req.cookie(COOKIE);
        final boolean validCookie;
        if (cookie == null) {
            Http.setCookie(res, configuration, COOKIE, newRandomString(64), Http.HttpOnlyFlag.UNSET);
            validCookie = false; // missing cookie
        } else {
            final String header = req.headers(HEADER);
            if (header != null) {
                if (header.equals(cookie)) {
                    validCookie = true;
                    Log.info("Received valid CSRF cookie");
                } else {
                    Log.error("Received invalid CSRF cookie");
                    throw new CloudSpillBackend.BadRequestException();
                }
            } else {
                validCookie = false; // missing header
            }
        }
        validCsrfCookie.set(validCookie);

    }

    public static boolean isValid() {
        return validCsrfCookie.get();
    }
}
