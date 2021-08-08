package org.gamboni.cloudspill.shared.domain;

import java.time.Instant;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

/**
 * @author tendays
 */
@Entity
public class Comment {
    private Long id;
    private String text;
    private Instant posted;
    private String author;


    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Instant getPosted() {
        return posted;
    }

    public void setPosted(Instant posted) {
        this.posted = posted;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
