package org.gamboni.cloudspill.server;

import java.net.URL;
import java.util.List;
import java.util.Properties;

import org.gamboni.cloudspill.domain.GalleryPart;
import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.server.config.ServerConfiguration;
import org.hibernate.c3p0.internal.C3P0ConnectionProvider;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl;
import org.hibernate.jpa.boot.internal.PersistenceUnitInfoDescriptor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;

import javax.persistence.EntityManagerFactory;
import javax.persistence.SharedCacheMode;
import javax.persistence.ValidationMode;
import javax.persistence.spi.ClassTransformer;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;
import javax.sql.DataSource;

public class ServerModule extends BackendModule {
    private final String configPath;
    public ServerModule(String configPath) {
        this.configPath = configPath;
    }
	@Provides
	public ServerConfiguration serverConfiguration() {
		return new ServerConfiguration(configPath);
	}

    @Override
    protected Class<? extends AbstractServer> getServerClass() {
        return CloudSpillServer.class;
    }

    @Override
    protected List<Class<?>> getManagedClasses() {
        return ImmutableList.of(
                User.class,
                GalleryPart.class,
                Item.class
        );
    }
}
