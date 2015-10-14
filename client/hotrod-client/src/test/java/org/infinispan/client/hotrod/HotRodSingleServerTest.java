package org.infinispan.client.hotrod;

import org.infinispan.client.hotrod.test.HotRodClientTestingUtil;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.server.hotrod.HotRodServer;
import org.infinispan.server.hotrod.configuration.HotRodServerConfigurationBuilder;
import org.infinispan.test.AbstractInfinispanTest;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.Test;

import static org.infinispan.server.hotrod.test.HotRodTestingUtil.hotRodCacheConfiguration;

@Test(testName = "client.hotrod.HotRodSingleServerTest", groups = "functional")
public class HotRodSingleServerTest extends AbstractInfinispanTest {

   public void test000() throws Exception {
      EmbeddedCacheManager cm = TestCacheManagerFactory.createCacheManager(hotRodCacheConfiguration());
      HotRodServer server = HotRodClientTestingUtil.startHotRodServer(cm, 11222, new HotRodServerConfigurationBuilder());
      Thread.sleep(600000);
      server.stop();
      cm.stop();
   }

}
