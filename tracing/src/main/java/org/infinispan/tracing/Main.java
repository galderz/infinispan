package org.infinispan.tracing;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.SpanCollector;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.scribe.ScribeSpanCollector;

public class Main {

   public static void main(String[] args) throws InterruptedException {
      SpanCollector collector = new ScribeSpanCollector("192.168.99.100", 9410);

//      Brave service1 = new Brave.Builder("service-1").spanCollector(collector).build();
//
//      Syst
//      service1.serverTracer().setStateCurrentTrace();
//
//      ServerTracer st = brave.serverTracer();
//      ClientTracer ct = brave.clientTracer();
//
//      SpanId spanId = ct.startNewSpan("client-req-" + System.currentTimeMillis());
//      ct.setClientSent();
//
//      st.setStateCurrentTrace(spanId.getTraceId(), spanId.getSpanId(), null, "server-request");
//      st.setServerReceived();
//      st.submitAnnotation("begin sleep marker");
//      st.submitBinaryAnnotation("Some Interesting Contaxt Value", "session id is 123");
//      Thread.sleep(250);
//      st.submitAnnotation("end sleep marker");
//      Thread.sleep(250);
//      st.setServerSend();

//      // Sub request 1
//      SpanId subSpanId1 = ct.startNewSpan("sub-1-client-req-" + System.currentTimeMillis());
//      ct.setClientSent();
//
//      // Sub request 2
//      SpanId subSpanId2 = ct.startNewSpan("sub-2-client-req-" + System.currentTimeMillis());
//      ct.setClientSent();

//      ct.setClientReceived();
   }



//   public static void main(String[] args) throws InterruptedException {
//      Brave.Builder builder = new Brave.Builder("brave-main");
//      SpanCollector collector = new ScribeSpanCollector("192.168.99.100", 9410);
//      Brave brave = builder.spanCollector(collector).build();
//
//      ServerTracer st = brave.serverTracer();
//      ClientTracer ct = brave.clientTracer();
//
//      SpanId spanId = ct.startNewSpan("client-req-" + System.currentTimeMillis());
//      ct.setClientSent();
//
//      st.setStateCurrentTrace(spanId.getTraceId(), spanId.getSpanId(), null, "server-request");
//      st.setServerReceived();
//      st.submitAnnotation("begin sleep marker");
//      st.submitBinaryAnnotation("Some Interesting Contaxt Value", "session id is 123");
//      Thread.sleep(250);
//      st.submitAnnotation("end sleep marker");
//      Thread.sleep(250);
//      st.setServerSend();
//
////      // Sub request 1
////      SpanId subSpanId1 = ct.startNewSpan("sub-1-client-req-" + System.currentTimeMillis());
////      ct.setClientSent();
////
////      // Sub request 2
////      SpanId subSpanId2 = ct.startNewSpan("sub-2-client-req-" + System.currentTimeMillis());
////      ct.setClientSent();
//
//      ct.setClientReceived();
//   }

}
