package org.gamboni.cloudspill.domain;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;

import org.gamboni.cloudspill.server.query.Java8SearchCriteria;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.Csv;
import org.gamboni.cloudspill.shared.domain.JpaItem;
import org.gamboni.cloudspill.shared.domain.JpaItem_;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Transient;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Root;

/**
 * @author tendays
 */
@Entity
public class GalleryPart implements Java8SearchCriteria<BackendItem> {

    public static final Csv<GalleryPart> CSV = new Csv.Impl<GalleryPart>()
            .add("id", gp -> String.valueOf(gp.getId()), (gp, id) -> gp.setId(Long.parseLong(id)))
            .add("user", gp -> Strings.nullToEmpty(gp.getUser()), (gp, us) -> gp.setUser(Strings.emptyToNull(us)))
            .add("tags", gp -> String.join(",", gp.tags), (gp, tags) -> {
                gp.getTags().clear();
                Splitter.on(',').omitEmptyStrings().split(tags).forEach(gp.getTags()::add);
            })
            .add("from", gp -> (gp.from == null) ? "" : gp.from.toString(),
                    (gp, from) -> gp.setFrom(from.isEmpty() ? null : LocalDate.parse(from)))
            .add("to", gp -> (gp.to == null) ? "" : gp.to.toString(),
                    (gp, to) -> gp.setTo(to.isEmpty() ? null : LocalDate.parse(to)))
            .add("title", GalleryPart::getTitle, GalleryPart::setTitle)
            .add("description", GalleryPart::getDescription, GalleryPart::setDescription);

    private long id;
    private String user;
    private Set<String> tags = new HashSet<>();
    private LocalDate from;
    private LocalDate to;
    private String description;
    private String title;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUser() {
        return user;
    }

    @Override
    @Transient
    public int getOffset() {
        return 0;
    }

    @Override
    @Transient
    public Integer getLimit() {
        return null;
    }

    public void setUser(String user) {
        this.user = user;
    }

    @ElementCollection(fetch = FetchType.LAZY)
    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public LocalDate getFrom() {
        return from;
    }

    @Override
    public LocalDate getTo() {
        return to;
    }

    public void setFrom(LocalDate from) {
        this.from = from;
    }

    public void setTo(LocalDate to) {
        this.to = to;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public String buildTitle() {
        return (getTitle() == null) ? Java8SearchCriteria.super.buildTitle() : getTitle();
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override @Transient public String getUrl(CloudSpillApi api) {
        return api.galleryPart(id, 0, null);
    }

    /** Stored galleries sort from old to new. */
    @Override @Transient public Order getOrder(CriteriaBuilder criteriaBuilder, Root<? extends JpaItem> root) {
        return criteriaBuilder.asc(root.get(JpaItem_.date));
    }

    @Override
    public Java8SearchCriteria<BackendItem> atOffset(int newOffset) {
        return new Slice(newOffset, null);
    }

    @Override
    public Java8SearchCriteria<BackendItem> withLimit(Integer newLimit) {
        return new Slice(0, newLimit);
    }

    private class Slice implements Java8SearchCriteria<BackendItem> {
        final int offset;
        final Integer limit;
        Slice(int offset, Integer limit) {
            this.offset = offset;
            this.limit = limit;
        }

        @Override
        public Java8SearchCriteria<BackendItem> atOffset(int newOffset) {
            return new Slice(newOffset, limit);
        }

        @Override
        public Java8SearchCriteria<BackendItem> withLimit(Integer newLimit) {
            return new Slice(offset, newLimit);
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
        public Set<String> getTags() {
            return tags;
        }

        @Override
        public String getUser() {
            return user;
        }

        @Override
        public String buildTitle() {
            return GalleryPart.this.buildTitle();
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
        public String getUrl(CloudSpillApi api) {
            return api.galleryPart(getId(), offset, limit);
        }
    }
}
