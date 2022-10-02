# Java-Addon-API
A addon API in Java for reference only

An humble attempt to make use of hidden classes for a plugin-like system, which was an humongous setback. Since I switched to C for my project out of frustration, I didn't even do a simple test for this API. I guess it's good if you're interested in knowing how to load classes in a foreign JAR **using nio** (not the dirty trick with `URLClassLoader::addURL`; though I guess Java is still broken when it comes down to very specific stuff).

## What is this API even supposed to do?

a.k.a. Why don't you just use OSGi already?🙄

I know it doesn't happen that often, but sometimes, you need a modular, super extensible application. And what's more, with Quality Of Life features!
With that, you can easily forget about 80% the websites Google showed you.

The QOL features include:

- Performance (so obviously no reflection in hot loop, but that should go without saying)
- Hook system: A plugin doesn't have to implement all abstract methods and can instead implement hooks as it wishes. (much like ExtensionPoints in PF4J)
- No restart required for loading a plugin: plugins can be loaded/unloaded dynamically at runtime. (though a restart may be needed depending on what the plugin does)
- Stylish code (not dirty tricks hidden behind encapsulation), for myself

The above imply a few things:

- No OSGi.
- No inlining with ASM because of flexibility (each time you create a HookListener, you need to write an ASM inlining method)
- No `URLClassLoader::addURL` with the help of reflection

### First Option

Much like `URLClassLoader::addURL`, but with custom class loader. Easy to write. Very easy for memory leak to happen.

### Second Option

Define nothing but hidden classes that can be "unloaded" (they aren't actually loaded to start with) without needing their classloader to be gc-ed first.
While the entry point of a plugin only registers lambdas (which are hidden classes), the entry point itself has to be loaded. Defining it as hidden class it feasible if it wasn't for the fact that it's in another package, let alone another JAR.

`MethodHandles.Lookup#defineHiddenClass()` performs very few checks, but sadly it does a fatal one: It checks if the package of the calling class is the same as what we are trying to define. We can really see that it's designed to create lambdas and nothing else.

Hacking this layer of checks is quite easy, but then the actual class definition is delegated to `SharedsSecrets` and, uhh... I don't want to mess with that.

### Not an Option

Java Agents and Instrumentation API

Well, it's designed to **add** bytecode, not **unload** some. With Java, you truly wish you had unlimited RAM.

End Of ~~File~~ Tantrum
