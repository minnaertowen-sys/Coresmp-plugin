package net.coresmp;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Values from config.yml. */
final class Cfg {
    static class Quest {
        String name = "Quest";
        String type = "KILL_MOBS";
        int amount = 10;
        int soldiers, revives;
        List<String> commands = new ArrayList<>();
    }

    static class Event {
        String announce = "";
        List<String> commands = new ArrayList<>();
    }

    static int maxHomes, warmup, homeCd, spawnCd, warpCd, tpaCd, tpaTimeout, backCd;
    static int rtpCd, rtpMin, rtpMax, rtpCenterX, rtpCenterZ, rtpAttempts, autosave;
    static List<String> welcome = new ArrayList<>();
    static boolean deathbanOn;
    static int deathbanMin;
    static List<String> deathbanExempt = new ArrayList<>(), chatExempt = new ArrayList<>();
    static int maxArmy;
    static boolean ac;
    static double maxSpeed, maxReach;
    static int hoverTicks, maxCps, kickVl;
    static boolean unstableOn;
    static int unstableMin;
    static List<Event> events = new ArrayList<>();
    static int dailyHours;
    static List<String> dailyCmds = new ArrayList<>(), dailyBonus = new ArrayList<>();
    static int duelTimeout, duelCountdown, duelMax, crowdSize, crowdRadius;
    static List<String> crowdNames = new ArrayList<>();
    static List<Quest> quests = new ArrayList<>();

    private Cfg() {}

    private static int num(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    private static List<String> strs(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) for (Object x : l) out.add(String.valueOf(x));
        return out;
    }

    static void load(CorePlugin pl) {
        pl.reloadConfig();
        FileConfiguration c = pl.getConfig();
        maxHomes = c.getInt("max-homes", 3);
        warmup = c.getInt("teleport-warmup-seconds", 3);
        homeCd = c.getInt("home-cooldown-seconds", 5);
        spawnCd = c.getInt("spawn-cooldown-seconds", 5);
        warpCd = c.getInt("warp-cooldown-seconds", 5);
        tpaCd = c.getInt("tpa-cooldown-seconds", 10);
        tpaTimeout = c.getInt("tpa-timeout-seconds", 60);
        backCd = c.getInt("back-cooldown-seconds", 30);
        rtpCd = c.getInt("rtp.cooldown-seconds", 300);
        rtpMin = c.getInt("rtp.min-radius", 500);
        rtpMax = c.getInt("rtp.max-radius", 5000);
        rtpCenterX = c.getInt("rtp.center-x", 0);
        rtpCenterZ = c.getInt("rtp.center-z", 0);
        rtpAttempts = c.getInt("rtp.attempts", 6);
        autosave = c.getInt("autosave-seconds", 300);
        welcome = c.getStringList("welcome-messages");
        deathbanOn = c.getBoolean("deathban.enabled", true);
        deathbanMin = c.getInt("deathban.minutes", 30);
        deathbanExempt = c.getStringList("deathban.exempt");
        chatExempt = c.getStringList("chat-exempt");
        maxArmy = c.getInt("army.max-soldiers", 10);
        ac = c.getBoolean("anti-cheat.enabled", true);
        maxSpeed = c.getDouble("anti-cheat.max-avg-speed-per-tick", 0.9);
        hoverTicks = c.getInt("anti-cheat.hover-ticks", 50);
        maxReach = c.getDouble("anti-cheat.max-reach", 6.0);
        maxCps = c.getInt("anti-cheat.max-cps", 22);
        kickVl = c.getInt("anti-cheat.kick-at-violations", 12);
        unstableOn = c.getBoolean("unstable.enabled", true);
        unstableMin = c.getInt("unstable.interval-minutes", 20);
        events = new ArrayList<>();
        for (Map<?, ?> m : c.getMapList("unstable.events")) {
            Event e = new Event();
            e.announce = String.valueOf(m.get("announce"));
            e.commands = strs(m.get("commands"));
            events.add(e);
        }
        dailyHours = c.getInt("daily.cooldown-hours", 20);
        dailyCmds = c.getStringList("daily.commands");
        dailyBonus = c.getStringList("daily.bonus-commands");
        duelTimeout = c.getInt("duel.request-timeout-seconds", 60);
        duelCountdown = c.getInt("duel.countdown-seconds", 5);
        duelMax = c.getInt("duel.max-seconds", 300);
        crowdSize = c.getInt("duel.crowd-size", 24);
        crowdRadius = c.getInt("duel.crowd-radius", 11);
        crowdNames = c.getStringList("duel.crowd-names");
        quests = new ArrayList<>();
        for (Map<?, ?> m : c.getMapList("quests")) {
            Quest q = new Quest();
            q.name = String.valueOf(m.get("name"));
            q.type = String.valueOf(m.get("type")).toUpperCase();
            q.amount = num(m.get("amount"), 10);
            q.soldiers = num(m.get("soldiers"), 0);
            q.revives = num(m.get("revives"), 0);
            q.commands = strs(m.get("commands"));
            quests.add(q);
        }
    }
}
