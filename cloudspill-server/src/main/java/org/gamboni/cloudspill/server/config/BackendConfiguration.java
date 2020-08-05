package org.gamboni.cloudspill.server.config;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;

import org.gamboni.cloudspill.shared.api.SharedConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Properties;

/**
 * @author tendays
 */
public abstract class BackendConfiguration implements SharedConfiguration {

    protected final Properties prop;

    protected BackendConfiguration(String path) {
        try (Reader reader = new InputStreamReader(new FileInputStream(path), Charsets.UTF_8)) {
            prop = new Properties();
            prop.load(reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public File getRepositoryPath() {
        return new File(requireProperty("repositoryPath"));
    }

    public String getPublicUrl() {
        return requireProperty("publicUrl");
    }

    @Override
    public boolean insecureAuthentication() {
        return Boolean.valueOf(prop.getProperty("insecureAuthentication", "false"));
    }

    protected String requireProperty(String name) {
        return Preconditions.checkNotNull(prop.getProperty(name), "Missing required property '"+ name +"'");
    }
}
