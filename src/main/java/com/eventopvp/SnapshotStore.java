package com.eventopvp;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** Guarda en disco el estado del jugador antes del evento (inventario separado). */
final class SnapshotStore {
    private final EventoPvP plugin;
    private final File dir;

    SnapshotStore(EventoPvP plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "snapshots");
        dir.mkdirs();
    }

    private File file(UUID id) {
        return new File(dir, id + ".yml");
    }

    boolean has(UUID id) {
        return file(id).exists();
    }

    /** Nunca sobrescribe un snapshot existente (conserva el inventario original). */
    boolean save(Player p) {
        File f = file(p.getUniqueId());
        if (f.exists()) return true;
        try {
            ItemStack[] contents = p.getInventory().getContents();
            ItemStack[] safe = new ItemStack[contents.length];
            for (int i = 0; i < contents.length; i++) {
                safe[i] = contents[i] == null ? new ItemStack(Material.AIR) : contents[i];
            }
            YamlConfiguration y = new YamlConfiguration();
            y.set("items", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(safe)));
            y.set("location", p.getLocation());
            y.set("health", p.getHealth());
            y.set("food", p.getFoodLevel());
            y.set("saturation", (double) p.getSaturation());
            y.set("level", p.getLevel());
            y.set("exp", (double) p.getExp());
            y.set("gamemode", p.getGameMode().name());
            y.set("slot", p.getInventory().getHeldItemSlot());
            y.set("effects", new ArrayList<>(p.getActivePotionEffects()));
            java.nio.file.Path tmp = f.toPath().resolveSibling(f.getName() + ".tmp");
            java.nio.file.Files.writeString(tmp, y.saveToString(), java.nio.charset.StandardCharsets.UTF_8);
            java.nio.file.Files.move(tmp, f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().warning("No se pudo guardar el inventario de " + p.getName() + ": " + ex.getMessage());
            f.delete();
            return false;
        }
    }

    boolean restore(Player p) {
        File f = file(p.getUniqueId());
        if (!f.exists()) return false;
        try {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            p.closeInventory();
            for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());

            // decodificar ANTES de tocar el inventario: si el archivo está corrupto
            // (p. ej. cambio de versión de MC), se conserva el inventario actual
            String b64 = y.getString("items");
            ItemStack[] arr = b64 == null ? null
                    : ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(b64));
            PlayerInventory inv = p.getInventory();
            inv.clear();
            if (arr != null) {
                if (arr.length > inv.getSize()) arr = java.util.Arrays.copyOf(arr, inv.getSize());
                inv.setContents(arr);
                inv.setHeldItemSlot(Math.max(0, Math.min(8, y.getInt("slot", 0))));
            }
            List<?> eff = y.getList("effects");
            if (eff != null) for (Object o : eff) if (o instanceof PotionEffect pe) p.addPotionEffect(pe);

            p.setFoodLevel(y.getInt("food", 20));
            p.setSaturation((float) y.getDouble("saturation", 5.0));
            p.setLevel(y.getInt("level", 0));
            p.setExp((float) y.getDouble("exp", 0.0));
            AttributeInstance maxAttr = p.getAttribute(Attribute.MAX_HEALTH);
            double max = maxAttr == null ? 20.0 : maxAttr.getValue();
            p.setHealth(Math.max(1.0, Math.min(y.getDouble("health", max), max)));
            try {
                p.setGameMode(GameMode.valueOf(y.getString("gamemode", "SURVIVAL")));
            } catch (IllegalArgumentException ex) {
                p.setGameMode(GameMode.SURVIVAL);
            }
            p.setFireTicks(0);
            p.setFallDistance(0f);
            Location loc = y.getLocation("location");
            if (loc != null && loc.getWorld() != null) p.teleport(loc);
            else p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            p.updateInventory();
            f.delete();
            return true;
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Error restaurando a " + p.getName() + " (snapshot conservado): " + ex);
            return false;
        }
    }
}
