package net.coresmp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

/** Warmup, cooldowns and the "back" location. */
final class Teleports {
    private static final class Pending {
        Location start, dest;
        double hp;
        String label;
        int ticks;
    }

    private static final Map<UUID, Pending> pending = new HashMap<>();
    private static final Map<String, Long> cooldowns = new HashMap<>();
    private static final Map<UUID, Location> back = new HashMap<>();

    private Teleports() {}

    static void setBack(UUID id, Location l) {
        back.put(id, l.clone());
    }

    static Location getBack(UUID id) {
        return back.get(id);
    }

    static long cooldownLeft(Player p, String type) {
        Long until = cooldowns.get(p.getUniqueId() + ":" + type);
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis() + 999) / 1000);
    }

    static void setCooldown(Player p, String type, int seconds) {
        if (seconds <= 0) return;
        cooldowns.put(p.getUniqueId() + ":" + type, System.currentTimeMillis() + seconds * 1000L);
    }

    static boolean cooldownOk(Player p, String type) {
        long left = cooldownLeft(p, type);
        if (left > 0) {
            Msg.send(p, "§cWait §e" + left + "s§c before using that again.");
            return false;
        }
        return true;
    }

    static void request(Player p, Location dest, String label) {
        if (dest.getWorld() == null) {
            Msg.send(p, "§cThat location's world no longer exists.");
            return;
        }
        if (Cfg.warmup <= 0) {
            go(p, dest, label);
            return;
        }
        Pending pd = new Pending();
        pd.start = p.getLocation();
        pd.hp = p.getHealth();
        pd.dest = dest.clone();
        pd.label = label;
        pd.ticks = Cfg.warmup * 20;
        pending.put(p.getUniqueId(), pd);
        Msg.send(p, "Teleporting to §e" + label + "§f in §e" + Cfg.warmup + "s§f. Don't move!");
    }

    static void cancel(UUID id) {
        pending.remove(id);
    }

    static void tick() {
        if (pending.isEmpty()) return;
        Iterator<Map.Entry<UUID, Pending>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> e = it.next();
            Pending pd = e.getValue();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            Location now = p.getLocation();
            if (now.getWorld() != pd.start.getWorld() || now.distanceSquared(pd.start) > 0.6 || p.getHealth() < pd.hp) {
                Msg.send(p, "§cTeleport cancelled.");
                it.remove();
                continue;
            }
            pd.ticks--;
            if (pd.ticks <= 0) {
                it.remove();
                go(p, pd.dest, pd.label);
            } else if (pd.ticks % 20 == 0) {
                Msg.bar(p, "§eTeleporting in " + (pd.ticks / 20) + "...");
            }
        }
    }

    static void go(Player p, Location dest, String label) {
        back.put(p.getUniqueId(), p.getLocation().clone());
        p.teleport(dest);
        Msg.send(p, "Teleported to §e" + label + "§f.");
    }
}
