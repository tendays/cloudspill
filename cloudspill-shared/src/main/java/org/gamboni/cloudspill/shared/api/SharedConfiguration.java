package org.gamboni.cloudspill.shared.api;

/**
 * @author tendays
 */
public interface SharedConfiguration {
    String getPublicUrl();
    /** Set to true to disable Secure session cookie, therefore allowing interception of authentication tokens. */
    boolean insecureAuthentication();
}
