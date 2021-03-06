package org.gamboni.cloudspill.domain;

import org.gamboni.cloudspill.shared.api.Csv;
import org.gamboni.cloudspill.shared.domain.ClientUser;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

/**
 * @author tendays
 */
@Entity
public class UserAuthToken extends BackendUserAuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public long getId() {
        return id;
    }
    public void setId(long id) {
        this.id = id;
    }
}
