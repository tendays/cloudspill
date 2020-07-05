package org.gamboni.cloudspill.server;

import com.google.common.collect.ImmutableList;
import com.google.inject.Provides;

import org.gamboni.cloudspill.domain.GalleryPart;
import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.domain.RemoteItem;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.domain.UserAuthToken;
import org.gamboni.cloudspill.server.config.ForwarderConfiguration;
import org.gamboni.cloudspill.server.config.ServerConfiguration;

import java.util.List;

/**
 * @author tendays
 */
public class ForwarderModule extends BackendModule {
    private final String configPath;

    public ForwarderModule(String configPath) {
        this.configPath = configPath;
    }

    @Provides
    public ForwarderConfiguration serverConfiguration() {
        return new ForwarderConfiguration(configPath);
    }

    @Override
    protected Class<? extends AbstractServer> getServerClass() {
        return CloudSpillForwarder.class;
    }

    @Override
    protected List<Class<?>> getManagedClasses() {
        return ImmutableList.of(
                User.class,
                RemoteItem.class
        );
    }
}
