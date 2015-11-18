package org.infinispan.trace;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.scribe.ScribeSpanCollector;

import java.util.Random;

public class BraveTracer {

   public static Brave brave;

   private static ScribeSpanCollector collector;

   static {
      Brave.Builder builder = new Brave.Builder("infinispan");
      collector = new ScribeSpanCollector("192.168.99.100", 9410);
      brave = builder.spanCollector(collector).build();
   }

   public static void main(String[] args) {
      final Random R = new Random();
      ClientTracer ct = BraveTracer.brave.clientTracer();
      ServerTracer st = BraveTracer.brave.serverTracer();

//      ct.startNewSpan("client span name " + R.nextLong());
//      ct.setClientSent();
//      ct.setClientReceived();

      final String serverSpanName = "server span name " + R.nextLong();
      st.setStateCurrentTrace(R.nextLong(), R.nextLong(), R.nextLong(), serverSpanName);

      st.setServerReceived();

      st.submitAnnotation("custom annotation");

      // Simulate client.
      final ClientTracer clientTracer = brave.clientTracer();
      final String clientSpanName = "client span name " + R.nextLong();
      clientTracer.startNewSpan(clientSpanName);
      clientTracer.setClientSent();
      clientTracer.setClientReceived();

      st.setServerSend();

      collector.close();
   }

}
