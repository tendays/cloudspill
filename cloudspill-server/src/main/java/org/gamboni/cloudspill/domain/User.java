/**
 * 
 */
package org.gamboni.cloudspill.domain;

import org.gamboni.cloudspill.shared.domain.InvalidPasswordException;
import org.gamboni.cloudspill.shared.domain.IsUser;
import org.gamboni.cloudspill.shared.domain.PermissionDeniedException;
import org.gamboni.cloudspill.shared.util.Log;
import org.mindrot.jbcrypt.BCrypt;

import javax.persistence.Entity;
import javax.persistence.Id;

/**
 * @author tendays
 *
 */
@Entity
public class User implements IsUser {
	public static final String DEFAULT_GROUP = "user";
	public static final String ADMIN_GROUP = "admin";
	private String name;
	private String salt;
	private String pass;
	private String userGroup;

	@Id
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public void verifyPassword(String password) throws InvalidPasswordException {
		if (this.salt == null || this.pass == null) {
			Log.error("User "+ this.getName() +" has no password");
			throw new InvalidPasswordException();
		}
		final String queryHash = BCrypt.hashpw(password, this.getSalt());
		if (!queryHash.equals(this.getPass())) {
			Log.error("Invalid credentials for user "+ this.getName());
			throw new InvalidPasswordException();
		} else {
			Log.info("User "+ this.getName() +" authenticated");
		}
	}

	public String getSalt() {
		return salt;
	}
	public void setSalt(String salt) {
		this.salt = salt;
	}
	
	public String getPass() {
		return pass;
	}
	public void setPass(String pass) {
		this.pass = pass;
	}

	public String getUserGroup() {
		return userGroup;
	}

	public void setUserGroup(String userGroup) {
		this.userGroup = userGroup;
	}

	public boolean hasGroup(String group) {
		return this.userGroup.equals(group);
	}

	public void verifyGroup(String group) throws PermissionDeniedException {
		if (!hasGroup(group)) {
			throw new PermissionDeniedException();
		}
	}

	public static User withName(String name) {
		User result = new User();
		result.setName(name);
		result.setUserGroup(DEFAULT_GROUP);
		return result;
	}
}
