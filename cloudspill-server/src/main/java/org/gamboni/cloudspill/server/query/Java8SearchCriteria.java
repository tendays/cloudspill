package org.gamboni.cloudspill.server.query;

import com.google.common.base.CaseFormat;
import com.google.common.collect.Streams;

import org.gamboni.cloudspill.domain.ServerDomain;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.domain.JpaItem;
import org.gamboni.cloudspill.shared.domain.JpaItem_;
import org.gamboni.cloudspill.shared.query.SearchCriteria;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.SetAttribute;

/** {@link SearchCriteria} extended with Java 8 features like java.time or default methods.
 *
 * @author tendays
 */
public interface Java8SearchCriteria<T extends JpaItem> extends GalleryRequest {
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

    Java8SearchCriteria<T> atOffset(int newOffset);

    Java8SearchCriteria<T> withLimit(Integer newLimit);

    default String buildTitle() {
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

    default String getUrl(CloudSpillApi api) {
        return api.getGalleryUrl(getTags(), getStringFrom(), getStringTo(), getOffset(), getLimit());
    }

    default Order getOrder(CriteriaBuilder criteriaBuilder, Root<? extends JpaItem> root) {
        return criteriaBuilder.desc(root.get(JpaItem_.date));
    }

    default <E extends T, Q extends ServerDomain.Query<E>> Q applyTo(Q itemQuery, ItemCredentials.AuthenticationStatus authStatus) {
        CriteriaBuilder criteriaBuilder = itemQuery.getCriteriaBuilder();
        itemQuery.addOrder(root -> getOrder(criteriaBuilder, root));

        for (String tag : getTags()) {
            itemQuery.add(root -> tagQuery(criteriaBuilder, tag, root));
        }
        if (authStatus != ItemCredentials.AuthenticationStatus.LOGGED_IN) {
            itemQuery.add(root -> tagQuery(criteriaBuilder, "public", root));
        }
        if (getFrom() != null) {
            itemQuery.add(root -> criteriaBuilder.greaterThanOrEqualTo(root.get(JpaItem_.date),
                    getFrom().atStartOfDay()));
        }
        if (getTo() != null) {
            itemQuery.add(root -> criteriaBuilder.lessThanOrEqualTo(root.get(JpaItem_.date),
                    getTo().plusDays(1).atStartOfDay()));
        }
        return itemQuery;
    }

    default <E extends T> Predicate tagQuery(CriteriaBuilder criteriaBuilder, String tag, Root<E> root) {
        @SuppressWarnings("unchecked")// why isn't get(PluralAttribute) contravariant on root type?
                Expression<Set<String>> tagPath = root.get(
                (SetAttribute<E, String>)(SetAttribute) JpaItem_.tags);
        return criteriaBuilder.isMember(tag, tagPath);
    }
}
