package com.xenon;

import org.jetbrains.annotations.NotNull;


/**
 * @author Zenon
 */
public interface Core{

    boolean DEBUG = property("xeDebug", false);

    /**
     * Retrieves the system property indicated by {@code nm} and returns it as a String.
     * @param nm the name of the property
     * @param defaultValue the default value
     * @return the value of the property or {@code defaultValue} if there is no matching property
     */
    static String property(@NotNull String nm, String defaultValue) {
        return System.getProperty(nm, defaultValue);
    }

    /**
     * Retrieves the system property indicated by {@code nm} and returns it as an integer.
     * Performs weak integer decoding that doesn't allow hexadecimal nor octal numbers.
     * @param nm the name of the property
     * @param defaultValue the default value
     * @return the value of the property or {@code defaultValue} if there is no matching property
     */
    static int property(@NotNull String nm, int defaultValue) {
        String s = System.getProperty(nm);
        return s == null ? defaultValue : Integer.parseInt(s);
    }

    /**
     * Retrieves the system property indicated by {@code nm} and returns it as a boolean.
     * @param nm the name of the property
     * @param defaultValue the default value
     * @return the value of the property or {@code defaultValue} if there is no matching property
     */
    static boolean property(@NotNull String nm, boolean defaultValue) {
        String s = System.getProperty(nm);
        return s == null ? defaultValue : Boolean.parseBoolean(s);
    }
}
