package org.gamboni.cloudspill.server;

import org.gamboni.cloudspill.server.config.BackendConfiguration;

import java.time.Duration;

import spark.Response;

/**
 * @author tendays
 */
public abstract class Http {
    public enum HttpOnlyFlag {
        SET, UNSET
    }

    public static void setCookie(Response res, BackendConfiguration configuration, String name, String value, HttpOnlyFlag httpOnly) {
        /* "Lax" SameSite to allow people to link to pages. We don't do state changes in GET requests. */
        res.header("Set-Cookie", name +"="+ value +
                "; Path=/; Max-Age="+ (int) Duration.ofDays(365).getSeconds() +
                (httpOnly == HttpOnlyFlag.UNSET ? "" : "; HttpOnly") +
                (configuration.insecureAuthentication() ? "" : "; Secure")+"; SameSite=Lax");
    }
}
