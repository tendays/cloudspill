package org.gamboni.cloudspill.server.query;

import com.google.common.base.CaseFormat;
import com.google.common.collect.Streams;

import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.query.SearchCriteria;

import java.time.LocalDate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** {@link SearchCriteria} extended with Java 8 features like java.time or default methods.
 *
 * @author tendays
 */
public interface Java8SearchCriteria extends SearchCriteria {
    LocalDate getFrom();

    LocalDate getTo();

    @Override
    default String getStringFrom() {
        return (getFrom() == null) ? null : getFrom().toString();
    }

    @Override
    default String getStringTo() {
        return (getTo() == null) ? null : getTo().toString();
    }

    Java8SearchCriteria atOffset(int newOffset);

    default String getTitle() {
        Stream<String> day = (getFrom() != null && getFrom().equals(getTo())) ?
                Stream.of(getFrom().toString()) : Stream.empty();
        return Streams.concat(
                day,
                getTags().stream())
                .map(t -> CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, t))
                .collect(Collectors.joining(" "))
                + " Photos";
    }

    default String getDescription() {
        return "";
    }

    default String getUrl() {
        return CloudSpillApi.getGalleryUrl(this);
    }
}
