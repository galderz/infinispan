package org.infinispan.notifications.cachemanagerlistener.event;

import org.infinispan.manager.CacheManager;
import org.infinispan.remoting.transport.Address;

import java.util.List;

/**
 * Implementation of cache manager events
 *
 * @author Manik Surtani
 * @since 4.0
 */
public class EventImpl implements CacheStartedEvent, CacheStoppedEvent, ViewChangedEvent {

   String cacheName;
   CacheManager cacheManager;
   Type type;
   List<Address> newMembers, oldMembers;
   Address localAddress;
   int viewId;

   public EventImpl() {
   }

   public EventImpl(String cacheName, CacheManager cacheManager, Type type, List<Address> newMemberList, List<Address> oldMemberList, Address localAddress, int viewId) {
      this.cacheName = cacheName;
      this.cacheManager = cacheManager;
      this.type = type;
      this.newMembers = newMemberList;
      this.oldMembers = oldMemberList;
      this.localAddress = localAddress;
      this.viewId = viewId;
   }

   public String getCacheName() {
      return cacheName;
   }

   public void setCacheName(String cacheName) {
      this.cacheName = cacheName;
   }

   public CacheManager getCacheManager() {
      return cacheManager;
   }

   public void setCacheManager(CacheManager cacheManager) {
      this.cacheManager = cacheManager;
   }

   public Type getType() {
      return type;
   }

   public void setType(Type type) {
      this.type = type;
   }

   public List<Address> getNewMembers() {
      return newMembers;
   }

   public void setNewMembers(List<Address> newMembers) {
      this.newMembers = newMembers;
   }

   public void setOldMembers(List<Address> oldMembers) {
      this.oldMembers = oldMembers;
   }

   public List<Address> getOldMembers() {
      return this.oldMembers;
   }

   public Address getLocalAddress() {
      return localAddress;
   }

   public int getViewId() {
      return viewId;
   }

   public void setViewId(int viewId) {
      this.viewId = viewId;
   }

   public void setLocalAddress(Address localAddress) {
      this.localAddress = localAddress;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      EventImpl event = (EventImpl) o;

      if (viewId != event.viewId) return false;
      if (cacheName != null ? !cacheName.equals(event.cacheName) : event.cacheName != null) return false;
      if (localAddress != null ? !localAddress.equals(event.localAddress) : event.localAddress != null) return false;
      if (newMembers != null ? !newMembers.equals(event.newMembers) : event.newMembers != null)
         return false;
      if (oldMembers != null ? !oldMembers.equals(event.oldMembers) : event.oldMembers != null)
         return false;
      if (type != event.type) return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = cacheName != null ? cacheName.hashCode() : 0;
      result = 31 * result + (type != null ? type.hashCode() : 0);
      result = 31 * result + (newMembers != null ? newMembers.hashCode() : 0);
      result = 31 * result + (oldMembers != null ? oldMembers.hashCode() : 0);
      result = 31 * result + (localAddress != null ? localAddress.hashCode() : 0);
      result = 31 * result + viewId;
      return result;
   }

   @Override
   public String toString() {
      return "EventImpl{" +
            "cacheName='" + cacheName + '\'' +
            ", type=" + type +
            ", newMembers=" + newMembers +
            ", oldMembers=" + oldMembers +
            ", localAddress=" + localAddress +
            ", viewId=" + viewId +
            '}';
   }
}
