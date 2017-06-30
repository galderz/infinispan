/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.test.cache.infinispan;
<<<<<<< HEAD
import static org.hibernate.TestLogger.LOG;
=======

<<<<<<< HEAD
>>>>>>> HHH-5942 - Migrate to JUnit 4
=======
import java.util.Properties;
>>>>>>> HHH-9490 - Migrate from dom4j to jaxb for XML processing;
import java.util.Set;

import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cache.infinispan.InfinispanRegionFactory;
import org.hibernate.cache.spi.GeneralDataRegion;
import org.hibernate.cache.spi.QueryResultsRegion;
import org.hibernate.cache.spi.Region;

import org.hibernate.test.cache.infinispan.util.CacheTestUtil;
import org.junit.Ignore;
import org.junit.Test;

import org.infinispan.AdvancedCache;
import org.infinispan.transaction.tm.BatchModeTransactionManager;

import org.jboss.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Base class for tests of QueryResultsRegion and TimestampsRegion.
 *
 * @author Galder Zamarreño
 * @since 3.5
 */
public abstract class AbstractGeneralDataRegionTestCase extends AbstractRegionImplTestCase {
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
    protected static final String KEY = "Key";

    protected static final String VALUE1 = "value1";
    protected static final String VALUE2 = "value2";

    public AbstractGeneralDataRegionTestCase( String name ) {
        super(name);
    }

    @Override
    protected void putInRegion( Region region,
                                Object key,
                                Object value ) {
        ((GeneralDataRegion)region).put(key, value);
    }

    @Override
    protected void removeFromRegion( Region region,
                                     Object key ) {
        ((GeneralDataRegion)region).evict(key);
    }

    /**
     * Test method for {@link QueryResultsRegion#evict(java.lang.Object)}. FIXME add testing of the
     * "immediately without regard for transaction isolation" bit in the CollectionRegionAccessStrategy API.
     */
    public void testEvict() throws Exception {
        evictOrRemoveTest();
    }

    private void evictOrRemoveTest() throws Exception {
        Configuration cfg = createConfiguration();
        InfinispanRegionFactory regionFactory = CacheTestUtil.startRegionFactory(getJdbcServices(), cfg, getCacheTestSupport());
        CacheAdapter localCache = getInfinispanCache(regionFactory);
        boolean invalidation = localCache.isClusteredInvalidation();

        // Sleep a bit to avoid concurrent FLUSH problem
        avoidConcurrentFlush();

        GeneralDataRegion localRegion = (GeneralDataRegion)createRegion(regionFactory,
                                                                        getStandardRegionName(REGION_PREFIX),
                                                                        cfg.getProperties(),
                                                                        null);

        cfg = createConfiguration();
        regionFactory = CacheTestUtil.startRegionFactory(getJdbcServices(), cfg, getCacheTestSupport());

        GeneralDataRegion remoteRegion = (GeneralDataRegion)createRegion(regionFactory,
                                                                         getStandardRegionName(REGION_PREFIX),
                                                                         cfg.getProperties(),
                                                                         null);

        assertNull("local is clean", localRegion.get(KEY));
        assertNull("remote is clean", remoteRegion.get(KEY));

        localRegion.put(KEY, VALUE1);
        assertEquals(VALUE1, localRegion.get(KEY));

        // allow async propagation
        sleep(250);
        Object expected = invalidation ? null : VALUE1;
        assertEquals(expected, remoteRegion.get(KEY));

        localRegion.evict(KEY);

        // allow async propagation
        sleep(250);
        assertEquals(null, localRegion.get(KEY));
        assertEquals(null, remoteRegion.get(KEY));
    }

    protected abstract String getStandardRegionName( String regionPrefix );

    /**
     * Test method for {@link QueryResultsRegion#evictAll()}. FIXME add testing of the
     * "immediately without regard for transaction isolation" bit in the CollectionRegionAccessStrategy API.
     */
    public void testEvictAll() throws Exception {
        evictOrRemoveAllTest("entity");
    }

    private void evictOrRemoveAllTest( String configName ) throws Exception {
        Configuration cfg = createConfiguration();
        InfinispanRegionFactory regionFactory = CacheTestUtil.startRegionFactory(getJdbcServices(), cfg, getCacheTestSupport());
        CacheAdapter localCache = getInfinispanCache(regionFactory);

        // Sleep a bit to avoid concurrent FLUSH problem
        avoidConcurrentFlush();

        GeneralDataRegion localRegion = (GeneralDataRegion)createRegion(regionFactory,
                                                                        getStandardRegionName(REGION_PREFIX),
                                                                        cfg.getProperties(),
                                                                        null);

        cfg = createConfiguration();
        regionFactory = CacheTestUtil.startRegionFactory(getJdbcServices(), cfg, getCacheTestSupport());
        CacheAdapter remoteCache = getInfinispanCache(regionFactory);

        // Sleep a bit to avoid concurrent FLUSH problem
        avoidConcurrentFlush();

        GeneralDataRegion remoteRegion = (GeneralDataRegion)createRegion(regionFactory,
                                                                         getStandardRegionName(REGION_PREFIX),
                                                                         cfg.getProperties(),
                                                                         null);

        Set keys = localCache.keySet();
        assertEquals("No valid children in " + keys, 0, getValidKeyCount(keys));

        keys = remoteCache.keySet();
        assertEquals("No valid children in " + keys, 0, getValidKeyCount(keys));

        assertNull("local is clean", localRegion.get(KEY));
        assertNull("remote is clean", remoteRegion.get(KEY));

        localRegion.put(KEY, VALUE1);
        assertEquals(VALUE1, localRegion.get(KEY));

        // Allow async propagation
        sleep(250);

        remoteRegion.put(KEY, VALUE1);
        assertEquals(VALUE1, remoteRegion.get(KEY));

        // Allow async propagation
        sleep(250);

        localRegion.evictAll();

        // allow async propagation
        sleep(250);
        // This should re-establish the region root node in the optimistic case
        assertNull(localRegion.get(KEY));
        assertEquals("No valid children in " + keys, 0, getValidKeyCount(localCache.keySet()));

        // Re-establishing the region root on the local node doesn't
        // propagate it to other nodes. Do a get on the remote node to re-establish
        // This only adds a node in the case of optimistic locking
        assertEquals(null, remoteRegion.get(KEY));
        assertEquals("No valid children in " + keys, 0, getValidKeyCount(remoteCache.keySet()));

        assertEquals("local is clean", null, localRegion.get(KEY));
        assertEquals("remote is clean", null, remoteRegion.get(KEY));
    }

    protected Configuration createConfiguration() {
        Configuration cfg = CacheTestUtil.buildConfiguration("test", InfinispanRegionFactory.class, false, true);
        return cfg;
    }

    protected void rollback() {
        try {
            BatchModeTransactionManager.getInstance().rollback();
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }
}
=======
   protected static final String KEY = "Key";

   protected static final String VALUE1 = "value1";
   protected static final String VALUE2 = "value2";

   public AbstractGeneralDataRegionTestCase(String name) {
      super(name);
   }

   @Override
   protected void putInRegion(Region region, Object key, Object value) {
      ((GeneralDataRegion) region).put(key, value);
   }

   @Override
   protected void removeFromRegion(Region region, Object key) {
      ((GeneralDataRegion) region).evict(key);
   }

   /**
    * Test method for {@link QueryResultsRegion#evict(java.lang.Object)}.
    * 
    * FIXME add testing of the "immediately without regard for transaction isolation" bit in the
    * CollectionRegionAccessStrategy API.
    */
   public void testEvict() throws Exception {
      evictOrRemoveTest();
   }

   private void evictOrRemoveTest() throws Exception {
      Configuration cfg = createConfiguration();
      InfinispanRegionFactory regionFactory = CacheTestUtil.startRegionFactory(
			  getServiceRegistry( cfg.getProperties() ), cfg, getCacheTestSupport()
	  );
      CacheAdapter localCache = getInfinispanCache(regionFactory);
      boolean invalidation = localCache.isClusteredInvalidation();

      // Sleep a bit to avoid concurrent FLUSH problem
      avoidConcurrentFlush();

      GeneralDataRegion localRegion = (GeneralDataRegion) createRegion(regionFactory,
               getStandardRegionName(REGION_PREFIX), cfg.getProperties(), null);

      cfg = createConfiguration();
      regionFactory = CacheTestUtil.startRegionFactory(
			  getServiceRegistry( cfg.getProperties() ), cfg, getCacheTestSupport()
	  );

      GeneralDataRegion remoteRegion = (GeneralDataRegion) createRegion(regionFactory,
               getStandardRegionName(REGION_PREFIX), cfg.getProperties(), null);

      assertNull("local is clean", localRegion.get(KEY));
      assertNull("remote is clean", remoteRegion.get(KEY));

      localRegion.put(KEY, VALUE1);
      assertEquals(VALUE1, localRegion.get(KEY));

      // allow async propagation
      sleep(250);
      Object expected = invalidation ? null : VALUE1;
      assertEquals(expected, remoteRegion.get(KEY));

      localRegion.evict(KEY);

      // allow async propagation
      sleep(250);
      assertEquals(null, localRegion.get(KEY));
      assertEquals(null, remoteRegion.get(KEY));
   }

   protected abstract String getStandardRegionName(String regionPrefix);

   /**
    * Test method for {@link QueryResultsRegion#evictAll()}.
    * 
    * FIXME add testing of the "immediately without regard for transaction isolation" bit in the
    * CollectionRegionAccessStrategy API.
    */
   public void testEvictAll() throws Exception {
      evictOrRemoveAllTest("entity");
   }

   private void evictOrRemoveAllTest(String configName) throws Exception {
      Configuration cfg = createConfiguration();
      InfinispanRegionFactory regionFactory = CacheTestUtil.startRegionFactory(
			  getServiceRegistry( cfg.getProperties() ), cfg, getCacheTestSupport()
	  );
      CacheAdapter localCache = getInfinispanCache(regionFactory);

      // Sleep a bit to avoid concurrent FLUSH problem
      avoidConcurrentFlush();

      GeneralDataRegion localRegion = (GeneralDataRegion) createRegion(regionFactory,
               getStandardRegionName(REGION_PREFIX), cfg.getProperties(), null);

      cfg = createConfiguration();
      regionFactory = CacheTestUtil.startRegionFactory(
			  getServiceRegistry( cfg.getProperties() ), cfg, getCacheTestSupport()
	  );
      CacheAdapter remoteCache = getInfinispanCache(regionFactory);

      // Sleep a bit to avoid concurrent FLUSH problem
      avoidConcurrentFlush();

      GeneralDataRegion remoteRegion = (GeneralDataRegion) createRegion(regionFactory,
               getStandardRegionName(REGION_PREFIX), cfg.getProperties(), null);

      Set keys = localCache.keySet();
      assertEquals("No valid children in " + keys, 0, getValidKeyCount(keys));

      keys = remoteCache.keySet();
      assertEquals("No valid children in " + keys, 0, getValidKeyCount(keys));

      assertNull("local is clean", localRegion.get(KEY));
      assertNull("remote is clean", remoteRegion.get(KEY));

      localRegion.put(KEY, VALUE1);
      assertEquals(VALUE1, localRegion.get(KEY));

      // Allow async propagation
      sleep(250);

      remoteRegion.put(KEY, VALUE1);
      assertEquals(VALUE1, remoteRegion.get(KEY));

      // Allow async propagation
      sleep(250);

      localRegion.evictAll();

      // allow async propagation
      sleep(250);
      // This should re-establish the region root node in the optimistic case
      assertNull(localRegion.get(KEY));
      assertEquals("No valid children in " + keys, 0, getValidKeyCount(localCache.keySet()));

      // Re-establishing the region root on the local node doesn't
      // propagate it to other nodes. Do a get on the remote node to re-establish
      // This only adds a node in the case of optimistic locking
      assertEquals(null, remoteRegion.get(KEY));
      assertEquals("No valid children in " + keys, 0, getValidKeyCount(remoteCache.keySet()));

      assertEquals("local is clean", null, localRegion.get(KEY));
      assertEquals("remote is clean", null, remoteRegion.get(KEY));
   }

   protected Configuration createConfiguration() {
      Configuration cfg = CacheTestUtil.buildConfiguration("test", InfinispanRegionFactory.class, false, true);
      return cfg;
   }

   protected void rollback() {
      try {
         BatchModeTransactionManager.getInstance().rollback();
      } catch (Exception e) {
         log.error(e.getMessage(), e);
      }
   }
}
>>>>>>> HHH-5949 - Migrate, complete and integrate TransactionFactory as a service
=======
=======
	private static final Logger log = Logger.getLogger( AbstractGeneralDataRegionTestCase.class );

>>>>>>> HHH-6098 - Slight naming changes in regards to new logging classes
	protected static final String KEY = "Key";

	protected static final String VALUE1 = "value1";
	protected static final String VALUE2 = "value2";

	protected StandardServiceRegistryBuilder createStandardServiceRegistryBuilder() {
		return CacheTestUtil.buildBaselineStandardServiceRegistryBuilder(
				"test",
				InfinispanRegionFactory.class,
				false,
				true
		);
	}

	@Override
	protected void putInRegion(Region region, Object key, Object value) {
		((GeneralDataRegion) region).put( key, value );
	}

	@Override
	protected void removeFromRegion(Region region, Object key) {
		((GeneralDataRegion) region).evict( key );
	}

	@Test
	@Ignore // currently ignored because of HHH-9800
	public void testEvict() throws Exception {
		evictOrRemoveTest();
	}

	private void evictOrRemoveTest() throws Exception {
		final StandardServiceRegistryBuilder ssrb = createStandardServiceRegistryBuilder();
		StandardServiceRegistry registry1 = ssrb.build();
		StandardServiceRegistry registry2 = ssrb.build();
		try {
			InfinispanRegionFactory regionFactory = CacheTestUtil.startRegionFactory(
					registry1,
					getCacheTestSupport()
			);

			final Properties properties = CacheTestUtil.toProperties( ssrb.getSettings() );

			boolean invalidation = false;

			// Sleep a bit to avoid concurrent FLUSH problem
			avoidConcurrentFlush();

			GeneralDataRegion localRegion = (GeneralDataRegion) createRegion(
					regionFactory,
					getStandardRegionName( REGION_PREFIX ),
					properties,
					null
			);

			regionFactory = CacheTestUtil.startRegionFactory(
					registry2,
					getCacheTestSupport()
			);

			GeneralDataRegion remoteRegion = (GeneralDataRegion) createRegion(
					regionFactory,
					getStandardRegionName( REGION_PREFIX ),
					properties,
					null
			);
			assertNull( "local is clean", localRegion.get( KEY ) );
			assertNull( "remote is clean", remoteRegion.get( KEY ) );

			regionPut( localRegion );
			sleep( 250 );
			assertEquals( VALUE1, localRegion.get( KEY ) );

			// allow async propagation
			sleep( 250 );
			Object expected = invalidation ? null : VALUE1;
			assertEquals( expected, remoteRegion.get( KEY ) );

			regionEvict( localRegion );

			// allow async propagation
			sleep( 250 );
			assertEquals( null, localRegion.get( KEY ) );
			assertEquals( null, remoteRegion.get( KEY ) );
		}
		finally {
			StandardServiceRegistryBuilder.destroy( registry1 );
			StandardServiceRegistryBuilder.destroy( registry2 );
		}
	}

   protected void regionEvict(GeneralDataRegion region) throws Exception {
      region.evict(KEY);
   }

   protected void regionPut(GeneralDataRegion region) throws Exception {
      region.put(KEY, VALUE1);
   }

   protected abstract String getStandardRegionName(String regionPrefix);

	/**
	 * Test method for {@link QueryResultsRegion#evictAll()}.
	 * <p/>
	 * FIXME add testing of the "immediately without regard for transaction isolation" bit in the
	 * CollectionRegionAccessStrategy API.
	 */
	public void testEvictAll() throws Exception {
		evictOrRemoveAllTest( "entity" );
	}

	private void evictOrRemoveAllTest(String configName) throws Exception {
		final StandardServiceRegistryBuilder ssrb = createStandardServiceRegistryBuilder();
		StandardServiceRegistry registry1 = ssrb.build();
		StandardServiceRegistry registry2 = ssrb.build();

		try {
			final Properties properties = CacheTestUtil.toProperties( ssrb.getSettings() );

			InfinispanRegionFactory regionFactory = CacheTestUtil.startRegionFactory(
					registry1,
					getCacheTestSupport()
			);
			AdvancedCache localCache = getInfinispanCache( regionFactory );

			// Sleep a bit to avoid concurrent FLUSH problem
			avoidConcurrentFlush();

			GeneralDataRegion localRegion = (GeneralDataRegion) createRegion(
					regionFactory,
					getStandardRegionName( REGION_PREFIX ),
					properties,
					null
			);

			regionFactory = CacheTestUtil.startRegionFactory(
					registry2,
					getCacheTestSupport()
			);
			AdvancedCache remoteCache = getInfinispanCache( regionFactory );

			// Sleep a bit to avoid concurrent FLUSH problem
			avoidConcurrentFlush();

			GeneralDataRegion remoteRegion = (GeneralDataRegion) createRegion(
					regionFactory,
					getStandardRegionName( REGION_PREFIX ),
					properties,
					null
			);

			Set keys = localCache.keySet();
			assertEquals( "No valid children in " + keys, 0, getValidKeyCount( keys ) );

			keys = remoteCache.keySet();
			assertEquals( "No valid children in " + keys, 0, getValidKeyCount( keys ) );

			assertNull( "local is clean", localRegion.get( KEY ) );
			assertNull( "remote is clean", remoteRegion.get( KEY ) );

			regionPut(localRegion);
			assertEquals( VALUE1, localRegion.get( KEY ) );

			// Allow async propagation
			sleep( 250 );

			regionPut(remoteRegion);
			assertEquals( VALUE1, remoteRegion.get( KEY ) );

			// Allow async propagation
			sleep( 250 );

			localRegion.evictAll();

			// allow async propagation
			sleep( 250 );
			// This should re-establish the region root node in the optimistic case
			assertNull( localRegion.get( KEY ) );
			assertEquals( "No valid children in " + keys, 0, getValidKeyCount( localCache.keySet() ) );

			// Re-establishing the region root on the local node doesn't
			// propagate it to other nodes. Do a get on the remote node to re-establish
			// This only adds a node in the case of optimistic locking
			assertEquals( null, remoteRegion.get( KEY ) );
			assertEquals( "No valid children in " + keys, 0, getValidKeyCount( remoteCache.keySet() ) );

			assertEquals( "local is clean", null, localRegion.get( KEY ) );
			assertEquals( "remote is clean", null, remoteRegion.get( KEY ) );
		}
		finally {
			StandardServiceRegistryBuilder.destroy( registry1 );
			StandardServiceRegistryBuilder.destroy( registry2 );
		}
	}

	protected void rollback() {
		try {
			BatchModeTransactionManager.getInstance().rollback();
		}
		catch (Exception e) {
			log.error( e.getMessage(), e );
		}
	}
}
>>>>>>> HHH-5942 - Migrate to JUnit 4
