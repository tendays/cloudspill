/**
 * 
 */
package org.gamboni.cloudspill.shared.domain;

import static org.gamboni.cloudspill.shared.util.Files.append;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;

/**
 * @author tendays
 *
 */
@MappedSuperclass
public class BaseItem {
	String user;
	String folder;
	String path;
	String checksum;
	LocalDateTime date;
	ItemType type;
	Set<String> tags;
	String description;
	
	@Column
	public String getUser() {
		return user;
	}
	public void setUser(String user) {
		this.user = user;
	}
	
	/** Each Folder represents a physical folder on one of the user's devices. */
	@Column
	public String getFolder() {
		return folder;
	}
	public void setFolder(String folder) {
		this.folder = folder;
	}
	@Column
	public String getPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}
	
	@Column
	public LocalDateTime getDate() {
		return this.date;
	}
	
	public void setDate(LocalDateTime date) {
		this.date = date;
	}
	
	@Column
	public String getChecksum() {
		return checksum;
	}
	public void setChecksum(String checksum) {
		this.checksum = checksum;
	}
	@Column
	@Enumerated(EnumType.STRING)
	public ItemType getType() {
		return type;
	}
	public void setType(ItemType type) {
		this.type = type;
	}

	@ElementCollection(fetch = FetchType.LAZY)
//	@BatchSize(size=50)
	public Set<String> getTags() {
		return tags;
	}
	
	public void setTags(Set<String> tags) {
		this.tags = tags;
	}

	@Column
	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public File getFile(File rootFolder) {
		return append(append(append(rootFolder, user), folder), path);
	}
	
	public String toString() {
		return "Item("+ user +", "+ folder +", "+ path + ", "+ tags +")";
	}


	@Transient
	public boolean isPublic() {
		return this.getTags().contains("public");
	}
}
