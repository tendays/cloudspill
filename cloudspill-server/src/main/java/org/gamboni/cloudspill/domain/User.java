/**
 * 
 */
package org.gamboni.cloudspill.domain;

import org.gamboni.cloudspill.shared.domain.InvalidPasswordException;
import org.gamboni.cloudspill.shared.domain.IsUser;
import org.gamboni.cloudspill.shared.util.Log;
import org.gamboni.cloudspill.shared.util.Supplier;
import org.mindrot.jbcrypt.BCrypt;

import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;

/**
 * @author tendays
 *
 */
@Entity
public class User implements IsUser {
	private String name;
	private String salt;
	private String pass;

	@Id
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public void verifyPassword(String password) throws InvalidPasswordException {
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

	public static User withName(String name) {
		User result = new User();
		result.setName(name);
		return result;
	}
}
