package org.gamboni.cloudspill.server.query;

import com.google.common.collect.ImmutableSet;

import org.gamboni.cloudspill.domain.BackendItem;
import org.gamboni.cloudspill.domain.BackendItem_;
import org.gamboni.cloudspill.domain.CloudSpillEntityManagerDomain;
import org.gamboni.cloudspill.domain.ServerDomain;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.query.QueryRange;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import javax.persistence.criteria.CriteriaBuilder;

/**
 * @author tendays
 */
public class ServerSearchCriteria implements Java8SearchCriteria<BackendItem> {
    public static final ServerSearchCriteria ALL =
            new ServerSearchCriteria(null, null, null, ImmutableSet.of(), null, null, null, QueryRange.ALL);

    private final LocalDate from, to;
    private final String user;
    private final ImmutableSet<String> tags;
    private final Long relativeTo;
    private final QueryRange range;
    private final Long minId;
    private final Instant minModDate;

    private ServerSearchCriteria(LocalDate from, LocalDate to, String user, Set<String> tags, Long minId, Instant minModDate,
                                 Long relativeTo, QueryRange range) {
        this.tags = ImmutableSet.copyOf(tags);
        this.from = from;
        this.to = to;
        this.user = user;
        this.minId = minId;
        this.minModDate = minModDate;
        this.relativeTo = relativeTo;
        this.range = range;
    }

    @Override
    public ImmutableSet<String> getTags() {
        return tags;
    }

    @Override
    public LocalDate getFrom() {
        return from;
    }

    @Override
    public LocalDate getTo() {
        return to;
    }

    @Override
    public String getUser() {
        return user;
    }

    @Override
    public String getStringFrom() {
        return (from == null) ? null : from.toString();
    }

    @Override
    public String getStringTo() {
        return (to == null) ? null : to.toString();
    }

    @Override
    public QueryRange getRange() {
        return range;
    }

    @Override
    public Long getRelativeTo() { return relativeTo; }

    @Override
    public ServerSearchCriteria relativeTo(Long itemId) {
        return new ServerSearchCriteria(from, to, user, tags, minId, minModDate, itemId, range);
    }

    @Override
    public Java8SearchCriteria<BackendItem> withRange(QueryRange newRange) {
        return new ServerSearchCriteria(from, to, user, tags, minId, minModDate, relativeTo, newRange);
    }

    public ServerSearchCriteria withIdAtLeast(long minId) {
        return new ServerSearchCriteria(from, to, user, tags, minId, minModDate, relativeTo, range);
    }

    public ServerSearchCriteria withTag(String tag) {
        return new ServerSearchCriteria(from, to, user,
                ImmutableSet.<String>builder().addAll(tags).add(tag).build(),
                minId, minModDate, relativeTo, range);
    }

    public ServerSearchCriteria at(LocalDate day) {
        return new ServerSearchCriteria(day, day, user, tags, minId, minModDate, relativeTo, range);
    }

    public ServerSearchCriteria modifiedSince(Instant minModDate) {
        return new ServerSearchCriteria(from, to, user, tags, minId, minModDate, relativeTo, range);
    }

    @Override
    public CloudSpillEntityManagerDomain.Ordering<? super BackendItem> getOrder() {
        if (minModDate != null) {
            return CloudSpillEntityManagerDomain.Ordering.asc(BackendItem_.updated);
        } else {
            return Java8SearchCriteria.super.getOrder();
        }
    }

    @Override
    public <E extends BackendItem, Q extends ServerDomain.Query<E>> Q applyTo(Q itemQuery, ItemCredentials credentials) {
        Java8SearchCriteria.super.applyTo(itemQuery, credentials);
        CriteriaBuilder criteriaBuilder = itemQuery.getCriteriaBuilder();
        if (minId != null) {
            itemQuery.add(root -> criteriaBuilder.gt(root.get("id"), minId));
        }
        if (minModDate != null) {
            itemQuery.add(root -> criteriaBuilder.greaterThanOrEqualTo(root.get(BackendItem_.updated), minModDate));
        }
        return itemQuery;
    }
}
