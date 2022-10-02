package com.xenon.plugin;

import com.xenon.Core;
import com.xenon.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.security.SecureClassLoader;
import java.util.*;
import java.util.stream.*;

/**
 * Examples:
 * In the application code, at startup:
 * <pre><code>
 *     PluginManager.registerHookListener(AssetLoadingListener.singleton(), AssetLoadingState.class);
 *     PluginManager.registerHookListener(PayloadListener.singleton(), PayLoadContent.class);
 *     ...
 * </code></pre>
 *
 * In an add-on main method:
 * <pre><code>
 *     PluginManager.registerHook(MyBootstrapImpl::onAssetLoad, AssetLoadingState.class, Priority.LOW);
 *     PluginManager.registerHook(MyPacketImpl::onPayload, PayLoadContent.class, Priority.TOP);
 *     ...
 * </code></pre>
 *
 * where {@code AssetLoadingListener.singleton()} and {@code PayloadListener.singleton()}
 * are respectively something along the lines
 * {@code new HookListener<AssetLoadingState>(AssetLoadingState.singleton(), true)}
 * and
 * {@code new HookListener<PayLoadContent>(PayLoadContent.singleton(), false)}
 * {@code HookListener}
 * @author Zenon
 * @apiNote About class loading:
 * <ul>
 *     <li>Main class is retrieved via MANIFEST.MF and defined using
 *     {@link MethodHandles.Lookup#defineHiddenClass(byte[], boolean, MethodHandles.Lookup.ClassOption...)}.
 *     That way, its main function can be executed without any class loader.</li>
 *     <li>That main function is expected to contain only calls to
 *     {@link PluginManager#registerHook(Hook, Class, Priority)} for which {@code Class} and {@code Priority}
 *     are already loaded (API side) and {@code Hook} is a plugin-side lambda (so it is by nature a hidden class).</li>
 *     <li>To sum it up: Plugin's main class and entry point are only retained in
 *     {@link PluginManager#loadPlugin(Plugin)} body, and Plugin's hooks are being retained by
 *     {@link PluginManager#hooksByPlugin} and by {@link HookListener} and can be garbage-collected upon
 *     {@link PluginManager#unloadPlugin(Plugin)}.</li>
 * </ul>
 * <h3>EDIT:</h3>
 * Fetching class in foreign class using hidden classes is a failure, since it would require hacking into
 * {@code JavaLangAccess} to simulate {@code MethodHandles.Lookup.defineHiddenClass()}.
 * A classic Class Loader trick is therefore used (not the usual garbage URLClassLoader.addURL).
 */
public final class PluginManager {

    private static final String MAIN_CLASS_HINT = "Main-Class",
            PLUGIN_NAME_HINT = "XePlugin-Name",
            PLUGIN_LOGO_HINT = "XePlugin-Logo",
            MANIFEST_PATH = "META-INF/MANIFEST.MF";
    /**
     * The folder in which plugins are expected to be found.
     * Can be set by launching the application with the custom VM arg "-DxePluginFolder=whatever".
     * Set to the folder "plugins" by default.
     */
    public static final Path PLUGIN_FOLDER = Paths.get(Core.property("xePluginFolder", "plugins"));


    private static final Logger LOGGER = Logger.forclass(PluginManager.class);

    /**
     * Gives access to a hook listener from its state's class.
     */
    private static final Map<Class<?>, HookListener<?>> listenersByClass = new HashMap<>();

    /**
     * Gives access to a plugin's hooks as well as the corresponding state's class, for later access to hook listener.
     */
    private static final Map<Plugin, Map.Entry<Hook<?>, Class<?>>[]> hooksByPlugin = new HashMap<>();

    /**
     * Sole reference for a given plugin to its class loader.
     */
    private static final Map<Plugin, SecureClassLoader> classLoadersByPlugin = new HashMap<>();


    private static final Set<Plugin> loadedPlugins = new HashSet<>();
    @Nullable
    private static Set<Plugin> scannedPlugins;


    /**
     * @return a copy of all currently loaded plugins
     */
    @NotNull
    public static Set<Plugin> loadedPlugins_cpy() {
        return Set.copyOf(loadedPlugins);
    }

    /**
     * The user should ensure that {@link #scanForPlugins()} is called before this method.
     * @return a copy of all currently scanned yet unloaded plugins
     * @throws NullPointerException if {@link #scanForPlugins()} never got called before
     */
    @NotNull
    public static Set<Plugin> unloadedPlugins_cpy() {
        assert scannedPlugins != null;
        Set<Plugin> s = new HashSet<>(scannedPlugins);
        loadedPlugins.forEach(s::remove);
        return s;
    }

    /**
     * Updates the loaded plugins, that is:
     * <ul>
     *     <li>For each plugin in the old loaded plugins set that isn't present in the new, unload and remove them.</li>
     *     <li>For each plugin in the new loaded plugins set that wasn't present in the old, load and add them.</li>
     * </ul>
     * O(nlog(n)) thx to sets
     * @param newLoadedPlugins the new loaded plugins set
     * @return whether a restart should be performed (some hooks that were unloaded were critical)
     */
    public static boolean updateLoadedPluginsDiff(@NotNull Set<Plugin> newLoadedPlugins) {
        boolean b = false;
        for (Iterator<Plugin> i = loadedPlugins.iterator(); i.hasNext();) {
            Plugin s = i.next();
            if (!newLoadedPlugins.remove(s)) {
                b |= unloadPlugin(s);
                i.remove();
            }
        }
        for (Plugin p : newLoadedPlugins) {
            loadPlugin(p);
            loadedPlugins.add(p);
        }
        return b;
    }


    /**
     * Scans {@link #PLUGIN_FOLDER} looking for jars that are plugins.
     * The result is put in {@link #scannedPlugins}.
     */
    public static void scanForPlugins() {
        try (Stream<Path> fs = Files.list(PLUGIN_FOLDER)) {
            scannedPlugins = fs
                    .filter(Files::isRegularFile)
                    .filter(PluginManager::checkIsJar)
                    .map(PluginManager::candidate)
                    .filter(PluginCandidate::isPlugin)
                    .map(PluginCandidate::build)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean checkIsJar(Path file) {
        return file.toString().endsWith(".jar");
    }

    private static PluginCandidate candidate(Path path) {
        label:
        try (FileSystem sys = FileSystems.newFileSystem(path)) {
            //Path root = sys.getPath("./");
            Path manifest = sys.getPath("./").resolve(MANIFEST_PATH);

            try (Stream<String> lines = Files.lines(manifest)) {
                // 1st slot is main class; 2nd is plugin name; 3rd is plugin logo
                final String[] ptr_arr = new String[3];

                lines.map(line -> line.split(":"))
                        .filter(arr -> arr.length == 2)
                        .forEach(arr -> {
                            switch (arr[0]) {
                                case MAIN_CLASS_HINT -> ptr_arr[0] = arr[1].stripIndent();
                                case PLUGIN_NAME_HINT -> ptr_arr[1] = arr[1].stripIndent();
                                case PLUGIN_LOGO_HINT -> ptr_arr[2] = arr[1].stripIndent();
                            }
                        });

                if (ptr_arr[0] == null || ptr_arr[1] == null)
                    break label;

                if (Core.DEBUG)
                    LOGGER.log("Scanned the plugin: [name %1; logo %2] %s", ptr_arr, path);
                return new PluginCandidate(path, ptr_arr[1], sys.getSeparator(), ptr_arr[0], ptr_arr[2]);
            }

        } catch (Exception ignored) {}

        return PluginCandidate.NULL;
    }

    /**
     * A PluginCandidate wrapper is needed for JARs to prevent parsing the MANIFEST two times in
     * {@link #scanForPlugins()} (one for filtering valid JARs and a second for actually loading valid JARs).
     * @param path
     * @param name
     * @param separator
     * @param mainClassPath
     * @param logoPath
     */
    private record PluginCandidate(@NotNull Path path, @NotNull String name,
                                   @NotNull String separator, @NotNull String mainClassPath,
                                   @Nullable String logoPath) {

        @SuppressWarnings("all")
        private static final PluginCandidate NULL = new PluginCandidate(null, null, null, null, null);
        private boolean isPlugin() {
            return this != NULL;
        }

        @NotNull
        private Plugin build() {
            return Plugin.wrap(path, name, separator, mainClassPath, null);
        }
    }



    /**
     * Registers a new hook listener for plugins to use. User-side method.
     * Obviously, add-ons should never play with this method.
     * @param listener the listener to be added
     * @param clazz the class generic because Java doesn't keep track of one generic's class
     * @param <T> the type of the state the listener will be overseeing
     * @throws RuntimeException if a listener is already associated to the state
     */
    public static <T> void registerHookListener(@NotNull HookListener<T> listener, @NotNull Class<T> clazz) {
        var o = listenersByClass.put(clazz, listener);
        if (Core.DEBUG && o != null)
            throw new RuntimeException("Found duplicate hook listeners. That should never happen.");
    }

    @Nullable
    private static PluginBuilder currentPluginBuilder;

    /**
     * Registers a new hook from a plugin. The hook is automatically distributed to the correct hook listener.
     * Add-on (plugin) side method.
     * Since another object is created for each hook, do not call this function twice for the same hook
     * (it won't crash though, just going to be executed 2 times).
     * @param h the hook to be registered
     * @param clazz the class generic because Java doesn't keep track of one generic's class
     * @param priority the Hook priority. Hooks with higher priority will be called before lower ones
     * @param <T> the type of the state the hook will be editing
     */
    public static <T> void registerHook(@NotNull Hook<T> h, @NotNull Class<T> clazz, @NotNull Priority priority) {
        assert currentPluginBuilder != null;
        currentPluginBuilder.registerHook(h, clazz, priority);
    }


    /**
     * Loads a plugin, that is, executes its entry point.
     * @param plugin the plugin to load
     */
    private static void loadPlugin(Plugin plugin) {
        currentPluginBuilder = new PluginBuilder(plugin);

        try {

            var loader = new PluginLoader(plugin);
            Class<?> clazz = loader.loadClass(plugin.mainClassName());

            Method method = clazz.getDeclaredMethod("main", String[].class);
            method.invoke(null, (Object) null);

            classLoadersByPlugin.put(plugin, loader);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        currentPluginBuilder.build();
    }

    /**
     * Unloads a plugin, that is unloads all its hooks in the different listeners.
     * @param plugin the plugin to unload
     * @return whether a restart should be performed
     * @throws RuntimeException if the plugin is not currently loaded
     */
    private static boolean unloadPlugin(Plugin plugin) {
        if (Core.DEBUG)
            LOGGER.log("Unloading plugin %s", plugin);

        Map.Entry<Hook<?>, Class<?>>[] entries = hooksByPlugin.remove(plugin);

        if (entries == null)
            throw new RuntimeException("Trying to unload an unknown plugin");

        boolean[] ptr = new boolean[1];
        for (var entry : entries) {
            HookListener<?> listener = listenersByClass.get(entry.getValue());
            listener.removeHook(entry.getKey());
            ptr[0] |= listener.critical;
        }
        loadedPlugins.remove(plugin);

        classLoadersByPlugin.remove(plugin);

        return ptr[0];
    }


    @SuppressWarnings("unchecked")
    private static class PluginBuilder {

        private final List<Map.Entry<Hook<?>, Class<?>>> hooks = new ArrayList<>(16);

        private final Plugin plugin;

        private PluginBuilder(Plugin plugin) {
            if (Core.DEBUG)
                LOGGER.log("Building plugin %s...", plugin);
            this.plugin = plugin;
        }

        private <T> void registerHook(@NotNull Hook<T> h, @NotNull Class<T> clazz, @NotNull Priority priority) {
            if (Core.DEBUG)
                LOGGER.log("Registering hook for class %s and priority %s", clazz, priority);

            hooks.add(Map.entry(h, clazz));
            HookListener<T> listener = (HookListener<T>) listenersByClass.get(clazz);
            listener.registerHook(h, priority);
        }

        private void build() {
            if (Core.DEBUG)
                LOGGER.log("Finished building plugin %s", plugin);

            hooksByPlugin.put(plugin, hooks.stream().distinct().toArray(Map.Entry[]::new));
        }

    }

    /**
     * A class loader unique for every plugin.
     * To load a class, do {@code new PluginLoader(myPlugin).loadClass(mainClassName)}.
     */
    private static final class PluginLoader extends SecureClassLoader {

        private final Plugin plugin;

        private PluginLoader(Plugin plugin) {
            super(PluginManager.class.getClassLoader());
            this.plugin = plugin;
        }

        @Override
        protected Class<?> findClass(String ignored) throws ClassNotFoundException {
            try {
                byte[] buffer = Files.readAllBytes(plugin.mainClassPath());
                return defineClass(plugin.mainClassName(), buffer, 0, buffer.length);
            } catch (Exception e) {
                throw new ClassNotFoundException();
            }
        }

    }

}
