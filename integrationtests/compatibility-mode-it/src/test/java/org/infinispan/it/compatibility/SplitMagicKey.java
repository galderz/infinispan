package org.infinispan.it.compatibility;

import org.infinispan.Cache;

import java.io.Serializable;
import java.util.Random;

import static org.infinispan.client.hotrod.test.HotRodClientTestingUtil.toBytes;
import static org.infinispan.distribution.DistributionTestHelper.addressOf;
import static org.infinispan.distribution.DistributionTestHelper.isFirstOwner;

/**
 * A magic key whose pojo version will be assigned to the specified embedded
 * cache, but it's binary format, it's marshalled to a different Hot Rod
 * server.
 *
 * Such type of keys can be found when compatibility mode is enabled,
 * and the keys have slightly hashing depending on whether the hashing is done
 * on the pojo vs the binary format. Ideally, such scenarios should be avoided.
 */
public class SplitMagicKey implements Serializable {

   /**
    * The name is used only for easier debugging and may be null. It is not part of equals()/hashCode().
    */
   private final String name;

   private final String addr;
   private final int hashcode;
   private final int segment;

   public SplitMagicKey(String name, Cache<?, ?> ownerAndNonOwner) {
      this.name = name;
      addr = addressOf(ownerAndNonOwner).toString();
      Random r = new Random();
      Object dummy;
      byte[] dummyBytes;
      int attemptsLeft = 1000;
      boolean ownerPojoFound = false;
      boolean nonOwnerBytesFound = false;
      do {
         // create a dummy object with this hashcode
         final int hc = r.nextInt();
         dummy = new Integer(hc);
         dummyBytes = toBytes(dummy);
         attemptsLeft--;
         ownerPojoFound = isFirstOwner(ownerAndNonOwner, dummy);
         nonOwnerBytesFound = !isFirstOwner(ownerAndNonOwner, dummyBytes);
      } while (!(ownerPojoFound && nonOwnerBytesFound) && attemptsLeft >= 0);

      if (attemptsLeft < 0) {
         throw new IllegalStateException("Could not find any key owned(pojo) and non-owned(bytes) by " + ownerAndNonOwner);
      }
      // we have found a hashcode that works!
      hashcode = dummy.hashCode();
      segment = ownerAndNonOwner.getAdvancedCache().getDistributionManager().getReadConsistentHash().getSegment(this);
   }

   @Override
   public int hashCode () {
      return hashcode;
   }

   @Override
   public boolean equals (Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      SplitMagicKey magicKey = (SplitMagicKey) o;

      return hashcode == magicKey.hashcode && addr.equals(magicKey.addr);
   }

   @Override
   public String toString() {
      return "SplitMagicKey#" + name + '{' + Integer.toHexString(hashcode)
            + '@' + addr + '/' + segment + "}";
   }

}
