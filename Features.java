package net.coresmp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Stats, leaderboards, daily rewards, revive tokens and /core back. */
final class Features {
    private Features() {}

    static void stats(CommandSender to, Player t) {
        Store.PlayerData d = Store.pd(t.getUniqueId());
        Msg.send(to, "§e" + t.getName() + "§f: mobs killed §e" + d.mobKills + "§f | players killed §e" + d.playerKills
                + "§f | deaths §e" + d.deaths);
        Msg.send(to, "blocks mined §e" + d.mined + "§f | playtime §e" + Kits.fmt(d.minutes * 60L)
                + "§f | soldiers §e" + d.soldiers + "§f | revives §e" + d.revives
                + "§f | duels §e" + d.duelWins + "W/" + d.duelLosses + "L");
    }

    private static long value(Store.PlayerData d, String stat) {
        return switch (stat) {
            case "kills" -> d.mobKills;
            case "pvp" -> d.playerKills;
            case "deaths" -> d.deaths;
            case "mined" -> d.mined;
            case "time" -> d.minutes;
            case "duels" -> d.duelWins;
            default -> -1;
        };
    }

    static void top(CommandSender to, String stat) {
        final String st = stat.toLowerCase();
        if (value(new Store.PlayerData(), st) < 0) {
            Msg.send(to, "§cUse one of: kills, pvp, deaths, mined, time, duels");
            return;
        }
        List<Store.PlayerData> list = new ArrayList<>(Store.players.values());
        list.removeIf(d -> d.name == null || value(d, st) <= 0);
        list.sort((a, b) -> Long.compare(value(b, st), value(a, st)));
        Msg.send(to, "§6Top " + st + ":");
        if (list.isEmpty()) to.sendMessage("§7Nobody yet.");
        for (int i = 0; i < Math.min(5, list.size()); i++) {
            Store.PlayerData d = list.get(i);
            long v = value(d, st);
            to.sendMessage("§e#" + (i + 1) + " §f" + d.name + " §7- §e" + (st.equals("time") ? Kits.fmt(v * 60) : String.valueOf(v)));
        }
    }

    static void daily(Player p) {
        Store.PlayerData d = Store.pd(p.getUniqueId());
        long now = System.currentTimeMillis();
        long cd = Cfg.dailyHours * 3_600_000L;
        if (d.lastDaily != 0 && now - d.lastDaily < cd) {
            long left = (d.lastDaily + cd - now + 999) / 1000;
            Msg.send(p, "§cDaily reward ready in §e" + Kits.fmt(left) + "§c.");
            return;
        }
        if (d.lastDaily == 0 || now - d.lastDaily > 48L * 3_600_000L) d.streak = 1;
        else d.streak++;
        d.lastDaily = now;
        Store.markDirty();
        Run.forPlayer(p, Cfg.dailyCmds);
        Msg.send(p, "§aDaily reward claimed! Streak: §e" + d.streak + " day(s)");
        if (d.streak % 7 == 0) {
            Run.forPlayer(p, Cfg.dailyBonus);
            Msg.send(p, "§6Streak bonus for 7 days!");
        }
    }

    static void revive(Player p, String name) {
        Store.PlayerData d = Store.pd(p.getUniqueId());
        if (d.revives <= 0) {
            Msg.send(p, "§cYou have no revive tokens. Earn them from quests.");
            return;
        }
        if (!Deathban.pardon(name)) {
            Msg.send(p, "§c" + name + " isn't deathbanned.");
            return;
        }
        d.revives--;
        Store.markDirty();
        Bukkit.broadcastMessage("§a✚ §f" + p.getName() + " §aused a revive token on §f" + name + "§a!");
    }

    static void giveRevives(Player target, int n) {
        Store.pd(target.getUniqueId()).revives += n;
        Store.markDirty();
        Msg.send(target, "You received §e" + n + "§f revive token(s).");
    }

    static void back(Player p) {
        Location l = Teleports.getBack(p.getUniqueId());
        if (l == null) {
            Msg.send(p, "§cNo previous location saved.");
            return;
        }
        if (!Teleports.cooldownOk(p, "back")) return;
        Teleports.setCooldown(p, "back", Cfg.backCd);
        Teleports.request(p, l, "your previous location");
    }
}
