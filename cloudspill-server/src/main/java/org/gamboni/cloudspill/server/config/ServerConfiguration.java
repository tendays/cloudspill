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

	public File getRepositoryPath() {
		return new File(prop.getProperty("repositoryPath"));
	}

	public String getRepositoryName() {
		return prop.getProperty("repositoryName");
	}

	public boolean allowAnonymousUserCreation() {
		// TODO this should actually be a command line option so it has less chance to stick
		String value = prop.getProperty("allowAnonymousUserCreation");
		return (value != null) && value.equals("true");
	}
}