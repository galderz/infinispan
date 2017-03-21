package org.infinispan.query;

import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.notifications.cachelistener.filter.CacheEventFilterConverter;
import org.infinispan.objectfilter.ObjectFilter;
import org.infinispan.objectfilter.impl.ReflectionMatcher;
import org.infinispan.query.continuous.impl.ContinuousQueryImpl;
import org.infinispan.query.dsl.Query;
import org.infinispan.query.dsl.QueryFactory;
import org.infinispan.query.api.continuous.ContinuousQuery;
import org.infinispan.query.dsl.embedded.impl.EmbeddedQueryFactory;
import org.infinispan.query.dsl.embedded.impl.JPACacheEventFilterConverter;
import org.infinispan.query.dsl.embedded.impl.JPAFilterAndConverter;
import org.infinispan.query.dsl.embedded.impl.QueryEngine;
import org.infinispan.query.dsl.impl.BaseQuery;
import org.infinispan.query.impl.SearchManagerImpl;
import org.infinispan.query.logging.Log;
import org.infinispan.security.AuthorizationManager;
import org.infinispan.security.AuthorizationPermission;
import org.infinispan.util.logging.LogFactory;

/**
 * Helper class to get a SearchManager out of an indexing enabled cache.
 *
 * @author Sanne Grinovero <sanne@hibernate.org> (C) 2011 Red Hat Inc.
 */
public final class Search {

   private static final Log log = LogFactory.getLog(Search.class, Log.class);

   private Search() {
   }

   public static <K, V> CacheEventFilterConverter<K, V, ObjectFilter.FilterResult> makeFilter(Query query) {
      BaseQuery baseQuery = (BaseQuery) query;
      JPAFilterAndConverter<K, V> filterAndConverter = new JPAFilterAndConverter<K, V>(baseQuery.getJPAQuery(), baseQuery.getNamedParameters(), ReflectionMatcher.class);
      return new JPACacheEventFilterConverter<K, V, ObjectFilter.FilterResult>(filterAndConverter);
   }

   public static QueryFactory getQueryFactory(Cache<?, ?> cache) {
      if (cache == null || cache.getAdvancedCache() == null) {
         throw new IllegalArgumentException("cache parameter shall not be null");
      }
      AdvancedCache<?, ?> advancedCache = cache.getAdvancedCache();
      ensureAccessPermissions(advancedCache);
      QueryEngine queryEngine = SecurityActions.getCacheComponentRegistry(advancedCache).getComponent(QueryEngine.class);
      if (queryEngine == null) {
         throw log.queryModuleNotInitialised();
      }
      return new EmbeddedQueryFactory(queryEngine);
   }

   public static <K, V> ContinuousQuery<K, V> getContinuousQuery(Cache<K, V> cache) {
      return new ContinuousQueryImpl<K, V>(cache);
   }

   public static SearchManager getSearchManager(Cache<?, ?> cache) {
      if (cache == null || cache.getAdvancedCache() == null) {
         throw new IllegalArgumentException("cache parameter shall not be null");
      }
      AdvancedCache<?, ?> advancedCache = cache.getAdvancedCache();
      ensureAccessPermissions(advancedCache);
      return new SearchManagerImpl(advancedCache);
   }

   private static void ensureAccessPermissions(final AdvancedCache<?, ?> cache) {
      AuthorizationManager authorizationManager = SecurityActions.getCacheAuthorizationManager(cache);
      if (authorizationManager != null) {
         authorizationManager.checkPermission(AuthorizationPermission.BULK_READ);
      }
   }
}
