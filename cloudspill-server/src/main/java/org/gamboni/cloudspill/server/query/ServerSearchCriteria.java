package org.gamboni.cloudspill.server.query;

import com.google.common.collect.ImmutableSet;

import org.gamboni.cloudspill.domain.BackendItem;
import org.gamboni.cloudspill.domain.BackendItem_;
import org.gamboni.cloudspill.domain.ServerDomain;
import org.gamboni.cloudspill.shared.api.ItemCredentials;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import javax.persistence.criteria.CriteriaBuilder;

/**
 * @author tendays
 */
public class ServerSearchCriteria implements Java8SearchCriteria<BackendItem> {
    public static final ServerSearchCriteria ALL = new ServerSearchCriteria(null, null, null, ImmutableSet.of(), null, null, 0, null);

    private final LocalDate from, to;
    private final String user;
    private final ImmutableSet<String> tags;
    private final int offset;
    private final Integer limit;
    private final Long minId;
    private final Instant minModDate;

    private ServerSearchCriteria(LocalDate from, LocalDate to, String user, Set<String> tags, Long minId, Instant minModDate, int offset, Integer limit) {
        this.tags = ImmutableSet.copyOf(tags);
        this.from = from;
        this.to = to;
        this.user = user;
        this.minId = minId;
        this.minModDate = minModDate;
        this.offset = offset;
        this.limit = limit;
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
    public int getOffset() {
        return offset;
    }

    @Override
    public Integer getLimit() {
        return limit;
    }

    @Override
    public ServerSearchCriteria atOffset(int newOffset) {
        return new ServerSearchCriteria(from, to, user, tags, minId, minModDate, newOffset, limit);
    }

    @Override
    public Java8SearchCriteria<BackendItem> withLimit(Integer newLimit) {
        return new ServerSearchCriteria(from, to, user, tags, minId, minModDate, offset, newLimit);
    }

    public ServerSearchCriteria withIdAtLeast(long minId) {
        return new ServerSearchCriteria(from, to, user, tags, minId, minModDate, offset, limit);
    }

    public ServerSearchCriteria withTag(String tag) {
        return new ServerSearchCriteria(from, to, user,
                ImmutableSet.<String>builder().addAll(tags).add(tag).build(),
                minId, minModDate, offset, limit);
    }

    public ServerSearchCriteria at(LocalDate day) {
        return new ServerSearchCriteria(day, day, user, tags, minId, minModDate, offset, limit);
    }

    public ServerSearchCriteria modifiedSince(Instant minModDate) {
        return new ServerSearchCriteria(from, to, user, tags, minId, minModDate, offset, limit);
    }

    @Override
    public <E extends BackendItem, Q extends ServerDomain.Query<E>> Q applyTo(Q itemQuery, ItemCredentials.AuthenticationStatus authStatus) {
        Java8SearchCriteria.super.applyTo(itemQuery, authStatus);
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
