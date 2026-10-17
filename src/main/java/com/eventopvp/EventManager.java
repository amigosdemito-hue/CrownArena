package com.eventopvp;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.eventopvp.Messages.p;
import static com.eventopvp.Messages.pc;

public final class EventManager {
    public enum State { IDLE, COUNTDOWN, INVITING, STARTING, RUNNING, ENDING }

    private record LastHit(UUID attacker, long time) {}

    private final EventoPvP plugin;
    private final ArenaManager arenas;
    private final SnapshotStore snapshots;
    private final KitManager kit;
    private final Messages msg;
    private final HistoryStore history;
    private final Hud hud;

    private State state = State.IDLE;
    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Map<UUID, Stats> stats = new HashMap<>();
    private final Map<UUID, Long> protectedUntil = new HashMap<>();
    private final Map<UUID, LastHit> lastHit = new HashMap<>();
    private final List<BukkitTask> eventTasks = new ArrayList<>();
    private final Set<String> fired = new HashSet<>();
    private final Set<String> spawnWarned = new HashSet<>();
    private BukkitTask scheduler;
    private String currentArena;
    private long endAt;
    private int totalSeconds;
    private long windowEnd;
    private int windowSeconds;
    private boolean firstBlood;
    private boolean badScheduleWarned;
    private final Set<UUID> hordeMobs = new HashSet<>();
    private List<EntityType> hordeTypes = List.of();
    private int wave;
    private boolean spawningHorde;

    EventManager(EventoPvP plugin, ArenaManager arenas, SnapshotStore snapshots, KitManager kit,
                 Messages msg, HistoryStore history) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.snapshots = snapshots;
        this.kit = kit;
        this.msg = msg;
        this.history = history;
        this.hud = new Hud(msg);
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public State state() {
        return state;
    }

    public boolean isParticipant(Player pl) {
        return participants.contains(pl.getUniqueId());
    }

    /** Cierto si el mundo es el lobby o alguna de las arenas del evento. */
    boolean isEventWorld(World w) {
        return arenas.isEventWorld(w);
    }

    /** Cierto mientras se generan las hordas (para permitir únicamente esos spawns). */
    boolean isSpawningHorde() {
        return spawningHorde;
    }

    public String currentArena() {
        return currentArena;
    }

    public int participantCount() {
        return participants.size();
    }

    public long secondsLeft() {
        return state == State.RUNNING ? Math.max(0, (endAt - System.currentTimeMillis()) / 1000) : 0;
    }

    boolean isProtected(Player pl) {
        return protectedUntil.getOrDefault(pl.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    void dropProtection(Player pl) {
        protectedUntil.remove(pl.getUniqueId());
    }

    private void protect(Player pl) {
        protectedUntil.put(pl.getUniqueId(),
                System.currentTimeMillis() + cfg().getInt("spawn-protection-seconds", 3) * 1000L);
    }

    /** true si el hambre debe quedar congelada (comida siempre llena, sin hambre). */
    boolean hungerLocked() {
        return cfg().getString("hunger", "vanilla").equalsIgnoreCase("locked");
    }

    /** Deja la comida al empezar/reaparecer: en vanilla llena con saturación normal; en "locked", llena del todo. */
    private void resetHunger(Player pl) {
        pl.setFoodLevel(20);
        pl.setSaturation(hungerLocked() ? 20f : 5f);
    }

    private List<Player> onlineParticipants() {
        List<Player> out = new ArrayList<>();
        for (UUID id : participants) {
            Player pl = Bukkit.getPlayer(id);
            if (pl != null) out.add(pl);
        }
        return out;
    }

    private void sound(Player pl, Sound s, float pitch) {
        if (cfg().getBoolean("sounds", true)) pl.playSound(pl.getLocation(), s, 1f, pitch);
    }

    private static String clock(long secs) {
        return String.format("%02d:%02d", secs / 60, secs % 60);
    }

    // ------------------------------------------------------------ programación quincenal

    void startScheduler() {
        scheduler = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSchedule, 100L, 20L);
    }

    private ZonedDateTime nowZoned() {
        try {
            return ZonedDateTime.now(ZoneId.of(cfg().getString("schedule.timezone", "America/Mexico_City")));
        } catch (DateTimeException ex) {
            warnSchedule(ex);
            return null;
        }
    }

    private void warnSchedule(Exception ex) {
        if (!badScheduleWarned) {
            badScheduleWarned = true;
            plugin.getLogger().warning("Programación inválida (schedule.*): " + ex.getMessage());
        }
    }

    /** Próximo inicio programado (o el actual si estamos dentro de su minuto). */
    public ZonedDateTime nextStart() {
        ZonedDateTime now = nowZoned();
        if (now == null) return null;
        try {
            LocalDate first = LocalDate.parse(cfg().getString("schedule.first-date", ""));
            int everyDays = cfg().getInt("schedule.every-days", 15);
            LocalTime fallback = LocalTime.parse(cfg().getString("schedule.time", "20:00").trim());
            return computeNextStart(now, first, everyDays, weeklyTimes(), fallback);
        } catch (DateTimeException ex) {
            warnSchedule(ex);
            return null;
        }
    }

    /**
     * Hora de cada día de la semana para el horario fijo ({@code schedule.times}). Vacío si no se
     * configuró (entonces se usa la hora única {@code schedule.time} para cualquier día).
     */
    private Map<DayOfWeek, LocalTime> weeklyTimes() {
        ConfigurationSection sec = cfg().getConfigurationSection("schedule.times");
        if (sec == null) return Map.of();
        Map<DayOfWeek, LocalTime> map = new EnumMap<>(DayOfWeek.class);
        for (String key : sec.getKeys(false)) {
            DayOfWeek day = parseDay(key);
            if (day == null) {
                plugin.getLogger().warning("schedule.times: día desconocido '" + key
                        + "' (usa lunes/martes/... o monday/tuesday/...).");
                continue;
            }
            String value = sec.getString(key, "");
            try {
                map.put(day, LocalTime.parse(value.trim()));
            } catch (DateTimeException ex) {
                plugin.getLogger().warning("schedule.times." + key + ": hora inválida '" + value
                        + "' (usa HH:mm, p. ej. 20:21).");
            }
        }
        return map;
    }

    /** Interpreta un día de la semana en inglés o español (sin distinguir mayúsculas ni acentos). */
    static DayOfWeek parseDay(String raw) {
        if (raw == null) return null;
        String s = Normalizer.normalize(raw.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace(".", "");
        return switch (s) {
            case "1", "mon", "monday", "lun", "lunes" -> DayOfWeek.MONDAY;
            case "2", "tue", "tues", "tuesday", "mar", "martes" -> DayOfWeek.TUESDAY;
            case "3", "wed", "wednesday", "mie", "miercoles" -> DayOfWeek.WEDNESDAY;
            case "4", "thu", "thur", "thurs", "thursday", "jue", "jueves" -> DayOfWeek.THURSDAY;
            case "5", "fri", "friday", "vie", "viernes" -> DayOfWeek.FRIDAY;
            case "6", "sat", "saturday", "sab", "sabado" -> DayOfWeek.SATURDAY;
            case "7", "sun", "sunday", "dom", "domingo" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    /** Cálculo puro del próximo inicio con una hora distinta por día (sin Bukkit, para poder probarlo). */
    static ZonedDateTime computeNextStart(ZonedDateTime now, LocalDate first, int everyDays,
                                          Map<DayOfWeek, LocalTime> times, LocalTime fallback) {
        int every = Math.max(1, everyDays);
        LocalDate today = now.toLocalDate();
        long diff = ChronoUnit.DAYS.between(first, today);
        LocalDate cand = diff < 0 ? first : first.plusDays((diff / every) * every);
        ZonedDateTime z = at(cand, times, fallback, now);
        if (z.isBefore(now.truncatedTo(ChronoUnit.MINUTES))) {
            z = at(cand.plusDays(every), times, fallback, now);
        }
        return z;
    }

    private static ZonedDateTime at(LocalDate day, Map<DayOfWeek, LocalTime> times, LocalTime fallback,
                                    ZonedDateTime now) {
        LocalTime t = times.getOrDefault(day.getDayOfWeek(), fallback);
        return day.atTime(t).atZone(now.getZone());
    }

    /** Cálculo puro del próximo inicio con una hora única (compatibilidad con la config antigua). */
    static ZonedDateTime computeNextStart(ZonedDateTime now, LocalDate first, LocalTime time, int everyDays) {
        return computeNextStart(now, first, everyDays, Map.of(), time);
    }

    private void tickSchedule() {
        if (state != State.IDLE || !cfg().getBoolean("schedule.enabled", true)) return;
        ZonedDateTime now = nowZoned();
        ZonedDateTime next = nextStart();
        if (now == null || next == null) return;
        if (fired.size() > 100) fired.clear();
        long secs = ChronoUnit.SECONDS.between(now, next);
        if (secs <= 0) {
            if (!fired.add("start " + next)) return;
            String err = start();
            if (err != null) plugin.getLogger().warning("No se pudo iniciar el evento programado: " + err);
            return;
        }
        for (int m : cfg().getIntegerList("schedule.reminders-minutes")) {
            if (secs <= m * 60L && secs > m * 60L - 5 && fired.add(next + "#" + m)) {
                Bukkit.broadcast(msg.get("reminder", p("minutes", m)));
            }
        }
    }

    /**
     * Si el inicio programado ya pasó mientras había evento (p. ej. un admin lo detuvo
     * justo en su minuto), lo marca como disparado para no relanzarlo inmediatamente.
     */
    private void markCurrentSlotIfDue() {
        ZonedDateTime now = nowZoned();
        ZonedDateTime next = nextStart();
        if (now == null || next == null) return;
        if (ChronoUnit.SECONDS.between(now, next) <= 0) fired.add("start " + next);
    }

    // ------------------------------------------------------------ ciclo del evento

    /** Devuelve la clave del mensaje de error, o null si arrancó. */
    public String start() {
        if (state != State.IDLE) return "already-running";
        if (arenas.names().isEmpty()) return "no-arenas";
        state = State.COUNTDOWN;
        int secs = Math.max(1, cfg().getInt("countdown-seconds", 10));
        Bukkit.broadcast(msg.get("countdown-chat", p("seconds", secs)));
        eventTasks.add(new BukkitRunnable() {
            int left = secs;

            @Override
            public void run() {
                if (state != State.COUNTDOWN) {
                    cancel();
                    return;
                }
                if (left <= 0) {
                    cancel();
                    openInvitations();
                    return;
                }
                Title t = Title.title(msg.raw("countdown-title", p("seconds", left)), msg.raw("countdown-subtitle"),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ZERO));
                for (Player pl : Bukkit.getOnlinePlayers()) {
                    pl.showTitle(t);
                    sound(pl, Sound.BLOCK_NOTE_BLOCK_PLING, left <= 3 ? 2f : 1f);
                }
                left--;
            }
        }.runTaskTimer(plugin, 0L, 20L));
        return null;
    }

    private void openInvitations() {
        List<String> names = arenas.names();
        if (names.isEmpty()) {
            endEvent(false);
            return;
        }
        currentArena = names.get(ThreadLocalRandom.current().nextInt(names.size()));
        state = State.INVITING;
        windowSeconds = Math.max(5, cfg().getInt("accept-window-seconds", 30));
        windowEnd = System.currentTimeMillis() + windowSeconds * 1000L;
        for (Player pl : Bukkit.getOnlinePlayers()) invite(pl);
        eventTasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::tickWaiting, 20L, 20L));
        eventTasks.add(Bukkit.getScheduler().runTaskLater(plugin, this::beginStarting, windowSeconds * 20L));
    }

    void invite(Player pl) {
        if (state != State.INVITING || participants.contains(pl.getUniqueId())) return;
        if (!pl.hasPermission("evento.join")) return;
        long left = Math.max(1, (windowEnd - System.currentTimeMillis()) / 1000);
        InviteDialog.show(plugin, this, msg, pl);
        pl.sendMessage(msg.get("invite-chat", p("seconds", left)));
        sound(pl, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f);
    }

    public void join(Player pl) {
        if (state != State.INVITING) {
            pl.sendMessage(msg.get("no-invite"));
            return;
        }
        if (participants.contains(pl.getUniqueId())) return;
        if (!pl.hasPermission("evento.join")) {
            pl.sendMessage(msg.get("no-permission"));
            return;
        }
        int max = cfg().getInt("max-players", 0);
        if (max > 0 && participants.size() >= max) {
            pl.sendMessage(msg.get("full"));
            return;
        }
        // devolver el cursor al inventario (cofre abierto) ANTES de guardar el snapshot
        pl.closeInventory();
        // Snapshot huérfano (p. ej. un restore fallido): devolverlo antes de guardar el inventario actual
        if (snapshots.has(pl.getUniqueId()) && !snapshots.restore(pl)) {
            pl.sendMessage(msg.get("snapshot-failed"));
            return;
        }
        if (!snapshots.save(pl)) {
            pl.sendMessage(msg.get("snapshot-failed"));
            return;
        }
        participants.add(pl.getUniqueId());
        stats.put(pl.getUniqueId(), new Stats(pl.getUniqueId(), pl.getName()));
        prepare(pl, waitingLocation(pl));
        tickWaiting();
        pl.sendMessage(msg.get("joined"));
    }

    private void prepare(Player pl, Location where) {
        pl.closeInventory();
        pl.leaveVehicle();
        pl.setGameMode(GameMode.SURVIVAL);
        for (PotionEffect e : new ArrayList<>(pl.getActivePotionEffects())) pl.removePotionEffect(e.getType());
        AttributeInstance max = pl.getAttribute(Attribute.MAX_HEALTH);
        pl.setHealth(max == null ? 20.0 : max.getValue());
        resetHunger(pl);
        pl.setFireTicks(0);
        pl.setFallDistance(0f);
        kit.give(pl);
        if (where != null) pl.teleport(where);
        protect(pl);
    }

    private void tickWaiting() {
        if (state != State.INVITING) return;
        long left = Math.max(0, (windowEnd - System.currentTimeMillis()) / 1000);
        hud.setBar(msg.raw("bar-waiting", p("seconds", left), p("players", participants.size())),
                left / (float) windowSeconds, BossBar.Color.YELLOW);
        if (cfg().getBoolean("bossbar", true)) for (Player pl : onlineParticipants()) hud.showBar(pl);
    }

    private void beginStarting() {
        if (state != State.INVITING) return;
        int min = Math.max(1, cfg().getInt("min-players", 2));
        if (participants.size() < min) {
            Bukkit.broadcast(msg.get("cancelled-few"));
            endEvent(false);
            return;
        }
        state = State.STARTING;
        int secs = Math.max(1, cfg().getInt("lobby-countdown-seconds", 5));
        eventTasks.add(new BukkitRunnable() {
            int left = secs;

            @Override
            public void run() {
                if (state != State.STARTING) {
                    cancel();
                    return;
                }
                if (left <= 0) {
                    cancel();
                    launch();
                    return;
                }
                hud.setBar(msg.raw("bar-starting", p("seconds", left)), left / (float) secs, BossBar.Color.RED);
                Title t = Title.title(msg.raw("start-title", p("seconds", left)), msg.raw("start-subtitle"),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ZERO));
                for (Player pl : onlineParticipants()) {
                    pl.showTitle(t);
                    sound(pl, Sound.BLOCK_NOTE_BLOCK_PLING, left <= 3 ? 2f : 1f);
                }
                left--;
            }
        }.runTaskTimer(plugin, 0L, 20L));
    }

    private void launch() {
        if (state != State.STARTING) return;
        state = State.RUNNING;
        firstBlood = true;
        totalSeconds = Math.max(1, cfg().getInt("duration-minutes", 20)) * 60;
        endAt = System.currentTimeMillis() + totalSeconds * 1000L;
        Bukkit.broadcast(msg.get("fight-start", p("players", participants.size())));
        Title go = Title.title(msg.raw("title-go"), msg.raw("title-go-sub", p("arena", currentArena)));
        List<Location> placed = new ArrayList<>();
        for (Player pl : onlineParticipants()) {
            Location l = pickSpawn(currentArena, placed);
            if (l != null) {
                pl.teleport(l);
                placed.add(l);
            }
            protect(pl);
            pl.showTitle(go);
            sound(pl, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f);
        }
        var s = Bukkit.getScheduler();
        hordeMobs.clear();
        wave = 0;
        hordeTypes = readHordeTypes();
        eventTasks.add(s.runTaskTimer(plugin, this::tickRunning, 0L, 20L));
        long ann = Math.max(10, cfg().getInt("announce-interval-seconds", 60)) * 20L;
        eventTasks.add(s.runTaskTimer(plugin, this::announceScore, ann, ann));
        long rot = Math.max(1, cfg().getInt("arena-rotation-minutes", 4)) * 60L * 20L;
        eventTasks.add(s.runTaskTimer(plugin, this::rotateArena, rot, rot));
        if (cfg().getBoolean("mobs.enabled", true)) {
            long first = Math.max(0, cfg().getInt("mobs.first-wave-seconds", 45)) * 20L;
            eventTasks.add(s.runTaskLater(plugin, this::spawnHorde, first));
        }
        eventTasks.add(s.runTaskLater(plugin, () -> endEvent(true), totalSeconds * 20L));
    }

    // ------------------------------------------------------------ hordas de mobs

    private List<EntityType> readHordeTypes() {
        List<EntityType> out = new ArrayList<>();
        for (String raw : cfg().getStringList("mobs.types")) {
            try {
                EntityType t = EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
                if (t.isSpawnable() && t.isAlive()) out.add(t);
                else plugin.getLogger().warning("mobs.types: '" + raw + "' no es un mob válido");
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("mobs.types inválido: '" + raw + "'");
            }
        }
        if (out.isEmpty()) out.add(EntityType.ZOMBIE);
        return out;
    }

    /** Horda progresiva en la arena actual (solo durante el combate). */
    private void spawnHorde() {
        if (state != State.RUNNING) return;
        List<Location> sp = arenas.spawns(currentArena);
        if (sp.isEmpty()) {
            scheduleNextHorde();
            return;
        }
        wave++;
        int max = Math.max(1, cfg().getInt("mobs.max-count", 16));
        int want = Math.max(1, cfg().getInt("mobs.base-count", 4))
                + (wave - 1) * Math.max(0, cfg().getInt("mobs.count-per-wave", 3));
        hordeMobs.removeIf(id -> Bukkit.getEntity(id) == null); // limpiar muertos
        int toSpawn = Math.min(want, max) - hordeMobs.size();
        int spawned = 0;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Collections.shuffle(sp);
        spawningHorde = true;
        try {
            for (int i = 0; i < toSpawn; i++) {
                Location base = sp.get(i % sp.size());
                Location at = base.clone().add(rnd.nextDouble(-0.5, 0.5), 0.1, rnd.nextDouble(-0.5, 0.5));
                EntityType type = hordeTypes.get(rnd.nextInt(hordeTypes.size()));
                Entity e = at.getWorld().spawnEntity(at, type);
                if (!(e instanceof Mob)) { // tipo configurado que no es un mob (p. ej. armadura)
                    e.remove();
                    continue;
                }
                if (e instanceof LivingEntity le) {
                    le.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 6000, 0));
                }
                hordeMobs.add(e.getUniqueId());
                spawned++;
            }
        } finally {
            spawningHorde = false;
        }
        if (spawned > 0) {
            for (Player pl : onlineParticipants()) pl.sendMessage(msg.get("horde-wave", p("wave", wave)));
        }
        scheduleNextHorde();
    }

    private void scheduleNextHorde() {
        long next = Math.max(5, cfg().getInt("mobs.interval-seconds", 60)) * 20L;
        eventTasks.add(Bukkit.getScheduler().runTaskLater(plugin, this::spawnHorde, next));
    }

    /** Retira los mobs de la horda vivos (cambio de arena, fin del evento, apagado). */
    private void clearHorde() {
        for (UUID id : hordeMobs) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        hordeMobs.clear();
    }

    // ------------------------------------------------------------ HUD

    private void tickRunning() {
        if (state != State.RUNNING) return;
        long left = secondsLeft();
        boolean bar = cfg().getBoolean("bossbar", true);
        boolean board = cfg().getBoolean("scoreboard", true);
        if (bar) {
            hud.setBar(msg.raw("bar-running", p("time", clock(left)), p("arena", currentArena)),
                    left / (float) totalSeconds, BossBar.Color.RED);
        }
        List<Stats> rank = ranking();
        for (Player pl : onlineParticipants()) {
            if (bar) hud.showBar(pl);
            Stats st = stats.get(pl.getUniqueId());
            if (board && st != null) hud.updateBoard(pl, buildLines(left, st, rank));
        }
    }

    private List<Component> buildLines(long left, Stats st, List<Stats> rank) {
        List<Component> l = new ArrayList<>();
        l.add(Component.empty());
        l.add(msg.raw("sb-time", p("time", clock(left))));
        l.add(msg.raw("sb-arena", p("arena", currentArena)));
        l.add(Component.empty());
        l.add(msg.raw("sb-kills", p("kills", st.kills), p("deaths", st.deaths)));
        l.add(msg.raw("sb-streak", p("streak", st.streak), p("best", st.bestStreak)));
        l.add(Component.empty());
        l.add(msg.raw("sb-top-header"));
        for (int i = 0; i < 3; i++) {
            if (i < rank.size()) {
                Stats s = rank.get(i);
                l.add(msg.raw("sb-top-entry", p("pos", i + 1), p("player", s.name), p("kills", s.kills)));
            } else {
                l.add(Component.empty());
            }
        }
        return l;
    }

    // ------------------------------------------------------------ spawns

    /** Spawn que maximiza la distancia al punto ocupado más cercano (reparto justo). */
    private Location pickSpawn(String arena, Collection<Location> occupied) {
        List<Location> sp = arenas.spawns(arena);
        if (sp.isEmpty()) {
            // Sin puntos válidos: aun así vamos al mundo de esa arena. Si no, el jugador no se
            // teletransporta y se queda en la arena anterior, como si hubiera ido a otra.
            if (spawnWarned.add(arena)) {
                plugin.getLogger().warning("Arena '" + arena + "' sin puntos de spawn válidos; se usará el spawn de su mundo. Fija uno con: /evento spawn " + arena);
            }
            World w = arenas.world(arena);
            return w == null ? null : w.getSpawnLocation();
        }
        Collections.shuffle(sp);
        if (occupied.isEmpty()) return sp.get(0);
        Location best = sp.get(0);
        double bestD = -1;
        for (Location s : sp) {
            double min = Double.MAX_VALUE;
            for (Location o : occupied) {
                if (o.getWorld() != null && o.getWorld().equals(s.getWorld())) {
                    min = Math.min(min, s.distanceSquared(o));
                }
            }
            if (min > bestD) {
                bestD = min;
                best = s;
            }
        }
        return best;
    }

    private List<Location> occupiedBy(Player except) {
        World w = arenas.world(currentArena);
        List<Location> out = new ArrayList<>();
        if (w == null) return out;
        for (Player pl : onlineParticipants()) {
            if (!pl.equals(except) && !pl.isDead() && pl.getWorld().equals(w)) out.add(pl.getLocation());
        }
        return out;
    }

    private Location waitingLocation(Player pl) {
        Location lobby = arenas.lobby();
        return lobby != null ? lobby : pickSpawn(currentArena, occupiedBy(pl));
    }

    Location respawnLocation(Player pl) {
        Location l = (state == State.RUNNING || state == State.ENDING)
                ? pickSpawn(currentArena, occupiedBy(pl))
                : waitingLocation(pl);
        return l != null ? l : Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    private void rotateArena() {
        if (state != State.RUNNING) return;
        List<String> names = arenas.names();
        names.remove(currentArena);
        if (names.isEmpty()) return;
        String next = names.get(ThreadLocalRandom.current().nextInt(names.size()));
        for (Player pl : onlineParticipants()) pl.sendMessage(msg.get("arena-warning"));
        eventTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != State.RUNNING) return;
            currentArena = next;
            clearHorde(); // los mobs de la arena anterior desaparecen con ella
            Title t = Title.title(msg.raw("title-arena"), msg.raw("title-arena-sub", p("arena", next)));
            List<Location> placed = new ArrayList<>();
            for (Player pl : onlineParticipants()) {
                Location l = pickSpawn(next, placed);
                if (l != null) {
                    pl.teleport(l);
                    placed.add(l);
                }
                protect(pl);
                pl.showTitle(t);
                sound(pl, Sound.ENTITY_ENDERMAN_TELEPORT, 1f);
            }
        }, 100L));
    }

    // ------------------------------------------------------------ combate

    void recordHit(Player victim, Player attacker) {
        if (state != State.RUNNING || victim.equals(attacker)) return;
        if (!participants.contains(victim.getUniqueId()) || !participants.contains(attacker.getUniqueId())) return;
        lastHit.put(victim.getUniqueId(), new LastHit(attacker.getUniqueId(), System.currentTimeMillis()));
    }

    void handleDeath(Player victim, Player killer) {
        if (state != State.RUNNING) return;
        Stats vs = stats.get(victim.getUniqueId());
        if (vs == null) return;
        int endedStreak = vs.streak;
        vs.deaths++;
        vs.streak = 0;
        lastHit.remove(victim.getUniqueId());
        Stats ks = (killer != null && !killer.equals(victim)) ? stats.get(killer.getUniqueId()) : null;
        if (ks == null) {
            Bukkit.broadcast(msg.get("death", p("victim", victim.getName()), p("deaths", vs.deaths)));
            return;
        }
        ks.kills++;
        ks.streak++;
        ks.bestStreak = Math.max(ks.bestStreak, ks.streak);
        if (firstBlood) {
            firstBlood = false;
            Bukkit.broadcast(msg.get("first-blood", p("killer", killer.getName())));
        }
        Material hand = killer.getInventory().getItemInMainHand().getType();
        Component weapon = hand == Material.AIR ? Component.text("las manos") : Component.translatable(hand.translationKey());
        Bukkit.broadcast(msg.get("kill", p("killer", killer.getName()), p("victim", victim.getName()),
                pc("weapon", weapon), p("kills", ks.kills)));
        if (endedStreak >= 3) {
            Bukkit.broadcast(msg.get("shutdown", p("killer", killer.getName()), p("victim", victim.getName()),
                    p("streak", endedStreak)));
        }
        if (cfg().getIntegerList("streaks").contains(ks.streak)) {
            Bukkit.broadcast(msg.get("streak", p("player", killer.getName()), p("streak", ks.streak)));
        }
        sound(killer, Sound.ENTITY_PLAYER_LEVELUP, 1.2f);
    }

    void handleTotem(Player victim) {
        if (state != State.RUNNING) return;
        LastHit lh = lastHit.get(victim.getUniqueId());
        long window = cfg().getInt("combat-tag-seconds", 10) * 1000L;
        if (lh == null || System.currentTimeMillis() - lh.time() > window) return;
        Player attacker = Bukkit.getPlayer(lh.attacker());
        Stats as = stats.get(lh.attacker());
        Stats vs = stats.get(victim.getUniqueId());
        if (attacker == null || as == null || vs == null) return;
        as.totemsBroken++;
        vs.totemsLost++;
        Bukkit.broadcast(msg.get("totem", p("attacker", attacker.getName()), p("victim", victim.getName()),
                p("totems", as.totemsBroken)));
        sound(attacker, Sound.ITEM_TOTEM_USE, 1f);
    }

    void afterRespawn(Player pl) {
        if (!isParticipant(pl) || state == State.IDLE) return;
        for (PotionEffect e : new ArrayList<>(pl.getActivePotionEffects())) pl.removePotionEffect(e.getType());
        resetHunger(pl);
        if (cfg().getBoolean("reset-kit-on-respawn", true)) kit.give(pl);
        protect(pl);
    }

    void onRejoin(Player pl) {
        if (state == State.IDLE) return;
        Location l = respawnLocation(pl);
        if (l != null) pl.teleport(l);
        protect(pl);
        pl.sendMessage(msg.get("rejoin"));
    }

    void onQuit(Player pl) {
        hud.forget(pl.getUniqueId());
    }

    private void announceScore() {
        if (state != State.RUNNING || stats.isEmpty()) return;
        Stats lk = null, md = null;
        for (Stats s : stats.values()) {
            if (lk == null || s.kills > lk.kills || (s.kills == lk.kills && s.deaths < lk.deaths)) lk = s;
            if (md == null || s.deaths > md.deaths) md = s;
        }
        if (lk.kills == 0 && md.deaths == 0) return;
        Bukkit.broadcast(msg.get("score-announce", p("kills_name", lk.name), p("kills", lk.kills),
                p("deaths_name", md.name), p("deaths", md.deaths)));
    }

    // ------------------------------------------------------------ fin

    public void stop() {
        if (state == State.IDLE) return;
        Bukkit.broadcast(msg.get("cancelled-admin"));
        endEvent(false);
    }

    void endEvent(boolean ranking) {
        cancelTasks();
        if (state == State.IDLE) return;
        if (ranking && state == State.RUNNING) {
            state = State.ENDING;
            announceResults();
            long ticks = Math.max(1, cfg().getInt("ending-seconds", 8)) * 20L;
            eventTasks.add(Bukkit.getScheduler().runTaskLater(plugin, this::finishRestore, ticks));
        } else {
            finishRestore();
        }
    }

    private void cancelTasks() {
        for (BukkitTask t : eventTasks) t.cancel();
        eventTasks.clear();
    }

    private void finishRestore() {
        hud.clearAll();
        clearHorde();
        for (UUID id : new ArrayList<>(participants)) {
            Player pl = Bukkit.getPlayer(id);
            if (pl != null && snapshots.restore(pl)) pl.sendMessage(msg.get("restored"));
            // los desconectados se restauran al volver a entrar
        }
        participants.clear();
        stats.clear();
        protectedUntil.clear();
        lastHit.clear();
        currentArena = null;
        markCurrentSlotIfDue();
        state = State.IDLE;
    }

    private List<Stats> ranking() {
        List<Stats> l = new ArrayList<>(stats.values());
        l.sort(Comparator.comparingInt((Stats s) -> -s.kills).thenComparingInt(s -> s.deaths).thenComparing(s -> s.name));
        return l;
    }

    private void announceResults() {
        List<Stats> r = ranking();
        Stats winner = (!r.isEmpty() && r.get(0).kills > 0) ? r.get(0) : null;
        for (Stats s : stats.values()) history.record(s, s == winner);
        history.save();

        Bukkit.broadcast(msg.get("results-header"));
        String[] keys = {"first", "second", "third"};
        for (int i = 0; i < Math.min(3, r.size()); i++) {
            Stats s = r.get(i);
            Bukkit.broadcast(msg.raw("results-line", p("pos", i + 1), p("player", s.name), p("kills", s.kills),
                    p("deaths", s.deaths), p("totems", s.totemsBroken)));
            if (s.kills > 0 && s.name.matches("[A-Za-z0-9_]{1,16}")) {
                for (String cmd : cfg().getStringList("rewards." + keys[i])) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", s.name));
                }
            }
        }
        if (winner == null) {
            Bukkit.broadcast(msg.get("no-winner"));
            return;
        }
        Title t = Title.title(msg.raw("winner-title", p("player", winner.name)),
                msg.raw("winner-sub", p("kills", winner.kills), p("deaths", winner.deaths)),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(500)));
        for (Player pl : onlineParticipants()) {
            pl.showTitle(t);
            sound(pl, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f);
        }
        final UUID winnerId = winner.id;
        if (cfg().getBoolean("fireworks", true)) {
            eventTasks.add(new BukkitRunnable() {
                int n = 0;

                @Override
                public void run() {
                    Player w = Bukkit.getPlayer(winnerId);
                    if (state != State.ENDING || w == null || n++ >= 6) {
                        cancel();
                        return;
                    }
                    ThreadLocalRandom rnd = ThreadLocalRandom.current();
                    fireworks(w.getLocation().add(rnd.nextDouble(-3, 3), 1, rnd.nextDouble(-3, 3)));
                }
            }.runTaskTimer(plugin, 0L, 20L));
        }
    }

    private void fireworks(Location loc) {
        loc.getWorld().spawn(loc, Firework.class, f -> {
            FireworkMeta m = f.getFireworkMeta();
            m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.ORANGE, Color.YELLOW).withFade(Color.RED).flicker(true).trail(true).build());
            m.setPower(1);
            f.setFireworkMeta(m);
        });
    }

    /** Saca a un participante del evento: devuelve su inventario y descarta sus stats. */
    public boolean kick(Player pl) {
        if (!participants.remove(pl.getUniqueId())) return false;
        stats.remove(pl.getUniqueId());
        protectedUntil.remove(pl.getUniqueId());
        lastHit.remove(pl.getUniqueId());
        hud.clear(pl);
        snapshots.restore(pl);
        pl.sendMessage(msg.get("restored"));
        return true;
    }

    public List<Stats> topList() {
        return ranking();
    }

    void shutdown() {
        if (scheduler != null) scheduler.cancel();
        cancelTasks();
        if (state != State.IDLE) finishRestore();
    }
}
