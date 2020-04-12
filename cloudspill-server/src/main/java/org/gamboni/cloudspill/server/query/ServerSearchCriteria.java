package org.gamboni.cloudspill.server.query;

import com.google.common.collect.ImmutableSet;

import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.domain.Item_;
import org.gamboni.cloudspill.domain.ServerDomain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import javax.persistence.criteria.CriteriaBuilder;

/**
 * @author tendays
 */
public class ServerSearchCriteria implements Java8SearchCriteria<Item> {
    public static final ServerSearchCriteria ALL = new ServerSearchCriteria(null, null, null, ImmutableSet.of(), null, null, 0);

    private final LocalDate from, to;
    private final String user;
    private final ImmutableSet<String> tags;
    private final int offset;
    private final Long minId;
    private final Instant minModDate;

    private ServerSearchCriteria(LocalDate from, LocalDate to, String user, Set<String> tags, Long minId, Instant minModDate, int offset) {
        this.tags = ImmutableSet.copyOf(tags);
        this.from = from;
        this.to = to;
        this.user = user;
        this.minId = minId;
        this.minModDate = minModDate;
        this.offset = offset;
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
    public ServerSearchCriteria atOffset(int newOffset) {
        return new ServerSearchCriteria(from, to, user, tags, minId, minModDate, newOffset);
    }

    public ServerSearchCriteria withIdAtLeast(long minId) {
        return new ServerSearchCriteria(from, to, user, tags, minId, minModDate, offset);
    }

    public ServerSearchCriteria withTag(String tag) {
        return new ServerSearchCriteria(from, to, user,
                ImmutableSet.<String>builder().addAll(tags).add(tag).build(),
                minId, minModDate, offset);
    }

    public ServerSearchCriteria at(LocalDate day) {
        return new ServerSearchCriteria(day, day, user, tags, minId, minModDate, offset);
    }

    public ServerSearchCriteria modifiedSince(Instant minModDate) {
        return new ServerSearchCriteria(from, to, user, tags, minId, minModDate, offset);
    }

    @Override
    public <Q extends ServerDomain.Query<Item>> Q applyTo(Q itemQuery) {
        CriteriaBuilder criteriaBuilder = itemQuery.getCriteriaBuilder();
        if (minId != null) {
            itemQuery.add(root -> criteriaBuilder.gt(root.get(Item_.id), minId));
        }
        if (minModDate != null) {
            itemQuery.add(root -> criteriaBuilder.greaterThanOrEqualTo(root.get(Item_.updated), minModDate));
        }
        return itemQuery;
    }
}
