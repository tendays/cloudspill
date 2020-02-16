package org.gamboni.cloudspill.shared.domain;

import java.util.Set;

/**
 * @author tendays
 */
public interface IsItem {
    Long getServerId();
    String getChecksum();
    Set<String> getTags();
}
