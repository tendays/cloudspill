package org.gamboni.cloudspill.server.query;

import com.google.common.collect.ImmutableSet;

import org.gamboni.cloudspill.shared.query.SearchCriteria;

import java.time.LocalDate;
import java.util.Set;

/**
 * @author tendays
 */
public class ServerSearchCriteria implements SearchCriteria {
    private final LocalDate from, to;
    private final ImmutableSet<String> tags;
    private final int offset;

    public ServerSearchCriteria(LocalDate from, LocalDate to, Set<String> tags, int offset) {
        this.tags = ImmutableSet.copyOf(tags);
        this.from = from;
        this.to = to;
        this.offset = offset;
    }

    @Override
    public ImmutableSet<String> getTags() {
        return tags;
    }

    public LocalDate getFrom() {
        return from;
    }

    public LocalDate getTo() {
        return to;
    }

    @Override
    public String getStringFrom() {
        return (from == null) ? null : from.toString();
    }

    @Override
    public String getStringTo() {
        return (to == null) ? null : to.toString();
    }

    public int getOffset() {
        return offset;
    }
}
