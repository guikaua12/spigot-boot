package me.approximations.apxPlugin.persistence.jpa.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hibernate.dialect.Dialect;

import javax.persistence.SharedCacheMode;
import javax.persistence.ValidationMode;
import javax.persistence.spi.ClassTransformer;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;
import javax.sql.DataSource;
import java.net.URL;
import java.sql.Driver;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Getter
public abstract class PersistenceUnitConfig implements PersistenceUnitInfo {
    protected final String persistenceUnitName;

    protected final String address;
    protected final String username;
    protected final List<Class<?>> entitiesClasses;
    protected final String password;
    protected final Class<? extends Driver> jdbcDriver;
    protected final Class<? extends Dialect> dialect;
    protected final boolean showSql;

    @Override
    public String getPersistenceProviderClassName() {
        return "org.hibernate.jpa.HibernatePersistenceProvider";
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
    public List<String> getManagedClassNames() {
        return entitiesClasses.stream().map(Class::getName).collect(Collectors.toList());
    }

    @Override
    public List<String> getMappingFileNames() {
        return null;
    }

    @Override
    public List<URL> getJarFileUrls() {
        return null;
    }

    @Override
    public URL getPersistenceUnitRootUrl() {
        return null;
    }

    @Override
    public boolean excludeUnlistedClasses() {
        return false;
    }

    @Override
    public SharedCacheMode getSharedCacheMode() {
        return null;
    }

    @Override
    public ValidationMode getValidationMode() {
        return null;
    }

    @Override
    public String getPersistenceXMLSchemaVersion() {
        return null;
    }

    @Override
    public ClassLoader getClassLoader() {
        return null;
    }

    @Override
    public void addTransformer(ClassTransformer classTransformer) {
    }

    @Override
    public ClassLoader getNewTempClassLoader() {
        return null;
    }
}
