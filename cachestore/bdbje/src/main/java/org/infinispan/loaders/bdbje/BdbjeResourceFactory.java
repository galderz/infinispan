package org.infinispan.loaders.bdbje;

import com.sleepycat.bind.EntryBinding;
import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.collections.CurrentTransaction;
import com.sleepycat.collections.StoredMap;
import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.je.*;
import com.sleepycat.util.ExceptionUnwrapper;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.marshall.Marshaller;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.io.File;

/**
 * Factory that assembles objects specific to the SleepyCat JE API.
 *
 * @author Adrian Cole
 * @since 4.0
 */
public class BdbjeResourceFactory {
    private static final Log log = LogFactory.getLog(BdbjeResourceFactory.class);
    private static final boolean trace = log.isTraceEnabled();

    private BdbjeCacheStoreConfig config;

    public BdbjeResourceFactory(BdbjeCacheStoreConfig config) {
        this.config = config;
    }

    /**
     * @return PreparableTransactionRunner that will try to resolve deadlocks maximum of {@link
     *         BdbjeCacheStoreConfig#getMaxTxRetries()} times.
     */
    public PreparableTransactionRunner createPreparableTransactionRunner(Environment env) {
        return new PreparableTransactionRunner(env, config.getMaxTxRetries(), null);
    }

    public CurrentTransaction createCurrentTransaction(Environment env) {
        return CurrentTransaction.getInstance(env);
    }

    /**
     * Open the environment, creating it if it doesn't exist.
     *
     * @param envLocation base directory where the Environment will write files
     * @return open Environment with a lock timeout of {@link org.infinispan.loaders.bdbje.BdbjeCacheStoreConfig#getLockAcquistionTimeout()}
     *         milliseconds.
     */
    public Environment createEnvironment(File envLocation) throws DatabaseException {
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);
        /* lock timeout is in microseconds */
        envConfig.setLockTimeout(config.getLockAcquistionTimeout() * 1000);
        if (trace) log.trace("opening or creating je environment at {0}", envLocation);
        Environment env = new Environment(envLocation, envConfig);
        log.debug("opened je environment at {0}", envLocation);
        return env;
    }

    public StoredClassCatalog createStoredClassCatalog(Database catalogDb) throws DatabaseException {
        StoredClassCatalog catalog = new StoredClassCatalog(catalogDb);
        log.debug("created stored class catalog from database {0}", config.getCatalogDbName());
        return catalog;
    }

    /**
     * Open the database, creating it if it doesn't exist.
     *
     * @return open transactional Database
     */
    public Database createDatabase(Environment env, String name) throws DatabaseException {
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        if (trace) log.trace("opening or creating database {0}", name);
        Database db = env.openDatabase(null, name, dbConfig);
        log.debug("opened database {0}", name);
        return db;
    }

    /**
     * create a {@link com.sleepycat.collections.StoredMap} persisted by the <code>database</code>
     *
     * @param database     where entries in the StoredMap are persisted
     * @param classCatalog location to store class descriptions
     * @return StoredMap backed by the database and classCatalog
     * @throws com.sleepycat.je.DatabaseException
     *          if the StoredMap cannot be opened.
     */
    public StoredMap<Object, InternalCacheEntry> createStoredMapViewOfDatabase(Database database, StoredClassCatalog classCatalog, Marshaller m) throws DatabaseException {
        EntryBinding<Object> storedEntryKeyBinding =
                new SerialBinding<Object>(classCatalog, Object.class);
        EntryBinding<InternalCacheEntry> storedEntryValueBinding = new InternalCacheEntryBinding(m);
        try {
            return new StoredMap<Object, InternalCacheEntry>(database,
                    storedEntryKeyBinding, storedEntryValueBinding, true);
        } catch (Exception caught) {
            caught = ExceptionUnwrapper.unwrap(caught);
            throw new DatabaseException("error opening stored map", caught);
        }
    }

    /**
     * create a {@link com.sleepycat.collections.StoredMap} persisted by the <code>database</code>
     *
     * @param database     where entries in the StoredMap are persisted
     * @param classCatalog location to store class descriptions
     * @return StoredMap backed by the database and classCatalog
     * @throws com.sleepycat.je.DatabaseException
     *          if the StoredMap cannot be opened.
     */
    public StoredSortedMap<Long, Object> createStoredSortedMapForKeyExpiry(Database database, StoredClassCatalog classCatalog, Marshaller marshaller) throws DatabaseException {
        EntryBinding<Long> expiryKeyBinding =
                new SerialBinding<Long>(classCatalog, Long.class);
        EntryBinding<Object> expiryValueBinding =
                new SerialBinding<Object>(classCatalog, Object.class);
        try {
            return new StoredSortedMap<Long, Object>(database,
                    expiryKeyBinding, expiryValueBinding, true);
        } catch (Exception caught) {
            caught = ExceptionUnwrapper.unwrap(caught);
            throw new DatabaseException("error opening stored map", caught);
        }

    }
}