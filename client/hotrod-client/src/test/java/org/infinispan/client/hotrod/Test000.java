package org.infinispan.client.hotrod;

import org.infinispan.Cache;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.configuration.NearCacheMode;
import org.infinispan.commons.equivalence.AnyServerEquivalence;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.server.hotrod.HotRodServer;
import org.infinispan.server.hotrod.configuration.HotRodServerConfiguration;
import org.infinispan.server.hotrod.configuration.HotRodServerConfigurationBuilder;
import org.infinispan.test.AbstractInfinispanTest;
import org.testng.annotations.Test;

@Test(testName = "client.hotrod.Test000", groups = "functional")
public class Test000 extends AbstractInfinispanTest {

   public void test000() {
      org.infinispan.configuration.cache.ConfigurationBuilder embeddedBuilder = new org.infinispan.configuration.cache.ConfigurationBuilder();
      embeddedBuilder
            .dataContainer()
            .keyEquivalence(new AnyServerEquivalence())
            .valueEquivalence(new AnyServerEquivalence())
            .compatibility()
            .enable();
      DefaultCacheManager defaultCacheManager = new DefaultCacheManager(embeddedBuilder.build());
        /*
         * Use the following for XML configuration
           InputStream is = SimpleEmbeddedHotRodServer.class.getResourceAsStream("/infinispan.xml");
           DefaultCacheManager defaultCacheManager = new DefaultCacheManager(is);
        */
      Cache<String, String> embeddedCache = defaultCacheManager.getCache();

      HotRodServerConfiguration build = new HotRodServerConfigurationBuilder().build();
      HotRodServer server = new HotRodServer();
      server.start(build, defaultCacheManager);

      ConfigurationBuilder remoteBuilder = new ConfigurationBuilder();
      remoteBuilder.nearCache().mode(NearCacheMode.INVALIDATED).maxEntries(10);
      remoteBuilder.addServers("localhost");
      RemoteCacheManager remoteCacheManager = new RemoteCacheManager(remoteBuilder.build());
      RemoteCache<String, String> remoteCache = remoteCacheManager.getCache();

      System.out.print("\nInserting data into embedded cache...");
      int i = 1;
      for(char ch='a'; ch<='a'; ch++) {
         String s = Character.toString(ch);
         embeddedCache.put(s, s);
         System.out.printf("%s...", s);
      }

      System.out.print("\nVerifying data in remote cache...");
      for(char ch='a'; ch<='a'; ch++) {
         String s = Character.toString(ch);
         //assert s.equals(remoteCache.get(s));
         System.out.printf("%s...", remoteCache.get(s));
         //System.out.printf("%s...", s);
      }

      System.out.println("\nDone !");
      remoteCacheManager.stop();
      server.stop();
      defaultCacheManager.stop();
   }

}
