package net.coresmp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/** Runs console commands so kits, quests and events can use any command. */
final class Run {
    private Run() {}

    static void cmd(String command) {
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } catch (Exception e) {
            CorePlugin.get().getLogger().warning("Command failed: " + command + " (" + e + ")");
        }
    }

    static String fill(String c, Player p) {
        return c.replace("%player%", p.getUniqueId().toString()).replace("%name%", p.getName());
    }

    static void forPlayer(Player p, List<String> commands) {
        for (String c : commands) cmd(fill(c, p));
    }
}
