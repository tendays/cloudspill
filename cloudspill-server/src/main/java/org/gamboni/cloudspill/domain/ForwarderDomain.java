package org.gamboni.cloudspill.domain;

import javax.persistence.EntityManager;

/**
 * @author tendays
 */
public class ForwarderDomain extends CloudSpillEntityManagerDomain {

    public ForwarderDomain(EntityManager session) {
        super(session);
    }
}
