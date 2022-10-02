package com.xenon;

import org.jetbrains.annotations.NotNull;

/**
 * @author Zenon
 */
public interface Logger {

    @NotNull
    static Logger forclass(Class<?> clazz) {
        return new Logger() {
            @Override
            public void log(String msg, Object... args) {
                System.out.printf((msg) + "%n", args);
            }

            @Override
            public void warn(String msg, Object... args) {
                System.err.printf((msg) + "%n", args);
            }

            @Override
            public void error(String msg, Object... args) {
                System.err.printf((msg) + "%n", args);
            }

            @Override
            public void fatal(String msg, Object... args) {
                System.err.printf((msg) + "%n", args);
            }
        };
    }

    void log(String msg, Object... args);

    void warn(String msg, Object... args);

    void error(String msg, Object... args);

    void fatal(String msg, Object... args);

}
