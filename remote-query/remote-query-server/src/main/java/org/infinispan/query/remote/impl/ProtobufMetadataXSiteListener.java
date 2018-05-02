package org.infinispan.query.remote.impl;

import org.infinispan.Cache;
import org.infinispan.commons.logging.LogFactory;
import org.infinispan.configuration.cache.BackupConfiguration;
import org.infinispan.query.remote.impl.logging.Log;
import org.infinispan.xsite.XSiteAdminOperations;
import org.jgroups.protocols.relay.RouteStatusListener;

public class ProtobufMetadataXSiteListener implements RouteStatusListener {

   private static final Log log = LogFactory.getLog(ProtobufMetadataXSiteListener.class, Log.class);

   private final Cache<String, String> protobufSchemaCache;

   public ProtobufMetadataXSiteListener(Cache<String, String> protobufSchemaCache) {
      this.protobufSchemaCache = protobufSchemaCache;
   }

   @Override
   public void sitesUp(String... sites) {
      // Start x-site state transfer
      XSiteAdminOperations xsiteAdminOperations =
         SecurityActions.getComponentRegistry(protobufSchemaCache.getAdvancedCache())
            .getComponent(XSiteAdminOperations.class);

      final org.infinispan.configuration.cache.Configuration cfg =
         SecurityActions.getCacheConfiguration(protobufSchemaCache.getAdvancedCache());

      cfg.sites().enabledBackups().stream()
         .map(BackupConfiguration::site)
         .forEach(siteName -> {
            log.debugf("Automatically pushing protobuf metadata state to '%s' site", siteName);
            xsiteAdminOperations.pushState(siteName);
         });
   }

   @Override
   public void sitesDown(String... sites) {
      // No-op
   }

}
