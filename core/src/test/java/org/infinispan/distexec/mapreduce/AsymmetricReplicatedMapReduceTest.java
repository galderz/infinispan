package org.infinispan.distexec.mapreduce;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.test.MultipleCacheManagersTest;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import static org.junit.Assert.assertTrue;

/**
 * // TODO: Document this
 *
 * @author Galder Zamarreño
 * @since // TODO
 */
@Test(groups = "functional", testName = "distexec.mapreduce.AsymmetricReplicatedMapReduceTest")
public class AsymmetricReplicatedMapReduceTest extends MultipleCacheManagersTest {

   static final String CACHE_NAME = "asymmetricCache";

   static HashMap<String,Integer> counts = new HashMap<String, Integer>();

   static {
      counts.put("of", 2);
      counts.put("open", 1);
      counts.put("is", 6);
      counts.put("source", 1);
      counts.put("JBoss", 5);
      counts.put("in", 2);
      counts.put("capital", 1);
      counts.put("world", 3);
      counts.put("Hello", 2);
      counts.put("Ontario", 1);
      counts.put("cool", 1);
      counts.put("JUDCon", 2);
      counts.put("Infinispan", 3);
      counts.put("a", 1);
      counts.put("awesome", 1);
      counts.put("Application", 1);
      counts.put("am", 1);
      counts.put("RedHat", 2);
      counts.put("Server", 1);
      counts.put("community", 2);
      counts.put("as", 1);
      counts.put("the", 1);
      counts.put("Toronto", 2);
      counts.put("close", 1);
      counts.put("to", 1);
      counts.put("division", 1);
      counts.put("here", 1);
      counts.put("Boston", 3);
      counts.put("well", 1);
      counts.put("World", 2);
      counts.put("I", 1);
      counts.put("rules", 2);
   }

   @Override
   protected void createCacheManagers() throws Throwable {
      ConfigurationBuilder builder = getDefaultClusteredCacheConfig(CacheMode.LOCAL, false);
      createClusteredCaches(2, builder);

      // Define replicated cache in only one of the nodes
      manager(1).defineConfiguration(CACHE_NAME,
            new ConfigurationBuilder().clustering().cacheMode(CacheMode.REPL_SYNC).build());
   }

   public void testinvokeMapReduceOnAllKeys() throws Exception {
      MapReduceTask<String,String,String,Integer> task = invokeMapReduce(new WordCountMapper(), new WordCountReducer());
      Map<String, Integer> mapReduce = task.execute();
      verifyResults(mapReduce, counts);
   }

   MapReduceTask<String, String, String, Integer> invokeMapReduce(
         Mapper<String, String, String, Integer> mapper,
         Reducer<String, Integer> reducer) {
      Cache c = cache(0, CACHE_NAME);

      c.put("1", "Hello world here I am");
      c.put("2", "Infinispan rules the world");
      c.put("3", "JUDCon is in Boston");
      c.put("4", "JBoss World is in Boston as well");
      c.put("12", "JBoss Application Server");
      c.put("15", "Hello world");
      c.put("14", "Infinispan community");

      c.put("111", "Infinispan open source");
      c.put("112", "Boston is close to Toronto");
      c.put("113", "Toronto is a capital of Ontario");
      c.put("114", "JUDCon is cool");
      c.put("211", "JBoss World is awesome");
      c.put("212", "JBoss rules");
      c.put("213", "JBoss division of RedHat ");
      c.put("214", "RedHat community");

      MapReduceTask<String, String, String, Integer> task = createMapReduceTask(c);
      task.mappedWith(mapper).reducedWith(reducer);
      task.combinedWith(reducer);
      return task;
   }

   MapReduceTask<String, String, String, Integer> createMapReduceTask(Cache c){
      return new MapReduceTask<String, String, String, Integer>(c);
   }

   void verifyResults(Map <String,Integer> result, Map <String,Integer> verifyAgainst) {
      assertTrue("Results should have at least 1 answer", result.size() > 0);
      for (Map.Entry<String, Integer> e : result.entrySet()) {
         String key = e.getKey();
         Integer count = verifyAgainst.get(key);
         assertTrue("key " + e.getKey() + " does not have count " + count + " but " + e.getValue(), count.equals(e.getValue()));
      }
   }

   static class WordCountMapper implements Mapper<String, String, String,Integer> {
      @Override
      public void map(String key, String value, Collector<String, Integer> collector) {
         if(value == null) throw new IllegalArgumentException("Key " + key + " has value " + value);
         StringTokenizer tokens = new StringTokenizer(value);
         while (tokens.hasMoreElements()) {
            String s = (String) tokens.nextElement();
            collector.emit(s, 1);
         }
      }
   }

   static class WordCountReducer implements Reducer<String, Integer> {
      @Override
      public Integer reduce(String key, Iterator<Integer> iter) {
         int sum = 0;
         while (iter.hasNext()) {
            sum += iter.next();
         }
         return sum;
      }
   }

}
