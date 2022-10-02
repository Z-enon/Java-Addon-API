package com.xenon.plugin;

/**
 * @author Zenon
 */
@FunctionalInterface
public interface Hook<T> {

    boolean call(T state);
}
