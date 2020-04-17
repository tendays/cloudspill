package org.gamboni.cloudspill.domain;

import javax.persistence.EntityManager;

/**
 * @author tendays
 */
public class ForwarderDomain extends CloudSpillEntityManagerDomain {

    public ForwarderDomain(EntityManager session) {
        super(session);
    }

    @Override
    public Query<RemoteItem> selectItem() {
        return new Query<>(RemoteItem.class);
    }

}
