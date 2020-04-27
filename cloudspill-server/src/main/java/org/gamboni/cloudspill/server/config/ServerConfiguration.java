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
}
