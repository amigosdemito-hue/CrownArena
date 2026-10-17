package com.eventopvp;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Barra de jefe compartida + marcador lateral por jugador (se restaura el marcador anterior). */
final class Hud {
    static final int LINES = 11;

    private static final class Board {
        Scoreboard sb;
        Scoreboard prev;
        final Team[] lines = new Team[LINES];
    }

    private final Messages msg;
    private final Map<UUID, Board> boards = new HashMap<>();
    private final Set<UUID> barViewers = new HashSet<>();
    private final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);

    Hud(Messages msg) {
        this.msg = msg;
    }

    void setBar(Component name, float progress, BossBar.Color color) {
        bar.name(name);
        bar.progress(Math.max(0f, Math.min(1f, progress)));
        bar.color(color);
    }

    void showBar(Player p) {
        if (barViewers.add(p.getUniqueId())) p.showBossBar(bar);
    }

    void updateBoard(Player p, List<Component> lines) {
        Board b = boards.get(p.getUniqueId());
        if (b == null) b = create(p);
        for (int i = 0; i < LINES; i++) {
            b.lines[i].prefix(i < lines.size() ? lines.get(i) : Component.empty());
        }
    }

    private Board create(Player p) {
        Board b = new Board();
        b.prev = p.getScoreboard();
        b.sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = b.sb.registerNewObjective("evento", Criteria.DUMMY, msg.raw("sb-title"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (int i = 0; i < LINES; i++) {
            String entry = "§" + Integer.toHexString(i) + "§r"; // entrada invisible y única por línea
            Team t = b.sb.registerNewTeam("l" + i);
            t.addEntry(entry);
            b.lines[i] = t;
            obj.getScore(entry).setScore(LINES - i);
        }
        p.setScoreboard(b.sb);
        boards.put(p.getUniqueId(), b);
        return b;
    }

    void clear(Player p) {
        if (barViewers.remove(p.getUniqueId())) p.hideBossBar(bar);
        Board b = boards.remove(p.getUniqueId());
        if (b != null) {
            p.setScoreboard(b.prev != null ? b.prev : Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    /** Jugador desconectado: solo olvidar (el cliente ya perdió la barra y el marcador). */
    void forget(UUID id) {
        barViewers.remove(id);
        boards.remove(id);
    }

    void clearAll() {
        for (Player p : Bukkit.getOnlinePlayers()) clear(p);
        barViewers.clear();
        boards.clear();
    }
}
