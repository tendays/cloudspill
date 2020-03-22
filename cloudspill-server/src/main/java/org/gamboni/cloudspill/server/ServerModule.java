package org.gamboni.cloudspill.server;

import java.net.URL;
import java.util.List;
import java.util.Properties;

import org.gamboni.cloudspill.domain.GalleryPart;
import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.server.html.ImagePage;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.c3p0.internal.C3P0ConnectionProvider;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl;
import org.hibernate.jpa.boot.internal.PersistenceUnitInfoDescriptor;
import org.hibernate.service.ServiceRegistry;

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

public class ServerModule extends AbstractModule {

	private final String configPath;
	
	ServerModule(String configPath) {
		this.configPath = configPath;
	}
	
	@Override
	protected void configure() {
		bind(CloudSpillServer.class).asEagerSingleton();
		bind(ImagePage.Factory.class).asEagerSingleton();
	}

	@Provides
	public EntityManagerFactory sessionFactory() {
		MysqlDataSource dataSource = new MysqlDataSource();
		dataSource.setUrl("jdbc:mysql://localhost:3306/cloudspill");
		dataSource.setUser("cloudspill");
		dataSource.setPassword("cloudspill");
		dataSource.setCharacterEncoding("utf8");

		Properties prop = new Properties();
		prop.setProperty(AvailableSettings.URL, "jdbc:mysql://localhost:3306/cloudspill");
		prop.setProperty(AvailableSettings.USER, "cloudspill");
		prop.setProperty(AvailableSettings.PASS, "cloudspill");
		prop.setProperty(AvailableSettings.SHOW_SQL, "true");
		prop.setProperty(AvailableSettings.DIALECT, MySQLDialect.class.getName());

		// See https://www.databasesandlife.com/automatic-reconnect-from-hibernate-to-mysql/
		prop.setProperty(AvailableSettings.C3P0_MIN_SIZE, "5");
		prop.setProperty(AvailableSettings.C3P0_MAX_SIZE, "20");
		prop.setProperty(AvailableSettings.C3P0_TIMEOUT, "1800");
		prop.setProperty(AvailableSettings.C3P0_MAX_STATEMENTS, "50");
		prop.setProperty(AvailableSettings.CONNECTION_PROVIDER, C3P0ConnectionProvider.class.getName());

		PersistenceUnitInfo pui = new PersistenceUnitInfo() {
			@Override
			public String getPersistenceUnitName() {
				return "cloudspill";
			}

			@Override
			public String getPersistenceProviderClassName() {
				// the name sounds right but I've no idea what I'm doing
				return HibernatePersistenceProvider.class.getName();
			}

			@Override
			public PersistenceUnitTransactionType getTransactionType() {
				return PersistenceUnitTransactionType.RESOURCE_LOCAL;
			}

			@Override
			public DataSource getJtaDataSource() {
				return null;
			}

			@Override
			public DataSource getNonJtaDataSource() {
				return dataSource;
			}

			@Override
			public List<String> getMappingFileNames() {
				return ImmutableList.of();
			}

			@Override
			public List<URL> getJarFileUrls() {
				return ImmutableList.of();
			}

			@Override
			public URL getPersistenceUnitRootUrl() {
				return null;
			}

			@Override
			public List<String> getManagedClassNames() {
				return ImmutableList.of(
					User.class.getName(),
						GalleryPart.class.getName(),
						Item.class.getName()
				);
			}

			@Override
			public boolean excludeUnlistedClasses() {
				return false;
			}

			@Override
			public SharedCacheMode getSharedCacheMode() {
				return SharedCacheMode.UNSPECIFIED; // todo what is this
			}

			@Override
			public ValidationMode getValidationMode() {
				return ValidationMode.AUTO; // sounds good
			}

			@Override
			public Properties getProperties() {
				return prop;
			}

			@Override
			public String getPersistenceXMLSchemaVersion() {
				return "2.1"; // == JPA_VERSION?
			}

			@Override
			public ClassLoader getClassLoader() {
				return getClass().getClassLoader();
			}

			@Override
			public void addTransformer(ClassTransformer classTransformer) {

			}

			@Override
			public ClassLoader getNewTempClassLoader() {
				return getClassLoader();
			}
		};


		new HibernatePersistenceProvider().createContainerEntityManagerFactory(pui, prop);

		ImmutableMap<Object, Object> configuration = ImmutableMap.of(); // ?
		return new EntityManagerFactoryBuilderImpl(new PersistenceUnitInfoDescriptor(pui), configuration).build();
	}
	
	@Provides
	public ServerConfiguration serverConfiguration() {
		return new ServerConfiguration(configPath);
	}
}
