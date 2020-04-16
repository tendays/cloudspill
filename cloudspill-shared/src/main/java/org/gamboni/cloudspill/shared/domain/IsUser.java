package org.gamboni.cloudspill.shared.domain;

/**
 * @author tendays
 */
public interface IsUser {
    String getName();
    boolean verifyPassword(String password);
}
