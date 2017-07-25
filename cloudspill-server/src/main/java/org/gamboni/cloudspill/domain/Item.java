/**
 * 
 */
package org.gamboni.cloudspill.domain;

import static org.gamboni.cloudspill.util.Files.append;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * An Item represents a file. Hierarchy is as follows: /user/folder/path where
 * path may contain slashes.
 * 
 * @author tendays
 */
@Entity
@Table(name="item")
public class Item {
	
	long id;
	String user;
	String folder;
	String path;
	LocalDateTime date;
	
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name="ID")
	public long getId() {
		return id;
	}
	public void setId(long id) {
		this.id = id;
	}
	
	@Column(name="USER")
	public String getUser() {
		return user;
	}
	public void setUser(String user) {
		this.user = user;
	}
	
	/** Each Folder represents a physical folder on one of the user's devices. */
	@Column(name="FOLDER")
	public String getFolder() {
		return folder;
	}
	public void setFolder(String folder) {
		this.folder = folder;
	}
	@Column(name="PATH")
	public String getPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}
	
	@Column(name="DATE")
	public LocalDateTime getDate() {
		return this.date;
	}
	
	public void setDate(LocalDateTime date) {
		this.date = date;
	}
	
	public File getFile(File rootFolder) {
		return append(append(append(rootFolder, user), folder), path);
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
		+ getDate().atOffset(ZoneOffset.UTC);
	}
	
	public String toString() {
		return "Item("+ user +", "+ folder +", "+ path +")";
	}
}
