package org.gamboni.cloudspill.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;

import org.gamboni.cloudspill.domain.GalleryPart;
import org.gamboni.cloudspill.domain.Item;
import org.gamboni.cloudspill.domain.User;
import org.hibernate.c3p0.internal.C3P0ConnectionProvider;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl;
import org.hibernate.jpa.boot.internal.PersistenceUnitInfoDescriptor;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.tool.schema.TargetType;

import java.net.URL;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;

import javax.persistence.EntityManagerFactory;
import javax.persistence.SharedCacheMode;
import javax.persistence.ValidationMode;
import javax.persistence.spi.ClassTransformer;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;
import javax.sql.DataSource;

/**
 * @author tendays
 */
abstract class BackendModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(getServerClass()).asEagerSingleton();
    }

    protected abstract Class<? extends AbstractServer> getServerClass();

    @Provides
    public EntityManagerFactory sessionFactory() {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUrl("jdbc:mysql://localhost:3306/cloudspill");
        dataSource.setUser("cloudspill");
        dataSource.setPassword("cloudspill");
        dataSource.setCharacterEncoding("utf8");
        dataSource.setUseUnicode(true);
        dataSource.setConnectionCollation("utf8mb4_general_ci"); //utf8"); // maybe add COLLATE utf8mb4_general_ci?


        Properties prop = new Properties();
        prop.setProperty(AvailableSettings.URL, "jdbc:mysql://localhost:3306/cloudspill");
        prop.setProperty(AvailableSettings.USER, "cloudspill");
        prop.setProperty(AvailableSettings.PASS, "cloudspill");
        prop.setProperty(AvailableSettings.SHOW_SQL, "true");
        prop.setProperty(AvailableSettings.DIALECT, MySQLDialect.class.getName());
        prop.setProperty(AvailableSettings.DEFAULT_BATCH_FETCH_SIZE, "50");

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
                return Lists.transform(BackendModule.this.getManagedClasses(),
                        Class::getName);
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

        ImmutableMap<Object, Object> configuration = ImmutableMap.of(); // ?

        EntityManagerFactoryBuilderImpl emfBuilder = new EntityManagerFactoryBuilderImpl(new PersistenceUnitInfoDescriptor(pui), configuration);
        EntityManagerFactory emf = emfBuilder.build();

        SchemaExport exp = new SchemaExport();
        exp.setOutputFile("/tmp/t");
        exp.execute(EnumSet.of(TargetType.STDOUT), SchemaExport.Action.CREATE, emfBuilder.getMetadata());

        return emf;
    }

    protected abstract List<Class<?>> getManagedClasses();

}
