package org.gamboni.cloudspill.shared.api;

/**
 * @author tendays
 */
public enum LoginState {
    DISCONNECTED,
    INVALID_TOKEN,
    WAITING_FOR_VALIDATION,
    LOGGED_IN
}
