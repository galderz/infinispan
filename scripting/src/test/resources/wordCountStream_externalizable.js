// mode=distributed,language=javascript
var Function = Java.type("java.util.function.Function")
var Externalizable = Java.type("java.io.Externalizable")
var Collectors = Java.type("java.util.stream.Collectors")
var Arrays = Java.type("org.infinispan.scripting.utils.JSArrays")
var CacheCollectors = Java.type("org.infinispan.stream.CacheCollectors");
var ExternalizableFunction = Java.extend(Function, Externalizable);
var Supplier = Java.extend(Java.type("java.util.function.Supplier"))
var ExternalizableSupplier = Java.extend(Supplier, Externalizable);
var e = new ExternalizableFunction( {
   apply: function(object) {
      return object.getValue().toLowerCase().split(/[\W]+/)
   }
})
var f = new ExternalizableFunction({
   apply: function(f) {
      return Arrays.stream(f)
   }
})
var s = new ExternalizableSupplier({
   get: function() {
      return Collectors.groupingBy(Function.identity(), Collectors.counting())
   }
})
cache.entrySet().stream().map(e)
  .flatMap(f)
  .collect(CacheCollectors.serializableCollector(s));
