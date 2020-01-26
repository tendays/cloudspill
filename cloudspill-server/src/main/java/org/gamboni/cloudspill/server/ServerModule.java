package org.gamboni.cloudspill.server;

import java.util.Properties;

import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.domain.User;
import org.gamboni.cloudspill.server.html.ImagePage;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.c3p0.internal.C3P0ConnectionProvider;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.service.ServiceRegistry;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

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
	public SessionFactory sessionFactory() {
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
		
		ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
				.applySettings(prop)
				.build();
		return new Configuration()
				.addPackage("org.gamboni.cloudspill.domain")
			   .addProperties(prop)
			   .addAnnotatedClass(Item.class)
			   .addAnnotatedClass(User.class)
			   .buildSessionFactory(serviceRegistry);
	}
	
	@Provides
	public ServerConfiguration serverConfiguration() {
		return new ServerConfiguration(configPath);
	}
}
