package com.xenon.plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.file.Path;

/**
 *
 * @param path
 * @param name
 * @param mainClassPath
 * @param mainClassName
 * @param logo
 * @param hashcode
 * @author Zenon
 */
public record Plugin(@NotNull Path path, @NotNull String name, @NotNull Path mainClassPath, @NotNull String mainClassName,
                     @Nullable ByteBuffer logo, int hashcode) {

    /**
     * Creates a wrapper for a plugin. Computes main class path by effectively doing
     * {@code path.resolve(mainClassName.replaceAll("\\.", separator))}.
     * @param path a path to the plugin JAR
     * @param name the plugin's name
     * @param separator the JAR File system separator
     * @param mainClassName the java name of the plugin's main class (Ex: net.mypck.main.Main)
     * @param logo the plugin logo
     * @return a new Plugin wrapper
     */
    @NotNull
    public static Plugin wrap(@NotNull Path path, @NotNull String name,
                              @NotNull String separator, @NotNull String mainClassName,
                              @Nullable ByteBuffer logo) {
        return new Plugin(
                path,
                name,
                path.resolve(mainClassName.replaceAll("\\.", separator)),
                mainClassName,
                logo,
                name.hashCode()
        );
    }


    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Plugin && ((Plugin) o).name.equals(name));
    }

    @Override
    public int hashCode() {
        return hashcode;
    }
}
