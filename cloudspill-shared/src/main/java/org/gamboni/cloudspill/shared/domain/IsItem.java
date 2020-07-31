package org.gamboni.cloudspill.shared.domain;

import java.util.Set;

/**
 * @author tendays
 */
public interface IsItem {
    String getUser();
    Long getServerId();
    String getChecksum();
    Set<String> getTags();
}
