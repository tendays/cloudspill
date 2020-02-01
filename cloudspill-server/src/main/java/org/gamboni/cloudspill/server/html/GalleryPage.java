package org.gamboni.cloudspill.server.html;

import com.google.common.base.CaseFormat;
import com.google.common.collect.Streams;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.server.ServerConfiguration;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.hibernate.persister.collection.CollectionPropertyNames;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author tendays
 */
public class GalleryPage extends AbstractPage {
    private final Domain domain;
    private final SearchCriteria criteria;

    public GalleryPage(ServerConfiguration configuration, Domain domain, SearchCriteria criteria) {
        super(configuration, configuration.getCss());

        this.domain = domain;
        this.criteria = criteria;
    }

    @Override
    protected String getTitle() {
        Stream<String> day = (criteria.getFrom() != null && criteria.getFrom().equals(criteria.getTo())) ?
                Stream.of(criteria.getFrom().toString()) : Stream.empty();
        return Streams.concat(
                day,
                criteria.getTags().stream())
                .map(t -> CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, t))
        .collect(Collectors.joining(" "))
        + " Photos";
    }

    @Override
    protected String getPageUrl(User user) {
        return getGalleryUrl(criteria);
    }

    @Override
    protected String getBody(User user) {
        final Domain.Query<Item> itemQuery = domain.selectItem().addOrder(Order.desc("date"));

        int counter=1;
        for (String tag : criteria.getTags()) {
            itemQuery.add(Restrictions.eq(
                    itemQuery.alias("tags", "t" + (counter++)) + "." + CollectionPropertyNames.COLLECTION_ELEMENTS,
                    tag));
        }
        if (criteria.getFrom() != null) {
            itemQuery.add(Restrictions.ge("date", criteria.getFrom().atStartOfDay()));
        }
        if (criteria.getTo() != null) {
            itemQuery.add(Restrictions.lt("date", criteria.getTo().plusDays(1).atStartOfDay()));
        }
        return itemQuery.list().stream().map(item ->
                tag("a href="+ quote(ImagePage.getUrl(configuration, item, user)),
            unclosedTag("img class='image' src="+ quote(getThumbnailUrl(item))))
        ).collect(Collectors.joining());
    }
}
