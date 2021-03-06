package org.gamboni.cloudspill.domain;

import org.gamboni.cloudspill.shared.api.Csv;

import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;

/**
 * @author tendays
 */
@MappedSuperclass
class BackendUserAuthToken {

    public static final Csv<UserAuthToken> CSV = new Csv.Impl<UserAuthToken>()
            .add("id", t -> Long.toString(t.getId()), (t, id) -> t.setId(Long.parseLong(id)))
            .add("user", t -> t.getUser().getName(), (t, user) -> t.setUser(User.withName(user)))
            .add("description", t -> t.getDescription(), (t, descr) -> t.setDescription(descr))
            .add("valid", t -> String.valueOf(t.getValid()), (t, valid) -> t.setValid(Boolean.valueOf(valid)));

    protected long id;
    private User user;
    private String value;
    private Boolean valid;
    private String description;

    @ManyToOne(optional = false)
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

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
