package org.gamboni.cloudspill.server.query;

import org.gamboni.cloudspill.domain.BackendItem;
import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.domain.ServerDomain;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;

import java.util.List;

/**
 * @author tendays
 */
public class LocalItemSet implements ItemSet {
    private final Java8SearchCriteria<BackendItem> criteria;
    private final ServerDomain domain;
    public LocalItemSet(Java8SearchCriteria<BackendItem> criteria, ServerDomain domain) {
        this.criteria = criteria;
        this.domain = domain;
    }

    @Override
    public String getTitle() {
        return criteria.buildTitle();
    }

    @Override
    public String getDescription() {
        return criteria.getDescription();
    }

    @Override
    public int getOffset() {
        return criteria.getOffset();
    }

    @Override
    public ItemSet atOffset(int newOffset) {
        return new LocalItemSet(criteria.atOffset(newOffset), domain);
    }

    @Override
    public long itemCount() {
        return criteria.applyTo(domain.selectItem()).getTotalCount();
    }

    @Override
    public List<Item> getSlice(int limit) {
        return criteria.applyTo(domain.selectItem()).limit(limit).list();
    }

    @Override
    public String getUrl(CloudSpillApi api) {
        return criteria.getUrl(api);
    }

    @Override
    public List<Item> getAllItems() {
        return criteria.applyTo(domain.selectItem()).list();
    }
}
