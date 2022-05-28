package org.gamboni.cloudspill.domain;

import org.gamboni.cloudspill.shared.api.Csv;

import java.time.Instant;

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
            .add("description", UserAuthToken::getDescription, UserAuthToken::setDescription)
            .add("valid", t -> String.valueOf(t.getValid()), (t, valid) -> t.setValid(Boolean.valueOf(valid)))
            .add("ip", UserAuthToken::getIp, UserAuthToken::setIp)
            .add("machine", UserAuthToken::getMachine, UserAuthToken::setMachine)
            .add("lastLogin", t -> serialise(t.getLastLogin()), (t, lastLogin) -> t.setLastLogin(deserialise(lastLogin)))
            .add("creationDate", t -> serialise(t.getCreationDate()), (t, lastLogin) -> t.setCreationDate(deserialise(lastLogin)));

    private static String serialise(Instant i) {
        return (i == null) ? "" : i.toString();
    }

    private static Instant deserialise(String i) {
        return i.isEmpty() ? null : Instant.parse(i);
    }

    protected long id;
    private User user;
    private String value;
    private Boolean valid;
    private String description;
    private String ip;
    /** (OS + browser) */
    private String machine;
    private Instant lastLogin;
    private Instant creationDate;

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getMachine() {
        return machine;
    }

    public void setMachine(String machine) {
        this.machine = machine;
    }

    public Instant getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(Instant lastLogin) {
        this.lastLogin = lastLogin;
    }

    public Instant getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Instant creationDate) {
        this.creationDate = creationDate;
    }

    @ManyToOne(optional = false)
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    /** Hashed form of the secret string to use to authenticate. The secret is server-generated. */
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
