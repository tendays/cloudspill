package org.gamboni.cloudspill.server.config;

/**
 * @author tendays
 */
public class ForwarderConfiguration extends BackendConfiguration {

    public ForwarderConfiguration(String path) {
        super(path);
    }

    public String getRemoteServer() {
        return prop.getProperty("remoteServer");
    }

}
