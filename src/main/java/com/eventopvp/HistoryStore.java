package com.eventopvp;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Estadísticas acumuladas de todos los eventos (history.yml). */
final class HistoryStore {
    static final class Entry {
        String name = "?";
        int events, wins, kills, deaths, totems, bestStreak;

        int value(String stat) {
            return switch (stat) {
                case "wins" -> wins;
                case "totems" -> totems;
                case "streak" -> bestStreak;
                case "events" -> events;
                default -> kills;
            };
        }
    }

    private final EventoPvP plugin;
    private final File file;
    private final Map<UUID, Entry> data = new LinkedHashMap<>();

    HistoryStore(EventoPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "history.yml");
        load();
    }

    private void load() {
        data.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("players");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection s = sec.getConfigurationSection(key);
                if (s == null) continue;
                Entry e = new Entry();
                e.name = s.getString("name", "?");
                e.events = s.getInt("events");
                e.wins = s.getInt("wins");
                e.kills = s.getInt("kills");
                e.deaths = s.getInt("deaths");
                e.totems = s.getInt("totems");
                e.bestStreak = s.getInt("best-streak");
                data.put(id, e);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    void record(Stats s, boolean won) {
        Entry e = data.computeIfAbsent(s.id, k -> new Entry());
        e.name = s.name;
        e.events++;
        if (won) e.wins++;
        e.kills += s.kills;
        e.deaths += s.deaths;
        e.totems += s.totemsBroken;
        e.bestStreak = Math.max(e.bestStreak, s.bestStreak);
    }

    void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Entry> en : data.entrySet()) {
            String b = "players." + en.getKey() + ".";
            Entry e = en.getValue();
            y.set(b + "name", e.name);
            y.set(b + "events", e.events);
            y.set(b + "wins", e.wins);
            y.set(b + "kills", e.kills);
            y.set(b + "deaths", e.deaths);
            y.set(b + "totems", e.totems);
            y.set(b + "best-streak", e.bestStreak);
        }
        try {
            plugin.getDataFolder().mkdirs();
            Path tmp = file.toPath().resolveSibling("history.yml.tmp");
            Files.writeString(tmp, y.saveToString(), StandardCharsets.UTF_8);
            Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar history.yml: " + ex.getMessage());
        }
    }

    Entry find(String name) {
        for (Entry e : data.values()) if (e.name.equalsIgnoreCase(name)) return e;
        return null;
    }

    List<String> names() {
        List<String> out = new ArrayList<>();
        for (Entry e : data.values()) out.add(e.name);
        return out;
    }

    List<Entry> top(String stat, int n) {
        List<Entry> l = new ArrayList<>();
        for (Entry e : data.values()) if (e.value(stat) > 0) l.add(e);
        l.sort(Comparator.comparingInt((Entry e) -> -e.value(stat)).thenComparing(e -> e.name));
        return l.size() > n ? new ArrayList<>(l.subList(0, n)) : l;
    }
}
