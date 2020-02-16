/**
 * 
 */
package org.gamboni.cloudspill.client.domain;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Version;

import org.gamboni.cloudspill.shared.domain.JpaItem;

/**
 * An Item represents a file. Hierarchy is as follows: /user/folder/path where
 * path may contain slashes.
 * 
 * @author tendays
 */
@Entity
public class Item extends JpaItem {
	
	long id;
	Instant updated;

	Long serverId;
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column
	public long getId() {
		return id;
	}
	public void setId(long id) {
		this.id = id;
	}
	
	@Version
	@Column
	public Instant getUpdated() {
		return updated;
	}
	public void setUpdated(Instant updated) {
		this.updated = updated;
	}

	@Override
	public Long getServerId() {
		return serverId;
	}

	public void setServerId(Long serverId) {
		this.serverId = serverId;
	}
}
