package org.gamboni.cloudspill.domain;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

/**
 * @author tendays
 */
@Entity
public class UserAuthToken {
    private long id;
    private String value;
    private Boolean valid;
    private String description;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    /** Salted form of the secret string to use to authenticate. The secret is server-generated. */
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    /** True if the token has been validated by an already authenticated session. */
    public Boolean getValid() {
        return valid;
    }

    public void setValid(Boolean valid) {
        this.valid = valid;
    }

    /** Description provided by the user when creating the token. */
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
