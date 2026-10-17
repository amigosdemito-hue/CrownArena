package com.eventopvp;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class EventoPvP extends JavaPlugin {
    private Messages messages;
    private ArenaManager arenas;
    private KitManager kit;
    private EventManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        BedrockPack.install(this);
        messages = new Messages(this);
        arenas = new ArenaManager(this);
        SnapshotStore snapshots = new SnapshotStore(this);
        HistoryStore history = new HistoryStore(this);
        kit = new KitManager(this);
        manager = new EventManager(this, arenas, snapshots, kit, messages, history);
        arenas.install(); // carga/copia/crea el lobby y las arenas antes de nada
        getServer().getPluginManager().registerEvents(new EventListener(this, manager, snapshots, messages), this);
        EventCommand cmd = new EventCommand(this, manager, arenas, history, messages);
        PluginCommand pc = getCommand("evento");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }
        manager.startScheduler();
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }

    void reload() {
        reloadConfig();
        messages.load();
        arenas.load();
        arenas.install();
        kit.load();
    }
}
