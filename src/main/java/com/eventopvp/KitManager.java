package com.eventopvp;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class KitManager {
    private final EventoPvP plugin;
    private ItemStack helmet, chest, legs, boots, offhand;
    private final List<ItemStack> items = new ArrayList<>();

    KitManager(EventoPvP plugin) {
        this.plugin = plugin;
        load();
    }

    void load() {
        var c = plugin.getConfig();
        helmet = parse(c.getString("kit.helmet"));
        chest = parse(c.getString("kit.chestplate"));
        legs = parse(c.getString("kit.leggings"));
        boots = parse(c.getString("kit.boots"));
        offhand = parse(c.getString("kit.offhand"));
        items.clear();
        for (String s : c.getStringList("kit.items")) {
            ItemStack i = parse(s);
            if (i != null) items.add(i);
        }
    }

    void give(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setHelmet(copy(helmet));
        inv.setChestplate(copy(chest));
        inv.setLeggings(copy(legs));
        inv.setBoots(copy(boots));
        inv.setItemInOffHand(offhand == null ? new ItemStack(Material.AIR) : offhand.clone());
        for (ItemStack i : items) inv.addItem(i.clone());
        p.updateInventory();
    }

    private static ItemStack copy(ItemStack i) {
        return i == null ? null : i.clone();
    }

    private ItemStack parse(String line) {
        if (line == null || line.isBlank()) return null;
        String[] t = line.trim().split("\\s+");
        String[] head = t[0].split(":");
        Material m = Material.matchMaterial(head[0]);
        if (m == null || m.isAir() || !m.isItem()) {
            plugin.getLogger().warning("Kit: material inválido '" + head[0] + "'");
            return null;
        }
        int amount = 1;
        if (head.length > 1) {
            try {
                amount = Integer.parseInt(head[1]);
            } catch (NumberFormatException ex) {
                plugin.getLogger().warning("Kit: cantidad inválida en '" + line + "'");
            }
        }
        ItemStack it = new ItemStack(m, Math.max(1, Math.min(amount, m.getMaxStackSize())));
        for (int i = 1; i < t.length; i++) {
            String tok = t[i];
            if (tok.equalsIgnoreCase("unbreakable")) {
                ItemMeta meta = it.getItemMeta();
                if (meta != null) {
                    meta.setUnbreakable(true);
                    it.setItemMeta(meta);
                }
                continue;
            }
            String[] kv = tok.split("=");
            if (kv.length != 2) {
                plugin.getLogger().warning("Kit: token inválido '" + tok + "' en '" + line + "'");
                continue;
            }
            try {
                Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(kv[0].toLowerCase(Locale.ROOT)));
                if (e == null) {
                    plugin.getLogger().warning("Kit: encantamiento desconocido '" + kv[0] + "'");
                    continue;
                }
                it.addUnsafeEnchantment(e, Integer.parseInt(kv[1]));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Kit: token inválido '" + tok + "' en '" + line + "'");
            }
        }
        return it;
    }
}
