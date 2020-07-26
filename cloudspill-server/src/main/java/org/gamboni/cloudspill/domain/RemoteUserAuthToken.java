package org.gamboni.cloudspill.domain;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

/**
 * @author tendays
 */
@Entity(name = "UserAuthToken")
public class RemoteUserAuthToken extends BackendUserAuthToken {

    @Id
    public long getId() {
        return id;
    }
    public void setId(long id) {
        this.id = id;
    }
}
