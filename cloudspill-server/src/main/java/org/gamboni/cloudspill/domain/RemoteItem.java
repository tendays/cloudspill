package org.gamboni.cloudspill.domain;

import java.util.Set;

import javax.persistence.AssociationOverride;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinTable;
import javax.persistence.Table;

/**
 * @author tendays
 */
@Entity(name="Item")
public class RemoteItem extends BackendItem {

    @Id
    public long getId() {
        return id;
    }
    public void setId(long id) {
        this.id = id;
    }
}
