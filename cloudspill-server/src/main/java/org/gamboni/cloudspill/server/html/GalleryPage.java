package org.gamboni.cloudspill.server.html;

import com.google.common.collect.Iterables;

import org.gamboni.cloudspill.domain.Domain;
import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.server.ServerConfiguration;
import org.hibernate.criterion.Restrictions;

import java.util.stream.Collectors;

import javax.persistence.criteria.JoinType;

/**
 * @author tendays
 */
public class GalleryPage extends AbstractPage {
    private final ServerConfiguration config;
    private final Domain domain;
    private final SearchCriteria criteria;

    public GalleryPage(ServerConfiguration config, Domain domain, SearchCriteria criteria) {
        super(config.getCss());

        this.config = config;
        this.domain = domain;
        this.criteria = criteria;
    }

    @Override
    protected String getTitle() {
        return "Photos"; // TODO take criteria into account
    }

    @Override
    protected String getPageUrl() {
        return config.getPublicUrl(); // TODO take criteria into account
    }

    @Override
    protected String getBody() {
        final Domain.Query<Item> itemQuery = domain.selectItem();
        //itemQuery.join("tags");
        itemQuery.add(Restrictions.eq("tags", Iterables.getOnlyElement(criteria.getTags())));
        return itemQuery.list().stream().map(Item::getPath).collect(Collectors.joining("<br>"));
    }
}
