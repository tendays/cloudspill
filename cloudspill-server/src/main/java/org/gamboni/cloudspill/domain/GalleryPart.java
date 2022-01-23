package org.gamboni.cloudspill.domain;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;

import org.gamboni.cloudspill.server.query.Java8SearchCriteria;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.Csv;
import org.gamboni.cloudspill.shared.api.ItemCredentials;
import org.gamboni.cloudspill.shared.domain.JpaItem_;
import org.gamboni.cloudspill.shared.query.QueryRange;
import org.gamboni.cloudspill.shared.util.UrlStringBuilder;

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
    private String key;

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
    public QueryRange getRange() {
        return QueryRange.ALL;
    }

    @Override
    @Transient
    public Long getRelativeTo() { return null; }

    public void setUser(String user) {
        this.user = user;
    }

    @ElementCollection(fetch = FetchType.LAZY)
    public Set<String> getTags() {
        return tags;
    }

    @Override
    @Transient
    public Set<String> getEffectiveTags() {
        /* Force keyless galleries to only show public items, even for logged in users */
        Set<String> result = new HashSet<>(this.getTags());
        if (!hasKeyProtection()) {
            result.add("public");
        }
        return result;
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

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    private boolean hasKeyProtection() {
        return this.key != null && !this.key.isEmpty();
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

    @Override @Transient public UrlStringBuilder getUrl(CloudSpillApi api) {
        return api.galleryPart(id, null, null, QueryRange.ALL);
    }

    /** Stored galleries sort from old to new. */
    @Override @Transient public CloudSpillEntityManagerDomain.Ordering<? super BackendItem> getOrder() {
        return CloudSpillEntityManagerDomain.Ordering.asc(JpaItem_.date);
    }

    public Java8SearchCriteria<BackendItem> withKey(String key) {
        return new Slice(key, null, QueryRange.ALL);
    }

    public Java8SearchCriteria<BackendItem> relativeTo(Long relativeTo) {
        return new Slice(null, relativeTo, QueryRange.ALL);
    }

    @Override
    public Java8SearchCriteria<BackendItem> withRange(QueryRange range) {
        return new Slice(null, null, range);
    }

    private class Slice implements Java8SearchCriteria<BackendItem> {
        final String providedKey;
        final Long relativeTo;
        final QueryRange range;
        Slice(String providedKey, Long relativeTo, QueryRange range) {
            this.providedKey = providedKey;
            this.relativeTo = relativeTo;
            this.range = range;
        }

        @Override
        public Java8SearchCriteria<BackendItem> relativeTo(Long relativeTo) {
            return new Slice(providedKey, relativeTo, range);
        }

        @Override
        public Java8SearchCriteria<BackendItem> withRange(QueryRange newRange) {
            return new Slice(providedKey, relativeTo, newRange);
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
        public Set<String> getEffectiveTags() {
            return GalleryPart.this.getEffectiveTags();
        }

        @Override
        public <E extends BackendItem, Q extends ServerDomain.Query<E>> void applyGeneralSecurity(Q itemQuery, ItemCredentials credentials) {
            // Disable general security checks if the part has a key and the provided one matches,
            // or if the user is logged in.

            // Assumption here is that if all pictures in a gallery are accessible to some people with a password,
            // those pictures can also safely be shared with all pictures who have an account.
            if (hasKeyProtection() &&
                    (GalleryPart.this.key.equals(this.providedKey) || credentials.getAuthStatus() == ItemCredentials.AuthenticationStatus.LOGGED_IN)) {
                return;
            }

            GalleryPart.this.applyGeneralSecurity(itemQuery, credentials);
        }

        @Override
        public String getUser() {
            return user;
        }

        @Override
        public CloudSpillEntityManagerDomain.Ordering<? super BackendItem> getOrder() {
            return GalleryPart.this.getOrder();
        }

        @Override
        public String buildTitle() {
            return GalleryPart.this.buildTitle();
        }

        @Override
        public String getDescription() {
            return GalleryPart.this.getDescription();
        }

        @Override
        public Long getRelativeTo() { return relativeTo; }

        @Override
        public QueryRange getRange() {
            return range;
        }

        @Override
        public UrlStringBuilder getUrl(CloudSpillApi api) {
            return api.galleryPart(getId(), providedKey, relativeTo, range);
        }
    }
}
