package org.gamboni.cloudspill.server.config;

import com.google.common.base.Charsets;

import org.gamboni.cloudspill.shared.api.SharedConfiguration;

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

    public String getPublicUrl() {
        return prop.getProperty("publicUrl");
    }

    public String getCss() {
        return prop.getProperty("css");
    }
}
