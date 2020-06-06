/**
 * 
 */
package org.gamboni.cloudspill.domain;

import org.gamboni.cloudspill.shared.domain.InvalidPasswordException;
import org.gamboni.cloudspill.shared.domain.IsUser;
import org.gamboni.cloudspill.shared.util.Log;
import org.gamboni.cloudspill.shared.util.Supplier;
import org.mindrot.jbcrypt.BCrypt;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

/**
 * @author tendays
 *
 */
@Entity
public class User implements IsUser {
	private String name;
	private String salt;
	private String pass;
	
	@Column
	@Id
	public String getName() {
		return name;
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

	public void setName(String name) {
		this.name = name;
	}
	
	@Column
	public String getSalt() {
		return salt;
	}
	public void setSalt(String salt) {
		this.salt = salt;
	}
	
	@Column
	public String getPass() {
		return pass;
	}
	public void setPass(String pass) {
		this.pass = pass;
	}
	
}
