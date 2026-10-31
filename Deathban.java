package net.coresmp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class Deathban {
    private Deathban() {}

    private static boolean exemptName(String n) {
        for (String e : Cfg.deathbanExempt) if (e.equalsIgnoreCase(n)) return true;
        return false;
    }

    static String banMessage(long remainingMs) {
        long s = Math.max(1, remainingMs / 1000);
        return "§c§lDEATHBANNED\n§7You died. Come back in §e" + Kits.fmt(s);
    }

    static void onDeath(Player p) {
        Store.pd(p.getUniqueId()).deaths++;
        Teleports.setBack(p.getUniqueId(), p.getLocation()); // /core back returns to where you died
        Store.markDirty();
        if (!Cfg.deathbanOn || exemptName(p.getName()) || p.hasPermission("coresmp.deathban.exempt")) return;
        long until = System.currentTimeMillis() + Cfg.deathbanMin * 60_000L;
        Store.bans.put(p.getUniqueId(), new Store.Ban(p.getName(), until));
        Store.markDirty();
        Msg.send(p, "§cYou died! You are deathbanned for §e" + Cfg.deathbanMin + " minutes§c.");
        Bukkit.broadcastMessage("§c☠ §f" + p.getName() + " §cwas deathbanned for " + Cfg.deathbanMin
                + " min. A friend can bring them back with §f/core revive " + p.getName());
        Bukkit.getScheduler().runTaskLater(CorePlugin.get(), () -> {
            if (p.isOnline()) p.kickPlayer(banMessage(until - System.currentTimeMillis()));
        }, 40L); // let them see the death screen for 2 seconds
    }

    static void preLogin(AsyncPlayerPreLoginEvent e) {
        Store.Ban b = Store.bans.get(e.getUniqueId());
        if (b == null) return;
        long left = b.until - System.currentTimeMillis();
        if (left <= 0) {
            Store.bans.remove(e.getUniqueId());
            Store.markDirty();
            return;
        }
        e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banMessage(left));
    }

    static boolean pardon(String name) {
        Iterator<Map.Entry<UUID, Store.Ban>> it = Store.bans.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Store.Ban> e = it.next();
            if (e.getValue().name.equalsIgnoreCase(name)) {
                it.remove();
                Store.markDirty();
                return true;
            }
        }
        return false;
    }

    static List<String> names() {
        List<String> out = new ArrayList<>();
        for (Store.Ban b : Store.bans.values()) out.add(b.name);
        return out;
    }
}
