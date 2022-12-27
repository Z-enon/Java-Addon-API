# Java-Addon-API
A addon API in Java for reference only

An humble attempt to make use of hidden classes for a plugin-like system, which was a huge setback. For demonstration purposes only.

## What is this supposed to do?

This project dealt with loading/unloading JAR dynamically at runtime, using Java nio API.

Features:

- Hook system: An addon doesn't have to implement all abstract methods and can instead implement hooks one at a time as it wishes. (much like ExtensionPoints in PF4J)
- No restart required to load a plugin: plugins can be loaded/unloaded dynamically at runtime. (though a "soft" restart may sometimes be needed. ex: if the plugin does critical things at boostrap)
- Java nio

The above imply a few things:

- No OSGi.
- No inlining with ASM because of flexibility (each time you create a HookListener, you need to write an ASM inlining method)
- No `URLClassLoader::addURL` with the help of reflection

### First Option

Much like `URLClassLoader::addURL`, but with custom class loader.

### Second Option

Define nothing but hidden classes that can be "unloaded" (they aren't actually loaded to start with) without needing their classloader to be gc-ed first.
While the entry point of a plugin only registers lambdas (which are hidden classes), the entry point itself has to be loaded. Defining it as hidden class could be feasible if it wasn't for the fact that it's in another package, let alone another JAR.

`MethodHandles.Lookup#defineHiddenClass()` performs very few checks, but sadly it does a fatal one: It checks if the calling class is in the same package as what we are trying to define, so it's definitely not the way to go to load external JARs.

### Not an Option

Java Agents and Instrumentation API

It is inherently designed to **load** bytecode, not **unload** some.

## Concretely

This project showcases the *First Option*.
