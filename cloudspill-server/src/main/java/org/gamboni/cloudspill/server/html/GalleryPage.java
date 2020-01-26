package org.gamboni.cloudspill.server.html;

import com.google.common.collect.Iterables;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.server.ServerConfiguration;
import org.hibernate.criterion.Restrictions;
import org.hibernate.persister.collection.CollectionPropertyNames;

import java.util.stream.Collectors;

import javax.persistence.criteria.JoinType;

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
        return "Photos"; // TODO take criteria into account
    }

    @Override
    protected String getPageUrl() {
        return configuration.getPublicUrl(); // TODO take criteria into account
    }

    @Override
    protected String getBody() {
        final Domain.Query<Item> itemQuery = domain.selectItem();

        int counter=1;
        for (String tag : criteria.getTags()) {
            itemQuery.add(Restrictions.eq(
                    itemQuery.alias("tags", "t" + (counter++)) + "." + CollectionPropertyNames.COLLECTION_ELEMENTS,
                    tag));
        }
        return itemQuery.list().stream().map(item ->
            unclosedTag("img class='image' src="+ quote(getThumbnailUrl(item)))
        ).collect(Collectors.joining());
    }
}
