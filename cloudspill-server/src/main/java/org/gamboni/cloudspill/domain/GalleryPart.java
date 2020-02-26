package org.gamboni.cloudspill.domain;

import org.gamboni.cloudspill.server.query.Java8SearchCriteria;
import org.gamboni.cloudspill.shared.query.SearchCriteria;

import java.time.LocalDate;
import java.util.Set;

import javax.persistence.Column;
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
public class GalleryPart implements Java8SearchCriteria {
    long id;
    String user;
    Set<String> tags;
    LocalDate from;
    LocalDate to;
    String description;
    String title;

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

    @Override
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override @Transient public String getUrl() {
        return "/gallery/"+ getId();
    }

    @Override
    public Java8SearchCriteria atOffset(int newOffset) {
        return new AtOffset(newOffset);
    }

    private class AtOffset implements Java8SearchCriteria {
        final int offset;
        AtOffset(int offset) {
            this.offset = offset;
        }

        @Override
        public Java8SearchCriteria atOffset(int newOffset) {
            return new AtOffset(newOffset);
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
        public String getTitle() {
            return title;
        }

        @Override
        public int getOffset() {
            return offset;
        }

        @Override
        public String getUrl() {
            return GalleryPart.this.getUrl() + "?offset="+ getOffset();
        }
    }
}
