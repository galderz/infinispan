package org.infinispan.config;

import org.infinispan.test.TestingUtil;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.infinispan.test.TestingUtil.INFINISPAN_END_TAG;
import static org.infinispan.test.TestingUtil.INFINISPAN_START_TAG;

@Test(groups = "unit", testName = "config.MinimalConfigurationParsingTest")
public class MinimalConfigurationParsingTest {
   public void testGlobalAndDefaultSection() throws IOException {
      String xml =  INFINISPAN_START_TAG +
            "    <global />\n" +
            "    <default>\n" +
            "        <locking concurrencyLevel=\"10000\" isolationLevel=\"READ_COMMITTED\" />\n" +
            "    </default>\n" +
            INFINISPAN_END_TAG;
      testXml(xml);
   }

   public void testNoGlobalSection() throws IOException {
      String xml = INFINISPAN_START_TAG +
            "    <default>\n" +
            "        <locking concurrencyLevel=\"10000\" isolationLevel=\"READ_COMMITTED\" />\n" +
            "    </default>\n" +
            INFINISPAN_END_TAG;
      testXml(xml);
   }

   public void testNoDefaultSection() throws IOException {
      String xml = INFINISPAN_START_TAG +
            "    <global />\n" +
            INFINISPAN_END_TAG;
      testXml(xml);
   }

   public void testNoSections() throws IOException {
      String xml = INFINISPAN_START_TAG + INFINISPAN_END_TAG;
      testXml(xml);
   }

   private void testXml(String xml) throws IOException {
      InputStream stream = new ByteArrayInputStream(xml.getBytes());
      InfinispanConfiguration ic = InfinispanConfiguration.newInfinispanConfiguration(stream);
      assert ic != null;
   }
}
