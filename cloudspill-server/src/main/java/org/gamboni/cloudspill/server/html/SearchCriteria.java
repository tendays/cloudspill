package org.gamboni.cloudspill.server.html;

import com.google.common.collect.ImmutableSet;

import java.time.LocalDate;
import java.util.Set;

/** TODO: let cloudspill-android's FilterSpecification extend this class, which should be moved to cloudspill-shared.
 *
 * @author tendays
 */
public class SearchCriteria {
    private final ImmutableSet<String> tags;
    private final LocalDate from, to;

    public SearchCriteria(Set<String> tags, LocalDate from, LocalDate to) {
        this.tags = ImmutableSet.copyOf(tags);
        this.from = from;
        this.to = to;
    }

    public ImmutableSet<String> getTags() {
        return tags;
    }

    public LocalDate getFrom() {
        return from;
    }

    public LocalDate getTo() {
        return to;
    }
}
