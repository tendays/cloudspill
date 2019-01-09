/**
 * 
 */
package org.gamboni.cloudspill.client.domain;

import static org.gamboni.cloudspill.shared.util.Files.append;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Version;

import org.gamboni.cloudspill.shared.domain.BaseItem;
import org.gamboni.cloudspill.shared.domain.ItemType;
import org.hibernate.annotations.BatchSize;

import com.google.common.base.Joiner;

/**
 * An Item represents a file. Hierarchy is as follows: /user/folder/path where
 * path may contain slashes.
 * 
 * @author tendays
 */
@Entity
public class Item extends BaseItem {
	
	long id;
	Instant updated;
	
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

}
