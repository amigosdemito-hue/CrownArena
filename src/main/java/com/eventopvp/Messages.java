package com.eventopvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;

/** Mensajes MiniMessage editables en messages.yml (con respaldo a los valores del jar). */
final class Messages {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    /** Versión de messages.yml del jar; si el archivo es más antiguo se regenera. */
    private static final int CURRENT_VERSION = 2;

    private final EventoPvP plugin;
    private YamlConfiguration yaml;
    private final Set<String> warned = new HashSet<>();

    Messages(EventoPvP plugin) {
        this.plugin = plugin;
        load();
    }

    void load() {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        refreshIfOutdated(f);
        if (!f.exists()) plugin.saveResource("messages.yml", false);
        yaml = YamlConfiguration.loadConfiguration(f);
        InputStream in = plugin.getResource("messages.yml");
        if (in != null) {
            yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
    }

    /** Si el messages.yml del servidor es de una versión anterior, lo respalda y lo regenera. */
    private void refreshIfOutdated(File f) {
        if (!f.exists()) return;
        int have;
        try {
            have = YamlConfiguration.loadConfiguration(f).getInt("messages-version", 1);
        } catch (Exception ex) {
            return;
        }
        if (have >= CURRENT_VERSION) return;
        try {
            File bak = new File(f.getParentFile(), "messages-v" + have + ".bak");
            Files.copy(f.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(f.toPath());
            plugin.saveResource("messages.yml", false);
            plugin.getLogger().info("messages.yml actualizado a la v" + CURRENT_VERSION
                    + " (copia anterior: plugins/EventoPvP/" + bak.getName() + ")");
        } catch (Exception ex) {
            plugin.getLogger().warning("No se pudo actualizar messages.yml: " + ex.getMessage());
        }
    }

    static TagResolver p(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    static TagResolver pc(String name, Component value) {
        return Placeholder.component(name, value);
    }

    /** Mensaje con prefijo. */
    Component get(String key, TagResolver... r) {
        return raw("prefix").append(raw(key, r));
    }

    /** Mensaje sin prefijo (títulos, marcador, diálogo). */
    Component raw(String key, TagResolver... r) {
        String s = yaml.getString(key);
        if (s == null) {
            if (warned.add(key)) plugin.getLogger().warning("Falta el mensaje '" + key + "' en messages.yml");
            return Component.text(key);
        }
        return MM.deserialize(s, r);
    }
}
