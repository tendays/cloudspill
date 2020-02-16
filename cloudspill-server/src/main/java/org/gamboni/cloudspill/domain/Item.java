/**
 * 
 */
package org.gamboni.cloudspill.domain;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Transient;
import javax.persistence.Version;

import org.gamboni.cloudspill.shared.domain.JpaItem;

import com.google.common.base.Joiner;

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
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column
	public long getId() {
		return id;
	}

	@Transient
	public Long getServerId() {
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

	/** Construct a Serialised form understandable by the android frontend. */
	public String serialise() {
		// TODO quote or escape
		return getId()
				+ ";"
				+ getUser()
				+ ";"
				+ getFolder()
				+ ";"
				+ getPath()
				+ ";"
				+ serialise(getDate())
				+ ";"
				+ getType()
				+ ";"
				+ Joiner.on(",").join(getTags())
				+ ";"
				+ getChecksum();
	}

	private static String serialise(LocalDateTime time) {
		return (time == null) ? "" : Long.toString(time.atOffset(ZoneOffset.UTC).toInstant().toEpochMilli());
	}
}
