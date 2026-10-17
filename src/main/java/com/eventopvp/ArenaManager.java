package com.eventopvp;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Gestiona los mundos del evento: el lobby y las arenas (una por mundo).
 * Al arrancar, carga los mundos configurados y, si no existen, los copia desde
 * {@code plugins/EventoPvP/world-templates/<nombre>/} (o {@code <nombre>.zip}) o los crea vacíos.
 */
final class ArenaManager {
    /** Carpeta dentro de la data del plugin donde se dejan los mundos base. */
    static final String TEMPLATES = "world-templates";

    private final EventoPvP plugin;
    private final File file;
    private final Map<String, List<String>> spawns = new LinkedHashMap<>();
    private final Map<String, String> envs = new LinkedHashMap<>();
    private String lobbyRaw;

    ArenaManager(EventoPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
        load();
    }

    // ------------------------------------------------------------ persistencia

    void load() {
        spawns.clear();
        envs.clear();
        lobbyRaw = null;
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        lobbyRaw = y.getString("lobby");
        ConfigurationSection es = y.getConfigurationSection("envs");
        if (es != null) {
            for (String name : es.getKeys(false)) envs.put(name, es.getString(name));
        }
        ConfigurationSection sec = y.getConfigurationSection("arenas");
        if (sec == null) return;
        for (String name : sec.getKeys(false)) {
            spawns.put(name, new ArrayList<>(y.getStringList("arenas." + name + ".spawns")));
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        if (lobbyRaw != null) y.set("lobby", lobbyRaw);
        for (Map.Entry<String, String> en : envs.entrySet()) y.set("envs." + en.getKey(), en.getValue());
        for (Map.Entry<String, List<String>> en : spawns.entrySet()) {
            y.set("arenas." + en.getKey() + ".spawns", en.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar arenas.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------ configuración

    String lobbyWorldName() {
        return plugin.getConfig().getString("worlds.lobby", "evento_lobby");
    }

    /** Nombres de arena configurados (cada uno es un mundo). */
    List<String> arenaNames() {
        List<String> out = new ArrayList<>();
        for (String s : plugin.getConfig().getStringList("worlds.arenas")) {
            String n = s == null ? "" : s.trim();
            if (!n.isEmpty() && !out.contains(n)) out.add(n);
        }
        return out;
    }

    // ------------------------------------------------------------ instalación

    /** Carga el lobby y las arenas (copiándolos/creándolos si faltan) y prepara spawns y lobby. */
    void install() {
        File tpl = new File(plugin.getDataFolder(), TEMPLATES);
        if (!tpl.isDirectory()) tpl.mkdirs();
        File readme = new File(tpl, "LEEME.txt");
        if (!readme.exists()) writeResource("world-templates/LEEME.txt", readme);

        String lobby = lobbyWorldName();
        ensureWorld(lobby);
        List<String> ready = new ArrayList<>();
        for (String arena : arenaNames()) {
            if (ensureWorld(arena) != null) ready.add(arena);
        }
        autoSpawns();
        ensureLobby();
        for (String a : arenaNames()) {
            if (spawnCount(a) == 0) {
                plugin.getLogger().warning("La arena '" + a + "' no tiene puntos de spawn; usa /evento spawn " + a);
            }
        }
        plugin.getLogger().info("Mundos del evento: lobby=" + lobby + " arenas=" + ready);
    }

    /**
     * Devuelve el mundo cargado (lo carga si ya existe la carpeta), o lo copia desde
     * world-templates/ o lo crea vacío. Nunca devuelve null salvo que no se pueda obtener.
     */
    World ensureWorld(String name) {
        World w = loadWorld(name);
        applyDifficulty(w);
        return w;
    }

    /**
     * Aplica a un mundo la dificultad configurada ({@code world-difficulty}). Sirve para que ningún
     * mapa importado quede en Peaceful, donde la vida se regenera sola y el hambre nunca baja.
     */
    private void applyDifficulty(World w) {
        if (w == null) return;
        Difficulty d = configuredDifficulty();
        if (w.getDifficulty() != d) {
            w.setDifficulty(d);
            plugin.getLogger().info("Mundo '" + w.getName() + "': dificultad ajustada a " + d + ".");
        }
    }

    private Difficulty configuredDifficulty() {
        String s = plugin.getConfig().getString("world-difficulty", "NORMAL");
        try {
            return Difficulty.valueOf(s == null ? "NORMAL" : s.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("world-difficulty inválida '" + s + "'; se usa NORMAL.");
            return Difficulty.NORMAL;
        }
    }

    private World loadWorld(String name) {
        if (name == null || name.isBlank()) return null;
        World w = Bukkit.getWorld(name);
        if (w != null) return w;
        File container = Bukkit.getWorldContainer();
        File dir = new File(container, name);

        // Paper 26.x migra los mundos antiguos a <nivel>/dimensions/minecraft/<nombre> y borra la
        // carpeta antigua. Si el mundo YA está migrado hay que cargarlo por su clave
        // (WorldCreator.ofKey); con el constructor String, Paper lo trata como mundo viejo, vuelve a
        // crear/migrar la carpeta y falla con "Refusing to overwrite existing migrated file".
        File migrated = migratedDimensionDir(name);
        if (migrated != null) {
            if (dir.isDirectory() && !isLoadedWorldFolder(dir)) {
                File backup = new File(container, name + ".legacy");
                for (int i = 1; backup.exists(); i++) backup = new File(container, name + ".legacy" + i);
                if (!dir.renameTo(backup)) {
                    plugin.getLogger().warning("El mundo '" + name + "' ya estaba migrado pero no se pudo mover la carpeta antigua '" + name + "'.");
                    return null;
                }
                plugin.getLogger().info("El mundo '" + name + "' ya estaba migrado; carpeta antigua movida a '" + backup.getName() + "'.");
            }
            // No se extrae ni se recrea nada: el mundo ya está instalado, solo se carga.
            World created = WorldCreator.ofKey(NamespacedKey.minecraft(name))
                    .environment(storedEnv(name, migrated))
                    .createWorld();
            if (created != null) {
                rememberEnv(name, created.getEnvironment());
                plugin.getLogger().info("Mundo '" + name + "' ya instalado: cargado sin tocar sus archivos.");
            } else {
                plugin.getLogger().warning("No se pudo cargar el mundo ya instalado '" + name + "'.");
            }
            return created;
        }

        if (dir.isDirectory()) {
            // El mundo ya está subido al servidor: por si quedó anidado (mundo/mundo/...).
            try {
                flattenWorldDir(dir);
            } catch (IOException ex) {
                plugin.getLogger().warning("No se pudo aplanar el mundo '" + name + "': " + ex.getMessage());
            }
        } else {
            File tplDir = new File(new File(plugin.getDataFolder(), TEMPLATES), name);
            File tplZip = new File(new File(plugin.getDataFolder(), TEMPLATES), name + ".zip");
            try {
                if (tplDir.isDirectory()) {
                    copyDir(resolveWorldRoot(tplDir).toPath(), dir.toPath());
                    plugin.getLogger().info("Mundo '" + name + "' copiado desde " + TEMPLATES + "/" + name);
                } else if (tplZip.isFile()) {
                    unzip(tplZip, dir);
                    flattenWorldDir(dir);
                    plugin.getLogger().info("Mundo '" + name + "' descomprimido desde " + TEMPLATES + "/" + name + ".zip");
                } else if (installEmbedded(name, dir)) {
                    // El mundo viene incluido en el propio plugin (worlds/<nombre>.zip).
                } else if (plugin.getConfig().getBoolean("create-world-if-missing", true)) {
                    World created = new WorldCreator(name).type(WorldType.FLAT).generateStructures(false).createWorld();
                    if (created != null) rememberEnv(name, created.getEnvironment());
                    plugin.getLogger().info("Mundo '" + name + "' creado vacío (plano).");
                    return created;
                } else {
                    plugin.getLogger().warning("Falta el mundo '" + name + "': súbelo o deja sus archivos en " + TEMPLATES + "/.");
                    return null;
                }
            } catch (IOException ex) {
                plugin.getLogger().warning("No se pudo preparar el mundo '" + name + "': " + ex.getMessage());
                return null;
            }
        }
        World.Environment env = environmentOf(dir);
        World loaded = new WorldCreator(name).environment(env).createWorld();
        if (loaded != null) {
            rememberEnv(name, loaded.getEnvironment());
            if (env != World.Environment.NORMAL) {
                plugin.getLogger().info("Mundo '" + name + "' cargado con dimensión " + env + ".");
            }
        }
        return loaded;
    }

    /**
     * Carpeta de dimensión de un mundo en el formato actual del servidor (Paper 26.x:
     * {@code <nivel>/dimensions/minecraft/<nombre>}), si el mundo ya fue migrado. Devuelve null
     * en servidores que aún usan una carpeta de mundo aparte.
     */
    private File migratedDimensionDir(String name) {
        // 1) Carpetas de los mundos ya cargados (el nivel principal suele ser el primero).
        for (World w : Bukkit.getWorlds()) {
            File dim = dimensionDir(w.getWorldFolder(), name);
            if (dim != null) return dim;
        }
        File container = Bukkit.getWorldContainer();
        // 2) La propia carpeta del mundo, por si el servidor la conserva.
        File own = dimensionDir(new File(container, name), name);
        if (own != null) return own;
        // 3) Cualquier carpeta de primer nivel (cubre niveles con nombres distintos al esperado).
        File[] subs = container.listFiles(File::isDirectory);
        if (subs != null) {
            for (File sub : subs) {
                File dim = dimensionDir(sub, name);
                if (dim != null) return dim;
            }
        }
        return null;
    }

    /** {@code <base>/dimensions/minecraft/<nombre>} si existe. */
    private static File dimensionDir(File base, String name) {
        if (base == null) return null;
        File dim = new File(base, "dimensions" + File.separator + "minecraft" + File.separator + name);
        return dim.isDirectory() ? dim : null;
    }

    /** Nunca se mueve la carpeta de un mundo que ya está cargado (p. ej. el nivel principal). */
    private boolean isLoadedWorldFolder(File dir) {
        for (World w : Bukkit.getWorlds()) {
            if (dir.equals(w.getWorldFolder())) return true;
        }
        return false;
    }

    /** Dimensión recordada para un mundo (si la guardamos al instalarlo), o la que deduce su carpeta. */
    private World.Environment storedEnv(String name, File migrated) {
        String s = envs.get(name);
        if (s != null) {
            try {
                return World.Environment.valueOf(s);
            } catch (RuntimeException ignored) {
                // valor corrupto: se ignora y se deduce de la carpeta
            }
        }
        return environmentOf(migrated);
    }

    private void rememberEnv(String name, World.Environment env) {
        String s = env.name();
        if (!s.equals(envs.get(name))) {
            envs.put(name, s);
            save();
        }
    }

    /**
     * Devuelve la carpeta que realmente contiene el mundo. Sirve para plantillas que quedaron
     * anidadas (p. ej. {@code the_bridge/the_bridge/level.dat}): baja hasta el {@code level.dat}.
     */
    private static File resolveWorldRoot(File dir) {
        File cur = dir;
        for (int depth = 0; depth < 6 && !new File(cur, "level.dat").isFile(); depth++) {
            File[] subs = cur.listFiles(File::isDirectory);
            if (subs == null || subs.length != 1) break;
            cur = subs[0];
        }
        return cur;
    }

    /** Mueve el contenido de una carpeta de mundo anidada a su raíz ({@code mundo/mundo/...} -> {@code mundo/...}). */
    private static void flattenWorldDir(File dir) throws IOException {
        if (new File(dir, "level.dat").isFile()) return;
        File cur = dir;
        File first = null;
        for (int depth = 0; depth < 6 && !new File(cur, "level.dat").isFile(); depth++) {
            File[] subs = cur.listFiles(File::isDirectory);
            if (subs == null || subs.length != 1) return;
            if (first == null) first = subs[0];
            cur = subs[0];
        }
        if (first == null || !new File(cur, "level.dat").isFile()) return;
        File[] children = cur.listFiles();
        if (children != null) {
            for (File c : children) {
                Files.move(c.toPath(), new File(dir, c.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        deleteRecursively(first);
    }

    private static void deleteRecursively(File f) {
        File[] cs = f.listFiles();
        if (cs != null) for (File c : cs) deleteRecursively(c);
        f.delete();
    }

    /**
     * Deduce la dimensión del mundo a partir de dónde tiene las regiones: la superficie del
     * overworld vive en {@code region/}, el nether en {@code DIM-1/region} y el end en {@code DIM1/region}
     * (o el formato nuevo de Paper en {@code dimensions/minecraft/<dimensión>}).
     */
    private static World.Environment environmentOf(File dir) {
        if (new File(dir, "region").isDirectory()) return World.Environment.NORMAL;
        File dim = new File(dir, "dimensions" + File.separator + "minecraft");
        if (hasRegion(dir, "DIM-1") || hasRegion(dim, "the_nether")) return World.Environment.NETHER;
        if (hasRegion(dir, "DIM1") || hasRegion(dim, "the_end")) return World.Environment.THE_END;
        if (hasRegion(dim, "overworld")) return World.Environment.NORMAL;
        return World.Environment.NORMAL;
    }

    private static boolean hasRegion(File parent, String child) {
        return new File(new File(parent, child), "region").isDirectory();
    }

    /** Genera spawns automáticos (en círculo alrededor del spawn del mundo) para arenas sin spawns. */
    private void autoSpawns() {
        if (!plugin.getConfig().getBoolean("worlds.auto-spawns", true)) return;
        int n = Math.max(2, plugin.getConfig().getInt("worlds.spawns-per-arena", 8));
        double radius = Math.max(3, plugin.getConfig().getDouble("worlds.spawn-radius", 12));
        boolean changed = false;
        for (String arena : allNames()) {
            if (spawnCount(arena) > 0) continue;
            World w = ensureWorld(arena);
            if (w == null) {
                plugin.getLogger().warning("No se pudo cargar la arena '" + arena + "': quedará fuera hasta que cargue.");
                continue;
            }
            Location c = w.getSpawnLocation();
            List<String> list = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                double ang = (2 * Math.PI * i) / n;
                int bx = (int) Math.floor(c.getX() + radius * Math.cos(ang));
                int bz = (int) Math.floor(c.getZ() + radius * Math.sin(ang));
                Location p = new Location(w, bx + 0.5, w.getHighestBlockYAt(bx, bz) + 1, bz + 0.5);
                p.setDirection(c.toVector().subtract(p.toVector()));
                list.add(fmt(p));
            }
            spawns.put(arena, list);
            changed = true;
            plugin.getLogger().info("Arena '" + arena + "': " + n + " spawns automáticos alrededor de " + fmt(c));
        }
        if (changed) save();
    }

    private void ensureLobby() {
        if (lobbyRaw != null) return;
        World w = ensureWorld(lobbyWorldName());
        if (w == null) return;
        lobbyRaw = fmt(w.getSpawnLocation());
        save();
        plugin.getLogger().info("Lobby por defecto: spawn del mundo '" + w.getName() + "'.");
    }

    private void writeResource(String resource, File dest) {
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) return;
            File parent = dest.getParentFile();
            if (parent != null) parent.mkdirs();
            try (OutputStream out = new FileOutputStream(dest)) {
                in.transferTo(out);
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo escribir " + dest.getName() + ": " + ex.getMessage());
        }
    }

    private static void copyDir(Path src, Path dst) throws IOException {
        Files.createDirectories(dst);
        try (var walk = Files.walk(src)) {
            for (Path p : walk.toList()) {
                String rel = src.relativize(p).toString();
                if (rel.equals("session.lock")) continue; // se regenera al abrir el mundo
                Path target = dst.resolve(rel);
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Path parent = target.getParent();
                    if (parent != null) Files.createDirectories(parent);
                    Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * Extrae un mundo que viene embebido en el propio jar ({@code worlds/<nombre>.zip}).
     * Devuelve true si lo encontró y lo instaló.
     */
    private boolean installEmbedded(String name, File dir) throws IOException {
        String resource = "worlds/" + name + ".zip";
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) return false;
            unzipStream(in, dir);
        }
        flattenWorldDir(dir);
        plugin.getLogger().info("Mundo '" + name + "' instalado desde los mapas incluidos en el plugin.");
        return true;
    }

    private static void unzip(File zip, File dst) throws IOException {
        try (InputStream in = new FileInputStream(zip)) {
            unzipStream(in, dst);
        }
    }

    private static void unzipStream(InputStream in, File dst) throws IOException {
        String base = dst.getCanonicalPath();
        try (ZipInputStream zin = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                // Normaliza separadores (algunos zip hechos en Windows usan '\').
                File out = new File(dst, entry.getName().replace('\\', '/'));
                if (!out.getCanonicalPath().startsWith(base)) continue; // evita salir de la carpeta
                if (entry.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null) parent.mkdirs();
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    zin.transferTo(fos);
                }
            }
        }
    }

    // ------------------------------------------------------------ consultas

    /** Mundos del evento: el lobby y todas las arenas (configuradas o con spawns). */
    boolean isEventWorld(World w) {
        if (w == null) return false;
        String n = w.getName();
        return n.equals(lobbyWorldName()) || allNames().contains(n);
    }

    /** Mundo de una arena (lo carga/copia/crea si hace falta). */
    World world(String arena) {
        return ensureWorld(arena);
    }

    World lobbyWorld() {
        return ensureWorld(lobbyWorldName());
    }

    Location lobby() {
        World w = lobbyWorld();
        return (w == null || lobbyRaw == null) ? null : parse(w, lobbyRaw);
    }

    void setLobby(Location l) {
        lobbyRaw = fmt(l);
        save();
    }

    /** Arenas jugables: con puntos de spawn o, en su defecto, con su mundo disponible. */
    List<String> names() {
        List<String> out = new ArrayList<>();
        for (String a : allNames()) {
            if (spawnCount(a) > 0 || ensureWorld(a) != null) out.add(a);
        }
        return out;
    }

    /** Todas las arenas conocidas: las configuradas más las que tengan spawns guardados. */
    List<String> allNames() {
        Set<String> out = new LinkedHashSet<>(arenaNames());
        out.addAll(spawns.keySet());
        return new ArrayList<>(out);
    }

    int spawnCount(String arena) {
        List<String> l = spawns.get(arena);
        return l == null ? 0 : l.size();
    }

    List<Location> spawns(String arena) {
        List<Location> out = new ArrayList<>();
        List<String> raw = spawns.get(arena);
        if (raw == null) return out;
        World w = ensureWorld(arena);
        if (w == null) return out;
        for (String s : raw) {
            Location l = parse(w, s);
            if (l != null) out.add(l);
        }
        return out;
    }

    void addSpawn(String arena, Location l) {
        spawns.computeIfAbsent(arena, k -> new ArrayList<>()).add(fmt(l));
        save();
    }

    /** Establece el spawn de una arena (reemplaza los puntos que hubiera) en la posición dada. */
    void setSpawn(String arena, Location l) {
        List<String> list = new ArrayList<>();
        list.add(fmt(l));
        spawns.put(arena, list);
        save();
    }

    boolean remove(String arena) {
        boolean r = spawns.remove(arena) != null;
        if (r) save();
        return r;
    }

    static String fmt(Location l) {
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f,%.1f,%.1f", l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    static Location parse(World w, String s) {
        try {
            String[] p = s.split(",");
            return new Location(w, Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                    Float.parseFloat(p[3]), Float.parseFloat(p[4]));
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
