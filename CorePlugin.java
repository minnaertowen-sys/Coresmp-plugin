package net.coresmp;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public final class CorePlugin extends JavaPlugin {
    private static CorePlugin instance;
    private int tickCounter = 0;
    private int autosaveCounter = 0;

    static CorePlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        Cfg.load(this);
        Store.load(this);

        Cmds cmds = new Cmds();
        for (String n : List.of("core", "spawn", "setspawn", "home", "sethome", "delhome", "homes",
                "warp", "warps", "setwarp", "delwarp", "tpa", "tpaccept", "tpdeny", "rtp")) {
            PluginCommand c = getCommand(n);
            if (c != null) {
                c.setExecutor(cmds);
                c.setTabCompleter(cmds);
            }
        }
        getServer().getPluginManager().registerEvents(new Events(), this);

        new BukkitRunnable() {
            @Override
            public void run() {
                tickAll();
            }
        }.runTaskTimer(this, 1L, 1L);

        getLogger().info("CoreSmp loaded.");
    }

    private void tickAll() {
        Teleports.tick();
        AntiCheat.tick();
        Duel.tick();
        tickCounter++;
        if (tickCounter % 10 == 0) Army.tick();
        if (tickCounter % 20 == 0) Extras.second();
        if (tickCounter % 1200 == 0) Quests.playMinute();
        if (++autosaveCounter >= Cfg.autosave * 20) {
            autosaveCounter = 0;
            Store.saveAsyncIfDirty(this);
        }
    }

    @Override
    public void onDisable() {
        Duel.shutdown();
        Store.saveNow();
    }
}
