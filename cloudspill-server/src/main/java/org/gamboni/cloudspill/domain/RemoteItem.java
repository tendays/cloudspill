package org.gamboni.cloudspill.domain;

import java.util.Set;

import javax.persistence.AssociationOverride;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinTable;
import javax.persistence.Table;

/**
 * @author tendays
 */
@Entity(name="Item")
public class RemoteItem extends BackendItem {
}
