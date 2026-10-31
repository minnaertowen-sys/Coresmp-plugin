package net.coresmp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/** Sequential quests. Finishing one earns army soldiers, revive tokens and optional commands. */
final class Quests {
    private Quests() {}

    static void add(Player p, String type, int n) {
        List<Cfg.Quest> qs = Cfg.quests;
        Store.PlayerData d = Store.pd(p.getUniqueId());
        if (d.questIndex >= qs.size()) return;
        Cfg.Quest q = qs.get(d.questIndex);
        if (!q.type.equalsIgnoreCase(type)) return;
        d.progress += n;
        Store.markDirty();
        if (d.progress >= q.amount) {
            d.soldiers = Math.min(Cfg.maxArmy, d.soldiers + q.soldiers);
            d.revives += q.revives;
            d.questIndex++;
            d.progress = 0;
            Run.forPlayer(p, q.commands);
            Msg.send(p, "§aQuest complete: §e" + q.name + "§a! +§e" + q.soldiers + "§a army soldier(s). Use §f/core army summon§a.");
            if (q.revives > 0) Msg.send(p, "§a+§e" + q.revives + "§a revive token(s)! Use §f/core revive <name>§a on a deathbanned friend.");
            if (d.questIndex < qs.size()) Msg.send(p, "Next quest: §e" + describe(qs.get(d.questIndex)));
            else Msg.send(p, "§6You finished every quest!");
        } else if (d.progress % 10 == 0) {
            Msg.bar(p, "§e" + q.name + ": §f" + d.progress + "/" + q.amount);
        }
    }

    static void playMinute() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Store.pd(p.getUniqueId()).minutes++;
            add(p, "PLAY_MINUTES", 1);
        }
    }

    static String describe(Cfg.Quest q) {
        String what = switch (q.type) {
            case "KILL_MOBS" -> "Kill " + q.amount + " hostile mobs";
            case "MINE_BLOCKS" -> "Mine " + q.amount + " blocks";
            case "PLAY_MINUTES" -> "Play for " + q.amount + " minutes";
            default -> q.type + " x" + q.amount;
        };
        return q.name + " - " + what + " (reward: " + q.soldiers + " soldier(s))";
    }

    static void status(Player p) {
        List<Cfg.Quest> qs = Cfg.quests;
        Store.PlayerData d = Store.pd(p.getUniqueId());
        if (d.questIndex >= qs.size()) {
            Msg.send(p, "§6You finished every quest! Soldiers earned: §e" + d.soldiers);
            return;
        }
        Cfg.Quest q = qs.get(d.questIndex);
        Msg.send(p, "Quest " + (d.questIndex + 1) + "/" + qs.size() + ": §e" + describe(q));
        Msg.send(p, "Progress: §e" + d.progress + "/" + q.amount + "§f  | Soldiers earned: §e" + d.soldiers);
    }
}
