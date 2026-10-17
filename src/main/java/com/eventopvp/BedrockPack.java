package com.eventopvp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.bukkit.plugin.Plugin;

/**
 * Resource pack de Bedrock que deja transparente el fondo del marcador lateral
 * (el "sidebar" que en Java es solo texto, pero que en Bedrock lleva un cuadro negro).
 *
 * El fondo lo dibuja el cliente, así que no se puede quitar desde el plugin: hay que
 * entregar un resource pack. Este helper lo escribe en la carpeta "packs" de Geyser
 * (solo si Geyser corre en este mismo servidor). Si Geyser está en un proxy, hay que
 * copiar el .zip a mano en la carpeta "packs" del proxy.
 */
final class BedrockPack {

    static final String FILE_NAME = "EventoPvP-ScoreboardTransparente.zip";

    /** Archivos del pack dentro del jar (los empaqueta el build desde bedrock-resourcepack/). */
    private static final String[] ENTRIES = {"manifest.json", "ui/scoreboards.json"};

    private BedrockPack() {
    }

    static void install(Plugin plugin) {
        if (!plugin.getConfig().getBoolean("bedrock.scoreboard-transparent", true)) {
            return;
        }

        File packsDir = findGeyserPacksDir(plugin);
        if (packsDir == null) {
            plugin.getLogger().info("Geyser no está en este servidor: si tus jugadores entran desde Bedrock, copia "
                    + FILE_NAME + " en la carpeta 'packs' de Geyser (del proxy, si lo usas) para quitar el fondo del marcador.");
            return;
        }

        File target = new File(packsDir, FILE_NAME);
        try {
            writePack(plugin, target);
            plugin.getLogger().info("Marcador transparente para Bedrock: resource pack escrito en " + target.getPath()
                    + " (reinicia Geyser para aplicarlo).");
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo escribir el resource pack de Bedrock en " + target.getPath()
                    + ": " + ex.getMessage());
        }
    }

    /** Carpeta "packs" de la primera instalación de Geyser que esté en este servidor, o null. */
    private static File findGeyserPacksDir(Plugin plugin) {
        File pluginsDir = plugin.getDataFolder().getParentFile();
        File[] dirs = pluginsDir == null ? null : pluginsDir.listFiles(File::isDirectory);
        if (dirs == null) {
            return null;
        }
        for (File dir : dirs) {
            if (!dir.getName().toLowerCase(Locale.ROOT).startsWith("geyser")) {
                continue;
            }
            File packs = new File(dir, "packs");
            if (packs.isDirectory() || packs.mkdirs()) {
                return packs;
            }
        }
        return null;
    }

    private static void writePack(Plugin plugin, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target.toPath()))) {
            for (String name : ENTRIES) {
                try (InputStream in = plugin.getResource("bedrock/" + name)) {
                    if (in == null) {
                        continue;
                    }
                    zip.putNextEntry(new ZipEntry(name));
                    in.transferTo(zip);
                    zip.closeEntry();
                }
            }
        }
    }
}
