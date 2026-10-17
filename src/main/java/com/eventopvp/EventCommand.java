package com.eventopvp;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.eventopvp.Messages.p;

final class EventCommand implements TabExecutor {
    private static final List<String> PUBLIC = List.of("aceptar", "top", "stats", "ranking", "proximo", "info");
    private static final List<String> ADMIN = List.of("iniciar", "detener", "spawn", "lobby", "borrar", "lista",
            "tparena", "mundo", "expulsar", "recargar", "instalar");
    private static final List<String> STATS = List.of("kills", "wins", "totems", "streak", "events");
    private static final List<String> SPAWN_ACTIONS = List.of("set", "add", "clear");

    private final EventoPvP plugin;
    private final EventManager manager;
    private final ArenaManager arenas;
    private final HistoryStore history;
    private final Messages msg;

    EventCommand(EventoPvP plugin, EventManager manager, ArenaManager arenas, HistoryStore history, Messages msg) {
        this.plugin = plugin;
        this.manager = manager;
        this.arenas = arenas;
        this.history = history;
        this.msg = msg;
    }

    private void send(CommandSender s, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... r) {
        s.sendMessage(msg.get(key, r));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            send(sender, "usage");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (ADMIN.contains(sub) && !sender.hasPermission("evento.admin")) {
            send(sender, "no-permission");
            return true;
        }
        switch (sub) {
            case "aceptar" -> {
                if (sender instanceof Player pl) manager.join(pl);
                else send(sender, "only-players");
            }
            case "top" -> top(sender);
            case "stats" -> stats(sender, args);
            case "ranking" -> ranking(sender, args);
            case "proximo" -> next(sender);
            case "info" -> info(sender);
            case "iniciar" -> {
                String err = manager.start();
                send(sender, err == null ? "started-ok" : err);
            }
            case "detener" -> {
                if (manager.state() == EventManager.State.IDLE) send(sender, "not-running");
                else manager.stop();
            }
            case "spawn" -> spawn(sender, args);
            case "lobby" -> {
                if (!(sender instanceof Player pl)) {
                    send(sender, "only-players");
                    break;
                }
                World lw = arenas.lobbyWorld();
                if (lw == null || !pl.getWorld().equals(lw)) {
                    send(sender, "lobby-world", p("lobby", arenas.lobbyWorldName()));
                    break;
                }
                arenas.setLobby(pl.getLocation());
                send(sender, "lobby-set");
            }
            case "borrar" -> {
                if (args.length < 2 || !args[1].matches("[A-Za-z0-9_-]+")) send(sender, "arena-usage");
                else send(sender, arenas.remove(args[1]) ? "arena-deleted" : "arena-missing", p("arena", args[1]));
            }
            case "lista" -> {
                List<String> all = arenas.allNames();
                if (all.isEmpty()) send(sender, "arena-none");
                for (String a : all) send(sender, "arena-line", p("arena", a), p("n", arenas.spawnCount(a)));
            }
            case "tparena" -> tparena(sender, args);
            case "mundo" -> {
                if (!(sender instanceof Player pl)) {
                    send(sender, "only-players");
                    return true;
                }
                World w = arenas.lobbyWorld();
                if (w == null) send(sender, "no-world");
                else pl.teleport(w.getSpawnLocation());
            }
            case "expulsar" -> {
                Player target = args.length < 2 ? null : Bukkit.getPlayerExact(args[1]);
                if (target != null && manager.kick(target)) send(sender, "kick-ok", p("player", target.getName()));
                else send(sender, "kick-none");
            }
            case "recargar" -> {
                plugin.reload();
                send(sender, "reloaded");
            }
            case "instalar" -> {
                arenas.install();
                send(sender, "install-ok", p("lobby", arenas.lobbyWorldName()), p("n", arenas.arenaNames().size()));
            }
            default -> send(sender, "unknown-sub");
        }
        return true;
    }

    private void spawn(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].matches("[A-Za-z0-9_-]+")) {
            send(sender, "spawn-usage");
            return;
        }
        String arena = args[1];
        String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "set";
        if (action.equals("clear")) {
            send(sender, arenas.remove(arena) ? "spawn-cleared" : "arena-missing", p("arena", arena));
            return;
        }
        if (!action.equals("set") && !action.equals("add")) {
            send(sender, "spawn-usage");
            return;
        }
        if (!(sender instanceof Player pl)) {
            send(sender, "only-players");
            return;
        }
        if (!pl.getWorld().getName().equals(arena)) {
            send(sender, "spawn-world", p("arena", arena));
            return;
        }
        if (action.equals("add")) {
            arenas.addSpawn(arena, pl.getLocation());
            send(sender, "spawn-added", p("n", arenas.spawnCount(arena)), p("arena", arena));
        } else {
            arenas.setSpawn(arena, pl.getLocation());
            send(sender, "spawn-set", p("arena", arena));
        }
    }

    private void tparena(CommandSender sender, String[] args) {
        if (!(sender instanceof Player pl)) {
            send(sender, "only-players");
            return;
        }
        if (args.length < 2 || !args[1].matches("[A-Za-z0-9_-]+")) {
            send(sender, "arena-usage");
            return;
        }
        List<Location> sp = arenas.spawns(args[1]);
        if (sp.isEmpty()) {
            send(sender, "arena-no-spawn", p("arena", args[1]));
            return;
        }
        pl.teleport(sp.get(0));
        send(sender, "tparena-ok", p("arena", args[1]));
    }

    private void top(CommandSender sender) {
        List<Stats> r = manager.topList();
        if (r.isEmpty()) {
            send(sender, "not-running");
            return;
        }
        for (int i = 0; i < Math.min(10, r.size()); i++) {
            Stats s = r.get(i);
            send(sender, "top-line", p("pos", i + 1), p("player", s.name), p("kills", s.kills), p("deaths", s.deaths));
        }
    }

    private void stats(CommandSender sender, String[] args) {
        String name = args.length > 1 ? args[1] : (sender instanceof Player pl ? pl.getName() : null);
        HistoryStore.Entry e = name == null ? null : history.find(name);
        if (e == null) {
            send(sender, "stats-none");
            return;
        }
        send(sender, "stats-line", p("player", e.name), p("events", e.events), p("wins", e.wins),
                p("kills", e.kills), p("deaths", e.deaths), p("totems", e.totems), p("streak", e.bestStreak));
    }

    private void ranking(CommandSender sender, String[] args) {
        String stat = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "kills";
        if (!STATS.contains(stat)) stat = "kills";
        List<HistoryStore.Entry> top = history.top(stat, 10);
        send(sender, "ranking-header", p("stat", stat));
        if (top.isEmpty()) send(sender, "ranking-empty");
        for (int i = 0; i < top.size(); i++) {
            HistoryStore.Entry e = top.get(i);
            send(sender, "ranking-line", p("pos", i + 1), p("player", e.name), p("value", e.value(stat)));
        }
    }

    private void next(CommandSender sender) {
        ZonedDateTime n = manager.nextStart();
        if (n == null) {
            send(sender, "next-none");
            return;
        }
        Duration d = Duration.between(ZonedDateTime.now(n.getZone()), n);
        if (d.isNegative()) d = Duration.ZERO;
        String left = d.toDays() + "d " + d.toHoursPart() + "h " + d.toMinutesPart() + "m";
        send(sender, "next-event", p("date", n.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))), p("left", left));
    }

    private void info(CommandSender sender) {
        long left = manager.secondsLeft();
        send(sender, "info", p("state", manager.state().name()), p("players", manager.participantCount()),
                p("arena", manager.currentArena() == null ? "-" : manager.currentArena()),
                p("time", String.format("%02d:%02d", left / 60, left % 60)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("evento.admin");
        if (args.length == 1) {
            String pre = args[0].toLowerCase(Locale.ROOT);
            for (String s : PUBLIC) if (s.startsWith(pre)) out.add(s);
            if (admin) for (String s : ADMIN) if (s.startsWith(pre)) out.add(s);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String pre = args[1].toLowerCase(Locale.ROOT);
            List<String> pool = new ArrayList<>();
            if (admin && (sub.equals("spawn") || sub.equals("borrar") || sub.equals("tparena"))) pool.addAll(arenas.allNames());
            else if (sub.equals("ranking")) pool.addAll(STATS);
            else if (sub.equals("stats") || (admin && sub.equals("expulsar"))) {
                for (Player pl : Bukkit.getOnlinePlayers()) pool.add(pl.getName());
                if (sub.equals("stats")) {
                    for (String n : history.names()) if (!pool.contains(n)) pool.add(n);
                }
            }
            for (String s : pool) if (s.toLowerCase(Locale.ROOT).startsWith(pre)) out.add(s);
        } else if (admin && args.length == 3 && args[0].equalsIgnoreCase("spawn")) {
            String pre = args[2].toLowerCase(Locale.ROOT);
            for (String s : SPAWN_ACTIONS) if (s.startsWith(pre)) out.add(s);
        }
        return out;
    }
}
