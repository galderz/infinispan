/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.infinispan.test;

import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.CacheDelegate;
import org.infinispan.commands.CommandsFactory;
import org.infinispan.commands.VisitableCommand;
import org.infinispan.container.DataContainer;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.context.InvocationContext;
import org.infinispan.context.InvocationContextContainer;
import org.infinispan.factories.ComponentRegistry;
import org.infinispan.factories.GlobalComponentRegistry;
import org.infinispan.interceptors.InterceptorChain;
import org.infinispan.interceptors.base.CommandInterceptor;
import org.infinispan.lifecycle.ComponentStatus;
import org.infinispan.loaders.CacheLoader;
import org.infinispan.loaders.CacheLoaderManager;
import org.infinispan.manager.CacheManager;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.remoting.ReplicationQueue;
import org.infinispan.remoting.transport.Address;
import org.infinispan.util.concurrent.locks.LockManager;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import javax.transaction.TransactionManager;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import static java.io.File.separator;

public class TestingUtil {
   
   private static final Log log = LogFactory.getLog(TestingUtil.class);
   private static final Random random = new Random();
   public static final String TEST_PATH = "target" + separator + "tempFiles";
   public static final String INFINISPAN_START_TAG = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<infinispan\n" +
         "      xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
         "      xsi:schemaLocation=\"urn:infinispan:config:4.0 http://www.infinispan.org/schemas/infinispan-config-4.0.xsd\"\n" +
         "      xmlns=\"urn:infinispan:config:4.0\">";
   public static final String INFINISPAN_END_TAG="</infinispan>";


   /**
    * Extracts the value of a field in a given target instance using reflection, able to extract private fields as
    * well.
    *
    * @param target    object to extract field from
    * @param fieldName name of field to extract
    * @return field value
    */
   public static Object extractField(Object target, String fieldName) {
      return extractField(target.getClass(), target, fieldName);
   }

   public static void replaceField(Object newValue, String fieldName, Object owner, Class baseType) {
      Field field;
      try {
         field = baseType.getDeclaredField(fieldName);
         field.setAccessible(true);
         field.set(owner, newValue);
      }
      catch (Exception e) {
         throw new RuntimeException(e);//just to simplify exception handling
      }
   }


   public static Object extractField(Class type, Object target, String fieldName) {
      while (true) {
         Field field;
         try {
            field = type.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
         }
         catch (Exception e) {
            if (type.equals(Object.class)) {
               e.printStackTrace();
               return null;
            } else {
               // try with superclass!!
               type = type.getSuperclass();
            }
         }
      }
   }

   public static <T extends CommandInterceptor> T findInterceptor(Cache<?, ?> cache, Class<T> interceptorToFind) {
      for (CommandInterceptor i : cache.getAdvancedCache().getInterceptorChain()) {
         if (interceptorToFind.isInstance(i)) return interceptorToFind.cast(i);
      }
      return null;
   }

   /**
    * Loops, continually calling {@link #areCacheViewsComplete(Cache[])} until it either returns true or
    * <code>timeout</code> ms have elapsed.
    *
    * @param caches  caches which must all have consistent views
    * @param timeout max number of ms to loop
    * @throws RuntimeException if <code>timeout</code> ms have elapse without all caches having the same number of
    *                          members.
    */
   public static void blockUntilViewsReceived(Cache[] caches, long timeout) {
      long failTime = System.currentTimeMillis() + timeout;

      while (System.currentTimeMillis() < failTime) {
         sleepThread(100);
         if (areCacheViewsComplete(caches)) {
            return;
         }
      }

      throw new RuntimeException("timed out before caches had complete views");
   }

   /**
    * Version of blockUntilViewsReceived that uses varargs
    */
   public static void blockUntilViewsReceived(long timeout, Cache... caches) {
      blockUntilViewsReceived(caches, timeout);
   }

   /**
    * Version of blockUntilViewsReceived that uses varargsa and cache managers
    */
   public static void blockUntilViewsReceived(long timeout, CacheManager... cacheManagers) {
      blockUntilViewsReceived(timeout, true, cacheManagers);
   }

   /**
    * Waits for the given memebrs to be removed from the cluster. The difference between this and {@link
    * #blockUntilViewsReceived(long, org.infinispan.manager.CacheManager[])} methods(s) is that it does not barf if
    * more than expected memebers is in the cluster - this is because we expect to start with a grater number fo
    * memebers than we eventually expect. It will barf though, if the number of members is not the one expected but
    * only after the timeout expieres.
    */
   public static void blockForMemberToFail(long timeout, CacheManager... cacheManagers) {
      blockUntilViewsReceived(timeout, false, cacheManagers);
      areCacheViewsComplete(true, cacheManagers);
   }

   public static void blockUntilViewsReceived(long timeout, boolean barfIfTooManyMembers, CacheManager... cacheManagers) {
      long failTime = System.currentTimeMillis() + timeout;

      while (System.currentTimeMillis() < failTime) {
         sleepThread(100);
         if (areCacheViewsComplete(barfIfTooManyMembers, cacheManagers)) {
            return;
         }
      }

      throw new RuntimeException("timed out before caches had complete views");
   }

   /**
    * Loops, continually calling {@link #areCacheViewsComplete(CacheSPI[])} until it either returns true or
    * <code>timeout</code> ms have elapsed.
    *
    * @param caches  caches which must all have consistent views
    * @param timeout max number of ms to loop
    * @throws RuntimeException if <code>timeout</code> ms have elapse without all caches having the same number of
    *                          members.
    */
//   public static void blockUntilViewsReceived(Cache[] caches, long timeout) {
//      long failTime = System.currentTimeMillis() + timeout;
//
//      while (System.currentTimeMillis() < failTime) {
//         sleepThread(100);
//         if (areCacheViewsComplete(caches)) {
//            return;
//         }
//      }
//
//      throw new RuntimeException("timed out before caches had complete views");
//   }


   /**
    * An overloaded version of {@link #blockUntilViewsReceived(long,Cache[])} that allows for 'shrinking' clusters.
    * I.e., the usual method barfs if there are more members than expected.  This one takes a param
    * (barfIfTooManyMembers) which, if false, will NOT barf but will wait until the cluster 'shrinks' to the desired
    * size.  Useful if in tests, you kill a member and want to wait until this fact is known across the cluster.
    *
    * @param timeout
    * @param barfIfTooManyMembers
    * @param caches
    */
   public static void blockUntilViewsReceived(long timeout, boolean barfIfTooManyMembers, Cache... caches) {
      long failTime = System.currentTimeMillis() + timeout;

      while (System.currentTimeMillis() < failTime) {
         sleepThread(100);
         if (areCacheViewsComplete(caches, barfIfTooManyMembers)) {
            return;
         }
      }

      throw new RuntimeException("timed out before caches had complete views");
   }

   /**
    * Loops, continually calling {@link #areCacheViewsComplete(Cache[])} until it either returns true or
    * <code>timeout</code> ms have elapsed.
    *
    * @param groupSize number of caches expected in the group
    * @param timeout   max number of ms to loop
    * @throws RuntimeException if <code>timeout</code> ms have elapse without all caches having the same number of
    *                          members.
    */
   public static void blockUntilViewReceived(Cache cache, int groupSize, long timeout) {
      blockUntilViewReceived(cache, groupSize, timeout, true);
   }

   public static void blockUntilViewReceived(Cache cache, int groupSize, long timeout, boolean barfIfTooManyMembersInView) {
      long failTime = System.currentTimeMillis() + timeout;

      while (System.currentTimeMillis() < failTime) {
         sleepThread(100);
         if (isCacheViewComplete(cache.getCacheManager().getMembers(), cache.getCacheManager().getAddress(), groupSize, barfIfTooManyMembersInView)) {
            return;
         }
      }

      throw new RuntimeException("timed out before caches had complete views");
   }

   /**
    * Checks each cache to see if the number of elements in the array returned by {@link CacheManager#getMembers()}
    * matches the size of the <code>caches</code> parameter.
    *
    * @param caches caches that should form a View
    * @return <code>true</code> if all caches have <code>caches.length</code> members; false otherwise
    * @throws IllegalStateException if any of the caches have MORE view members than caches.length
    */
   public static boolean areCacheViewsComplete(Cache[] caches) {
      return areCacheViewsComplete(caches, true);
   }

   public static boolean areCacheViewsComplete(Cache[] caches, boolean barfIfTooManyMembers) {
      int memberCount = caches.length;

      for (int i = 0; i < memberCount; i++) {
         if (!isCacheViewComplete(caches[i].getCacheManager().getMembers(), caches[i].getCacheManager().getAddress(), memberCount, barfIfTooManyMembers)) {
            return false;
         }
      }

      return true;
   }

   public static boolean areCacheViewsComplete(boolean barfIfTooManyMembers, CacheManager... cacheManagers) {
      if (cacheManagers == null) throw new NullPointerException("Cache Manager array is null");
      int memberCount = cacheManagers.length;

      for (int i = 0; i < memberCount; i++) {
         if (!isCacheViewComplete(cacheManagers[i].getMembers(), cacheManagers[i].getAddress(), memberCount, barfIfTooManyMembers)) {
            return false;
         }
      }

      return true;
   }

//   /**
//    * @param cache
//    * @param memberCount
//    */
//   public static boolean isCacheViewComplete(Cache cache, int memberCount) {
//      List members = cache.getCacheManager().getMembers();
//      if (members == null || memberCount > members.size()) {
//         return false;
//      } else if (memberCount < members.size()) {
//         // This is an exceptional condition
//         StringBuilder sb = new StringBuilder("Cache at address ");
//         sb.append(cache.getCacheManager().getAddress());
//         sb.append(" had ");
//         sb.append(members.size());
//         sb.append(" members; expecting ");
//         sb.append(memberCount);
//         sb.append(". Members were (");
//         for (int j = 0; j < members.size(); j++) {
//            if (j > 0) {
//               sb.append(", ");
//            }
//            sb.append(members.get(j));
//         }
//         sb.append(')');
//
//         throw new IllegalStateException(sb.toString());
//      }
//
//      return true;
//   }

   /**
    * @param c
    * @param memberCount
    */
   public static boolean isCacheViewComplete(Cache c, int memberCount) {
      return isCacheViewComplete(c.getCacheManager().getMembers(), c.getCacheManager().getAddress(), memberCount, true);
   }

   public static boolean isCacheViewComplete(List members, Address address, int memberCount, boolean barfIfTooManyMembers) {
      if (members == null || memberCount > members.size()) {
         return false;
      } else if (memberCount < members.size()) {
         if (barfIfTooManyMembers) {
            // This is an exceptional condition
            StringBuilder sb = new StringBuilder("Cache at address ");
            sb.append(address);
            sb.append(" had ");
            sb.append(members.size());
            sb.append(" members; expecting ");
            sb.append(memberCount);
            sb.append(". Members were (");
            for (int j = 0; j < members.size(); j++) {
               if (j > 0) {
                  sb.append(", ");
               }
               sb.append(members.get(j));
            }
            sb.append(')');

            throw new IllegalStateException(sb.toString());
         } else return false;
      }

      return true;
   }


   /**
    * Puts the current thread to sleep for the desired number of ms, suppressing any exceptions.
    *
    * @param sleeptime number of ms to sleep
    */
   public static void sleepThread(long sleeptime) {
      try {
         Thread.sleep(sleeptime);
      }
      catch (InterruptedException ie) {
      }
   }

   public static void sleepRandom(int maxTime) {
      sleepThread(random.nextInt(maxTime));
   }

   public static void recursiveFileRemove(String directoryName) {
      File file = new File(directoryName);
      recursiveFileRemove(file);
   }

   public static void recursiveFileRemove(File file) {
      if (file.exists()) {
         System.out.println("Deleting file " + file);
         recursivedelete(file);
      }
   }

   private static void recursivedelete(File f) {
      if (f.isDirectory()) {
         File[] files = f.listFiles();
         for (File file : files) {
            recursivedelete(file);
         }
      }
      //System.out.println("File " + f.toURI() + " deleted = " + f.delete());
      f.delete();
   }

   public static void killCaches(Collection caches) {
      if (caches != null) killCaches((Cache[]) caches.toArray(new Cache[]{}));
   }

   public static void killCacheManagers(CacheManager... cacheManagers) {
      if (cacheManagers != null) {
         for (CacheManager cm : cacheManagers) {
            if (cm != null) cm.stop();
         }
      }
   }

   public static void killCacheManagers(Collection<CacheManager> cacheManagers) {
      killCacheManagers(cacheManagers.toArray(new CacheManager[cacheManagers.size()]));
   }
   
   public static void clearContent(CacheManager cacheManager) {
      if (cacheManager != null) {
         Set<Cache> runningCaches = getRunningCaches(cacheManager);
         for (Cache cache : runningCaches) {
            clearRunningTx(cache);
         }
         if (!cacheManager.getStatus().allowInvocations()) return;
         for (Cache cache : runningCaches) {
            removeInMemoryData(cache);
            clearCacheLoader(cache);
            clearReplicationQueues(cache);
            ((AdvancedCache) cache).getInvocationContextContainer().createInvocationContext();
         }
      }
   }
   
   protected static Set<Cache> getRunningCaches(CacheManager cacheManager) {
      ConcurrentMap<String, Cache> caches = (ConcurrentMap<String, Cache>) TestingUtil.extractField(DefaultCacheManager.class, cacheManager, "caches");
      if (caches == null) return Collections.emptySet();
      Set<Cache> result = new HashSet<Cache>();
      for (Cache cache : caches.values()) {
         if (cache.getStatus() == ComponentStatus.RUNNING) result.add(cache);
      }
      return result;
   }
   
   private static void clearRunningTx(Cache cache) {
      if (cache != null) {
         TransactionManager txm = TestingUtil.getTransactionManager(cache);
         if (txm == null) return;
         try {
            txm.rollback();
         }
         catch (Exception e) {
            // don't care
         }
      }
   }
   
   private static void clearReplicationQueues(Cache cache) {
      ReplicationQueue queue = TestingUtil.extractComponent(cache, ReplicationQueue.class);
      if (queue != null) queue.reset();
   }
   
   private static void clearCacheLoader(Cache cache) {
      CacheLoaderManager cacheLoaderManager = TestingUtil.extractComponent(cache, CacheLoaderManager.class);
      if (cacheLoaderManager != null && cacheLoaderManager.getCacheStore() != null) {
         try {
            cacheLoaderManager.getCacheStore().clear();
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }
   }
   
   private static void removeInMemoryData(Cache cache) {
      CacheManager mgr = cache.getCacheManager();
      Address a = mgr.getAddress();
      String str;
      if (a == null)
         str = "a non-clustered cache manager";
      else
         str = "a cache manager at address " + a;
      log.debug("Cleaning data for cache '{0}' on {1}", cache.getName(), str);
      DataContainer dataContainer = TestingUtil.extractComponent(cache, DataContainer.class);
      log.debug("removeInMemoryData(): dataContainerBefore == {0}", dataContainer);
      dataContainer.clear();
      log.debug("removeInMemoryData(): dataContainerAfter == {0}", dataContainer);
   }

   /**
    * Kills a cache - stops it, clears any data in any cache loaders, and rolls back any associated txs
    */
   public static void killCaches(Cache... caches) {
      for (Cache c : caches) {
         try {
            if (c != null && c.getStatus() == ComponentStatus.RUNNING) {
               TransactionManager tm = getTransactionManager(c);
               if (tm != null) {
                  try {
                     tm.rollback();
                  }
                  catch (Exception e) {
                     // don't care
                  }
               }
               c.stop();
            }
         }
         catch (Throwable t) {

         }
      }
   }

   /**
    * Clears transaction with the current thread in the given transaction manager.
    *
    * @param txManager a TransactionManager to be cleared
    */
   public static void killTransaction(TransactionManager txManager) {
      if (txManager != null) {
         try {
            txManager.rollback();
         }
         catch (Exception e) {
            // don't care
         }
      }
   }


   /**
    * Clears any associated transactions with the current thread in the caches' transaction managers.
    */
   public static void killTransactions(Cache... caches) {
      for (Cache c : caches) {
         if (c != null && c.getStatus() == ComponentStatus.RUNNING) {
            TransactionManager tm = getTransactionManager(c);
            if (tm != null) {
               try {
                  tm.rollback();
               }
               catch (Exception e) {
                  // don't care
               }
            }
         }
      }
   }

   /**
    * For testing only - introspects a cache and extracts the ComponentRegistry
    *
    * @param cache cache to introspect
    * @return component registry
    */
   public static ComponentRegistry extractComponentRegistry(Cache cache) {
      return (ComponentRegistry) extractField(cache, "componentRegistry");
   }

   public static GlobalComponentRegistry extractGlobalComponentRegistry(CacheManager cacheManager) {
      return (GlobalComponentRegistry) extractField(cacheManager, "globalComponentRegistry");
   }

   public static LockManager extractLockManager(Cache cache) {
      return extractComponentRegistry(cache).getComponent(LockManager.class);
   }

   /**
    * For testing only - introspects a cache and extracts the ComponentRegistry
    *
    * @param ci interceptor chain to introspect
    * @return component registry
    */
   public static ComponentRegistry extractComponentRegistry(InterceptorChain ci) {
      return (ComponentRegistry) extractField(ci, "componentRegistry");
   }


   /**
    * Replaces the existing interceptor chain in the cache wih one represented by the interceptor passed in.  This
    * utility updates dependencies on all components that rely on the interceptor chain as well.
    *
    * @param cache       cache that needs to be altered
    * @param interceptor the first interceptor in the new chain.
    */
   public static void replaceInterceptorChain(Cache cache, CommandInterceptor interceptor) {
      ComponentRegistry cr = extractComponentRegistry(cache);
      // make sure all interceptors here are wired.
      CommandInterceptor i = interceptor;
      do {
         cr.wireDependencies(i);
      }
      while ((i = i.getNext()) != null);

      InterceptorChain inch = cr.getComponent(InterceptorChain.class);
      inch.setFirstInChain(interceptor);
   }

   /**
    * Replaces an existing interceptor of the given type in the interceptor chain with a new interceptor instance passed as parameter.
    * 
    * @param replacingInterceptor the interceptor to add to the interceptor chain
    * @param toBeReplacedInterceptorType the type of interceptor that should be swapped with the new one
    * @return true if the interceptor was replaced
    */
   public static boolean replaceInterceptor(Cache cache, CommandInterceptor replacingInterceptor, Class<? extends CommandInterceptor> toBeReplacedInterceptorType) {
      ComponentRegistry cr = extractComponentRegistry(cache);
      // make sure all interceptors here are wired.
      CommandInterceptor i = replacingInterceptor;
      do {
         cr.wireDependencies(i);
      }
      while ((i = i.getNext()) != null);
      InterceptorChain inch = cr.getComponent(InterceptorChain.class);
      return inch.replaceInterceptor(replacingInterceptor, toBeReplacedInterceptorType);
   }

   /**
    * Retrieves the remote delegate for a given cache.  It is on this remote delegate that the JGroups RPCDispatcher
    * invokes remote methods.
    *
    * @param cache cache instance for which a remote delegate is to be retrieved
    * @return remote delegate, or null if the cacge is not configured for replication.
    */
   public static CacheDelegate getInvocationDelegate(Cache cache) {
      return (CacheDelegate) cache;
   }

   /**
    * Blocks until the cache has reached a specified state.
    *
    * @param cache       cache to watch
    * @param cacheStatus status to wait for
    * @param timeout     timeout to wait for
    */
   public static void blockUntilCacheStatusAchieved(Cache cache, ComponentStatus cacheStatus, long timeout) {
      AdvancedCache spi = cache.getAdvancedCache();
      long killTime = System.currentTimeMillis() + timeout;
      while (System.currentTimeMillis() < killTime) {
         if (spi.getStatus() == cacheStatus) return;
         sleepThread(50);
      }
      throw new RuntimeException("Timed out waiting for condition");
   }

   public static void replicateCommand(Cache cache, VisitableCommand command) throws Throwable {
      ComponentRegistry cr = extractComponentRegistry(cache);
      InterceptorChain ic = cr.getComponent(InterceptorChain.class);
      InvocationContextContainer icc = cr.getComponent(InvocationContextContainer.class);
      InvocationContext ctxt = icc.createInvocationContext();
      ic.invoke(ctxt, command);
   }

   public static void blockUntilViewsReceived(int timeout, List caches) {
      blockUntilViewsReceived((Cache[]) caches.toArray(new Cache[]{}), timeout);
   }

   public static CommandsFactory extractCommandsFactory(Cache<Object, Object> cache) {
      return (CommandsFactory) extractField(cache, "commandsFactory");
   }

   public static void dumpCacheContents(List caches) {
      System.out.println("**** START: Cache Contents ****");
      int count = 1;
      for (Object o : caches) {
         Cache c = (Cache) o;
         if (c == null) {
            System.out.println("  ** Cache " + count + " is null!");
         } else {
            System.out.println("  ** Cache " + count + " is " + c.getCacheManager().getAddress());
         }
         count++;
      }
      System.out.println("**** END: Cache Contents ****");
   }

   public static void dumpCacheContents(Cache... caches) {
      dumpCacheContents(Arrays.asList(caches));
   }

   /**
    * Extracts a component of a given type from the cache's internal component registry
    */
   public static <T> T extractComponent(Cache cache, Class<T> componentType) {
      ComponentRegistry cr = extractComponentRegistry(cache);
      return cr.getComponent(componentType);
   }

   /**
    * Extracts a component of a given type from the cache's internal component registry
    */
   public static <T> T extractGlobalComponent(CacheManager cacheManager, Class<T> componentType) {
      GlobalComponentRegistry gcr = extractGlobalComponentRegistry(cacheManager);
      return gcr.getComponent(componentType);
   }

   public static TransactionManager getTransactionManager(Cache cache) {
      return cache == null ? null : extractComponent(cache, TransactionManager.class);
   }

   /**
    * Replaces a component in a running cache
    *
    * @param cache                cache in which to replace component
    * @param componentType        component type of which to replace
    * @param replacementComponent new instance
    * @param rewire               if true, ComponentRegistry.rewire() is called after replacing.
    * @return the original component that was replaced
    */
   public static <T> T replaceComponent(Cache<?, ?> cache, Class<T> componentType, T replacementComponent, boolean rewire) {
      ComponentRegistry cr = extractComponentRegistry(cache);
      T old = cr.getComponent(componentType);
      cr.registerComponent(replacementComponent, componentType);
      if (rewire) cr.rewire();
      return old;
   }

   /**
    * Replaces a component in a running cache manager (global component registry)
    *
    * @param cacheManager         cache in which to replace component
    * @param componentType        component type of which to replace
    * @param replacementComponent new instance
    * @param rewire               if true, ComponentRegistry.rewire() is called after replacing.
    * @return the original component that was replaced
    */
   public static <T> T replaceComponent(CacheManager cacheManager, Class<T> componentType, T replacementComponent, boolean rewire) {
      GlobalComponentRegistry cr = extractGlobalComponentRegistry(cacheManager);
      T old = cr.getComponent(componentType);
      cr.registerComponent(replacementComponent, componentType);
      if (rewire) {
         cr.rewire();
         cr.rewireNamedRegistries();
      }
      return old;
   }

   public static CacheLoader getCacheLoader(Cache cache) {
      CacheLoaderManager clm = extractComponent(cache, CacheLoaderManager.class);
      if (clm != null && clm.isEnabled()) {
         return clm.getCacheLoader();
      } else {
         return null;
      }
   }

   public static String printCache(Cache cache) {
      DataContainer dataContainer = TestingUtil.extractComponent(cache, DataContainer.class);
      Iterator it = dataContainer.iterator();
      StringBuilder builder = new StringBuilder(cache.getName() + "[");
      while (it.hasNext()) {
         CacheEntry ce = (CacheEntry) it.next();
         builder.append(ce.getKey() + "=" + ce.getValue() + ",l=" + ce.getLifespan() + "; ");
      }
      builder.append("]");
      return builder.toString();
   }
   
   public static Set getInternalKeys(Cache cache) {
      DataContainer dataContainer = TestingUtil.extractComponent(cache, DataContainer.class);
      Set keys = new HashSet();
      for (CacheEntry entry : dataContainer) {
         keys.add(entry.getKey());
      }
      return keys;
   }
   
   public static Collection getInternalValues(Cache cache) {
      DataContainer dataContainer = TestingUtil.extractComponent(cache, DataContainer.class);
      Collection values = new ArrayList();
      for (CacheEntry entry : dataContainer) {
         values.add(entry.getValue());
      }
      return values;
   }

   /**
    * Creates a path to a temp directory based on a base directory and a test.
    * @param basedir may be null, if relative directories are to be used.
    * @param test test that requires this directory.
    * @return a path, relative or absolute.
    */
   public static String tmpDirectory(String basedir, AbstractInfinispanTest test) {
      String prefix = "";
      if (basedir != null) {
         prefix = basedir;
         if (!prefix.endsWith(separator)) prefix += separator;
      }
      return prefix + TEST_PATH + separator + test.getClass().getSimpleName();
   }

   public static String k(Method method, String index) {
      return new StringBuilder().append("k").append(index).append('-')
         .append(method.getName()).toString();
   }

   public static String v(Method method, String index) {
      return new StringBuilder().append("v").append(index).append('-')
         .append(method.getName()).toString();
   }

   public static String k(Method method) {
      return k(method, "");
   }

   public static Object v(Method method) {
      return v(method, "");
   }
}
