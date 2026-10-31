package net.coresmp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Unstable SMP extras: random world events and an "orbital strike" admin tool. */
final class Extras {
    private static final Random RNG = new Random();
    private static int seconds = 0;

    private Extras() {}

    /** Called once per second. */
    static void second() {
        if (!Store.unstableOn || !Cfg.unstableOn) return;
        if (++seconds < Cfg.unstableMin * 60) return;
        seconds = 0;
        trigger();
    }

    static boolean trigger() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty() || Cfg.events.isEmpty()) return false;
        Cfg.Event ev = Cfg.events.get(RNG.nextInt(Cfg.events.size()));
        Player target = players.get(RNG.nextInt(players.size()));
        Bukkit.broadcastMessage("§d[Unstable] " + ev.announce.replace("%name%", target.getName()));
        Run.forPlayer(target, ev.commands);
        return true;
    }

    static void strike(Player target) {
        Bukkit.broadcastMessage("§4§l[ORBITAL STRIKE] §cTarget locked: §f" + target.getName());
        Location l = target.getLocation();
        for (int i = 0; i < 3; i++) target.getWorld().strikeLightning(l);
        TNTPrimed tnt = target.getWorld().spawn(l.clone().add(0, 15, 0), TNTPrimed.class);
        tnt.setFuseTicks(60);
    }
}
