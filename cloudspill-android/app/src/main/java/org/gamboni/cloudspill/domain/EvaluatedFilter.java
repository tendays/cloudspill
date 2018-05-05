package org.gamboni.cloudspill.domain;

import org.gamboni.cloudspill.job.ThumbnailIntentService;

import java.util.List;

/**
 * @author tendays
 */

public class EvaluatedFilter {

    final Domain domain;
    final FilterSpecification filter;
    final List<Domain.Item> itemList;
    final Domain.ItemQuery itemQuery;
    public EvaluatedFilter(Domain domain, FilterSpecification filter) {
        this.domain = domain;
        this.filter = filter;
        this.itemQuery = domain.selectItems();
        if (filter.from != null) {
            itemQuery.ge(Domain.ItemSchema.DATE, filter.from);
        }
        if (filter.to != null) {
            itemQuery.le(Domain.ItemSchema.DATE, filter.to);
        }
        if (filter.by != null) {
            itemQuery.eq(Domain.ItemSchema.USER, filter.by);
        }
        if (!filter.tags.isEmpty()) {
            itemQuery.hasTags(filter.tags);
        }
        this.itemList = apply(itemQuery, filter.sort).list();
    }
    public int size() {
        return itemList.size();
    }
    public Domain.Item getByPosition(int position) {
        return itemList.get(position);
    }
    public Domain.Item getById(long id) {
        return domain.selectItems().eq(Domain.ItemSchema.ID, id).detachedList().get(0);
    }
    public boolean isStale(FilterSpecification currentFilter) {
        return this.filter != currentFilter;
    }
    public void close() {
        itemQuery.close();
    }


    private Domain.Query<Domain.Item> apply(Domain.Query<Domain.Item> query, FilterSpecification.Sort sort) {
        switch (sort) {
            case DATE_ASC:
                return query.orderAsc(Domain.ItemSchema.DATE);
            case DATE_DESC:
                return query.orderDesc(Domain.ItemSchema.DATE);
        }
        throw new UnsupportedOperationException(sort.toString());
    }
}
