package org.gamboni.cloudspill.server.html;

import com.google.common.base.CaseFormat;
import com.google.common.collect.Streams;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.server.ServerConfiguration;
import org.gamboni.cloudspill.server.query.Java8SearchCriteria;
import org.gamboni.cloudspill.server.query.ServerSearchCriteria;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.query.SearchCriteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.hibernate.persister.collection.CollectionPropertyNames;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gamboni.cloudspill.shared.api.CloudSpillApi.getGalleryUrl;

/**
 * @author tendays
 */
public class GalleryPage extends AbstractPage {

    /* 60 is the smallest multiple of 2, 3, 4, 5 and 6. So as long as there are six or fewer images per row, the last result row will be full */
    private static final int PAGE_SIZE = 60;
    private final Domain domain;
    private final Java8SearchCriteria criteria;

    public GalleryPage(ServerConfiguration configuration, Domain domain, Java8SearchCriteria criteria) {
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
        return configuration.getPublicUrl() + getGalleryUrl(criteria);
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

        int pageNumber = criteria.getOffset() / PAGE_SIZE;
        long totalCount = itemQuery.getTotalCount();
        return pageLink(pageNumber - 1, "&lt;", totalCount) +
                itemQuery.offset(criteria.getOffset()).limit(PAGE_SIZE).list().stream().map(item ->
                tag("a", "href="+ quote(
                        configuration.getPublicUrl() +
                                (user == null ?
                                CloudSpillApi.getPublicImagePageUrl(item) :
                        CloudSpillApi.getLoggedInImagePageUrl(item))),
            unclosedTag("img class='thumb' src="+ quote(configuration.getPublicUrl() + CloudSpillApi.getThumbnailUrl(item))))
        ).collect(Collectors.joining()) +
                pageLink(pageNumber + 1, "&gt;", totalCount);
    }

    private String pageLink(int pageNumber, String label, long totalCount) {
        int offset = pageNumber * PAGE_SIZE;
        if (offset >= 0 && offset < totalCount) {
            return "<a class='pagerLink' href='"+ configuration.getPublicUrl() + CloudSpillApi.getGalleryUrl(criteria.atOffset(offset)) +"'>"+ label +"</a>";
        } else {
            return "";
        }
    }
}
