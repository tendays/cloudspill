/**
 * 
 */
package org.gamboni.cloudspill.domain;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;

/**
 * An Item represents a file. Hierarchy is as follows: /user/folder/path where
 * path may contain slashes.
 * 
 * @author tendays
 */
@Entity
public class Item extends BackendItem {
	
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public long getId() {
		return this.id;
	}
	public void setId(long id) {
		this.id = id;
	}
}
