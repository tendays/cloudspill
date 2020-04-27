/**
 * 
 */
package org.gamboni.cloudspill.shared.domain;

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

import static org.gamboni.cloudspill.shared.util.Files.append;

/**
 * @author tendays
 *
 */
@MappedSuperclass
public abstract class JpaItem implements IsItem {
	private String user;
	private String folder;
	private String path;
	private String checksum;
	private LocalDateTime date;
	private ItemType type;
	private Set<String> tags;
	private String description;
	private String datePrecision;

	@Column
	public String getUser() {
		return user;
	}
	public void setUser(String user) {
		this.user = user;
	}
	
	/** Each Folder represents a physical folder on one of the user's devices. */
	public String getFolder() {
		return folder;
	}
	public void setFolder(String folder) {
		this.folder = folder;
	}

	public String getPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}

	/** DateTimeFormatter characters indicating the precision of the date column: s for seconds, m for minutes, H for hours, d for day, M for month, y for year. */
	public String getDatePrecision() { return datePrecision; }
	public void setDatePrecision(String datePrecision) { this.datePrecision = datePrecision; }
	
	public LocalDateTime getDate() {
		return this.date;
	}
	public void setDate(LocalDateTime date) {
		this.date = date;
	}
	
	@Override
	public String getChecksum() {
		return checksum;
	}
	public void setChecksum(String checksum) {
		this.checksum = checksum;
	}

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

	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}

	public File getFile(File rootFolder) {
		return append(append(append(rootFolder, user), folder), path);
	}

	@Transient
	public String getTitle() {
		return user +"/"+ folder +"/"+ path;
	}
	
	public String toString() {
		return "Item("+ user +", "+ folder +", "+ path + ", "+ tags +")";
	}

	@Transient
	public boolean isPublic() {
		return Items.isPublic(this);
	}
}
