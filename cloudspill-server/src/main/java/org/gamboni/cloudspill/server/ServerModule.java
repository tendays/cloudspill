package org.gamboni.cloudspill.server;

import java.util.Properties;

import org.gamboni.cloudspill.domain.Item;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class ServerModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(CloudSpillServer.class).asEagerSingleton();
	}

	@Provides
	public SessionFactory sessionFactory() {
		Properties prop = new Properties();
		prop.setProperty("hibernate.connection.url", "jdbc:mysql://localhost:3306/cloudspill");
		prop.setProperty("hibernate.connection.username", "cloudspill");
		prop.setProperty("hibernate.connection.password", "cloudspill");
		prop.setProperty("hibernate.show_sql", "true");
		prop.setProperty("dialect", "org.hibernate.dialect.MySQLDialect");
		
		ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
				.applySettings(prop)
				.build();
		return new Configuration()
				.addPackage("com.concretepage.persistence")
			   .addProperties(prop)
			   .addAnnotatedClass(Item.class)
			   .buildSessionFactory(serviceRegistry);
	}
	
}
