/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.config;

import org.infinispan.config.Configuration.AsyncType;
import org.infinispan.config.Configuration.BooleanAttributeType;
import org.infinispan.config.Configuration.ClusteringType;
import org.infinispan.config.Configuration.CustomInterceptorsType;
import org.infinispan.config.Configuration.DeadlockDetectionType;
import org.infinispan.config.Configuration.EvictionType;
import org.infinispan.config.Configuration.ExpirationType;
import org.infinispan.config.Configuration.HashType;
import org.infinispan.config.Configuration.L1Type;
import org.infinispan.config.Configuration.LockingType;
import org.infinispan.config.Configuration.QueryConfigurationBean;
import org.infinispan.config.Configuration.StateRetrievalType;
import org.infinispan.config.Configuration.SyncType;
import org.infinispan.config.Configuration.TransactionType;
import org.infinispan.config.Configuration.UnsafeType;
import org.infinispan.config.GlobalConfiguration.FactoryClassWithPropertiesType;
import org.infinispan.config.GlobalConfiguration.GlobalJmxStatisticsType;
import org.infinispan.config.GlobalConfiguration.SerializationType;
import org.infinispan.config.GlobalConfiguration.ShutdownType;
import org.infinispan.config.GlobalConfiguration.TransportType;
import org.infinispan.loaders.CacheLoaderConfig;
import org.infinispan.loaders.decorators.AsyncStoreConfig;
import org.infinispan.loaders.decorators.SingletonStoreConfig;

/**
 * ConfigurationBeanVisitor implementations are passed through InfinispanConfiguration object tree
 * visiting each configuration element of InfinispanConfiguration instance.
 * <p>
 * 
 * AbstractConfigurationBeanVisitor is a convenience super class for all implementations of
 * ConfigurationBeanVisitor. Most of the time, custom visitors should extend
 * AbstractConfigurationBeanVisitor rather than implement ConfigurationBeanVisitor
 * 
 * 
 * 
 * @author Vladimir Blagojevic
 * @see AbstractConfigurationBeanVisitor
 * @since 4.0
 */
public interface ConfigurationBeanVisitor { 
   
   void visitInfinispanConfiguration(InfinispanConfiguration bean);
   
   void visitGlobalConfiguration(GlobalConfiguration bean);
   
   void visitFactoryClassWithPropertiesType(FactoryClassWithPropertiesType bean);
   
   void visitGlobalJmxStatisticsType(GlobalJmxStatisticsType bean);
   
   void visitSerializationType(SerializationType bean);
   
   void visitShutdownType(ShutdownType bean);
   
   void visitTransportType(TransportType bean);
   
   void visitConfiguration(Configuration bean);
   
   void visitAsyncType(AsyncType bean);
   
   void visitBooleanAttributeType(BooleanAttributeType bean);
   
   void visitClusteringType(ClusteringType bean);
   
   void visitCustomInterceptorsType(CustomInterceptorsType bean);
   
   void visitDeadlockDetectionType(DeadlockDetectionType bean);
   
   void visitEvictionType(EvictionType bean);
   
   void visitExpirationType(ExpirationType bean);
   
   void visitHashType(HashType bean);
   
   void visitL1Type(L1Type bean);
   
   void visitQueryConfigurationBean(QueryConfigurationBean bean);
   
   void visitLockingType(LockingType bean);
      
   void visitStateRetrievalType(StateRetrievalType bean);
   
   void visitSyncType(SyncType bean);
   
   void visitTransactionType(TransactionType bean);
   
   void visitUnsafeType(UnsafeType bean);
   
   void visitCacheLoaderManagerConfig(CacheLoaderManagerConfig bean);
   
   void visitCacheLoaderConfig(CacheLoaderConfig bean);
   
   void visitSingletonStoreConfig(SingletonStoreConfig bean);
   
   void visitAsyncStoreConfig(AsyncStoreConfig bean);

   void visitCustomInterceptorConfig(CustomInterceptorConfig customInterceptorConfig);   

}
