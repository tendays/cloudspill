/**
 * 
 */
package org.gamboni.cloudspill.server.config;

import java.io.File;

/**
 * @author tendays
 *
 */
public class ServerConfiguration extends BackendConfiguration {

	public ServerConfiguration(String path) {
		super(path);
	}

	public String getRepositoryName() {
		return requireProperty("repositoryName");
	}

	public boolean allowAnonymousUserCreation() {
		// TODO this should actually be a command line option so it has less chance to stick
		String value = prop.getProperty("allowAnonymousUserCreation");
		return (value != null) && value.equals("true");
	}
}
