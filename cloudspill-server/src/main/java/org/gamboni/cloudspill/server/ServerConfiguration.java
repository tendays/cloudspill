/**
 * 
 */
package org.gamboni.cloudspill.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Properties;

import com.google.common.base.Charsets;

/**
 * @author tendays
 *
 */
public class ServerConfiguration {
	Properties prop;
	
	public ServerConfiguration(String path) {
		try (Reader reader = new InputStreamReader(new FileInputStream(path), Charsets.UTF_8)) {
			prop = new Properties();
			prop.load(reader);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public File getRepositoryPath() {
		return new File(prop.getProperty("repositoryPath"));
	}
}
