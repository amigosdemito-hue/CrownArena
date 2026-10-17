package com.eventopvp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Locale;

final class EventListener implements Listener {
    private final EventoPvP plugin;
    private final EventManager manager;
    private final SnapshotStore snapshots;
    private final Messages msg;

    EventListener(EventoPvP plugin, EventManager manager, SnapshotStore snapshots, Messages msg) {
        this.plugin = plugin;
        this.manager = manager;
        this.snapshots = snapshots;
        this.msg = msg;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player pl = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!pl.isOnline()) return;
            if (snapshots.has(pl.getUniqueId())) {
                if (manager.isParticipant(pl)) {
                    manager.onRejoin(pl);
                } else if (snapshots.restore(pl)) {
                    pl.sendMessage(msg.get("restored")); // el evento terminó (o el servidor cayó) mientras estaba fuera
                }
            } else {
                if (!pl.hasPermission("evento.admin") && manager.isEventWorld(pl.getWorld())) {
                    pl.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                    pl.sendMessage(msg.get("world-restricted"));
                }
                manager.invite(pl);
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        manager.onQuit(e.getPlayer());
    }

    /** Solo participantes (y admins) pueden entrar al mundo del evento. */
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getTo() == null || !manager.isEventWorld(e.getTo().getWorld())) return;
        Player pl = e.getPlayer();
        if (manager.isParticipant(pl) || pl.hasPermission("evento.admin")) return;
        e.setCancelled(true);
        pl.sendMessage(msg.get("world-restricted"));
    }

    /** El mundo del evento no tiene mobs naturales: solo las hordas del combate. */
    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (manager.isSpawningHorde()) return;
        if (e.getEntity() instanceof Mob && manager.isEventWorld(e.getLocation().getWorld())) {
            e.setCancelled(true);
        }
    }

    /** Las explosiones no destruyen terreno en el mundo del evento (el daño a entidades sí aplica). */
    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (manager.isEventWorld(e.getLocation().getWorld())) e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (manager.isEventWorld(e.getBlock().getWorld())) e.blockList().clear();
    }

    /** Los mobs (hordas incluidas) no sueltan drops ni experiencia en el mundo del evento. */
    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return;
        if (manager.isEventWorld(e.getEntity().getWorld())) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player v = e.getEntity();
        if (!manager.isParticipant(v)) return;
        e.setKeepInventory(true);
        e.setKeepLevel(true);
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.deathMessage(null);
        manager.handleDeath(v, v.getKiller());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (v.isOnline() && v.isDead()) v.spigot().respawn();
        });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player pl = e.getPlayer();
        if (!manager.isParticipant(pl)) return;
        e.setRespawnLocation(manager.respawnLocation(pl));
        Bukkit.getScheduler().runTask(plugin, () -> manager.afterRespawn(pl));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player pl)) return;
        // En el mundo del evento solo hay PvP entre participantes (las hordas de mobs sí aplican)
        if (e instanceof EntityDamageByEntityEvent by) {
            Player attacker = attackerOf(by);
            if (attacker != null && manager.isEventWorld(pl.getWorld())
                    && manager.isParticipant(pl) != manager.isParticipant(attacker)) {
                e.setCancelled(true);
                return;
            }
        }
        if (!manager.isParticipant(pl)) return;
        EntityDamageEvent.DamageCause c = e.getCause();
        if (c == EntityDamageEvent.DamageCause.VOID || c == EntityDamageEvent.DamageCause.KILL) return;
        if (manager.isProtected(pl) || manager.state() != EventManager.State.RUNNING) e.setCancelled(true);
    }

    private static Player attackerOf(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player pl) return pl;
        if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player shooter) return shooter;
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = attackerOf(e);
        if (attacker == null) return;
        manager.dropProtection(attacker); // atacar quita la invulnerabilidad de reaparición
        manager.recordHit(victim, attacker);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTotem(EntityResurrectEvent e) {
        if (e.isCancelled()) return; // sin tótem el evento llega cancelado
        if (e.getEntity() instanceof Player pl && manager.isParticipant(pl)) manager.handleTotem(pl);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player pl && manager.isParticipant(pl) && manager.hungerLocked()) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (manager.isParticipant(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player pl && manager.isParticipant(pl)) e.setCancelled(true);
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent e) {
        if (manager.isParticipant(e.getPlayer())) e.setCancelled(true);
    }

    private boolean noBuild(Player pl) {
        return manager.isParticipant(pl) && !plugin.getConfig().getBoolean("allow-building", false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (noBuild(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (noBuild(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (noBuild(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (noBuild(e.getPlayer())) e.setCancelled(true);
    }

    /**
     * Lista blanca de comandos durante el evento. Al heredar de este evento también se
     * cubren los comandos escritos en carteles (PlayerSignCommandPreprocessEvent).
     * Ojo: los comandos que otros plugins ejecutan por código (player.performCommand)
     * no pasan por aquí; eso solo se puede cerrar si el propio plugin que los lanza
     * respeta allowed-commands.
     */
    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player pl = e.getPlayer();
        if (!manager.isParticipant(pl) || pl.hasPermission("evento.admin")) return;
        String raw = e.getMessage();
        String root = (raw.startsWith("/") ? raw.substring(1) : raw).split(" ")[0].toLowerCase(Locale.ROOT);
        int colon = root.indexOf(':');
        if (colon >= 0) root = root.substring(colon + 1);
        if (!plugin.getConfig().getStringList("allowed-commands").contains(root)) {
            e.setCancelled(true);
            pl.sendMessage(msg.get("cmd-blocked"));
        }
    }
}
