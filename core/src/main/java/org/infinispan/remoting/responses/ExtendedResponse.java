/*
* JBoss, Home of Professional Open Source
* Copyright 2005, JBoss Inc., and individual contributors as indicated
* by the @authors tag. See the copyright.txt in the distribution for a
* full listing of individual contributors.
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
package org.infinispan.remoting.responses;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.infinispan.marshall.Marshallable;
import org.infinispan.marshall.Ids;

/**
 * A response with extended information
 *
 * @author Jason T. Greene
 */
@Marshallable(externalizer = ExtendedResponse.Externalizer.class, id = Ids.EXTENDED_RESPONSE)
public class ExtendedResponse extends ValidResponse {
   private final boolean replayIgnoredRequests;
   private final Response response;

   public ExtendedResponse(Response response, boolean replayIgnoredRequests) {
      this.response = response;
      this.replayIgnoredRequests = replayIgnoredRequests;
   }

   public boolean isReplayIgnoredRequests() {
      return replayIgnoredRequests;
   }

   public Response getResponse() {
      return response;
   }

   public boolean isSuccessful() {
      return response.isSuccessful();
   }
   
   public static class Externalizer implements org.infinispan.marshall.Externalizer {
      public void writeObject(ObjectOutput output, Object subject) throws IOException {
         ExtendedResponse er = (ExtendedResponse) subject;
         output.writeBoolean(er.replayIgnoredRequests);
         output.writeObject(er.response);
      }

      public Object readObject(ObjectInput input) throws IOException, ClassNotFoundException {
         boolean replayIgnoredRequests = input.readBoolean();
         Response response = (Response) input.readObject();
         return new ExtendedResponse(response, replayIgnoredRequests);
      }
   }
}
