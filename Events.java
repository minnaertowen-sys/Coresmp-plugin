package net.coresmp;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

final class Events implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Store.pd(p.getUniqueId()).name = p.getName();
        Store.markDirty();
        for (String line : Cfg.welcome) p.sendMessage(line.replace("%player%", p.getName()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Teleports.cancel(e.getPlayer().getUniqueId());
        AntiCheat.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        Deathban.preLogin(e);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        AntiCheat.grace(e.getPlayer().getUniqueId());
    }

    // ---- deaths: deathban, stats ----
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Deathban.onDeath(victim);
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            Store.pd(killer.getUniqueId()).playerKills++;
            Store.markDirty();
        }
    }

    // ---- mob kills: quests, stats; soldiers never drop gear ----
    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        LivingEntity ent = e.getEntity();
        if (Army.isSoldier(ent)) {
            e.getDrops().clear();
            e.setDroppedExp(0);
            return;
        }
        Player killer = ent.getKiller();
        if (killer != null && ent instanceof Enemy) {
            Store.pd(killer.getUniqueId()).mobKills++;
            Store.markDirty();
            Quests.add(killer, "KILL_MOBS", 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Store.pd(p.getUniqueId()).mined++;
        Quests.add(p, "MINE_BLOCKS", 1);
    }

    // ---- anti-cheat + soldiers tracking fights ----
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            if (AntiCheat.checkAttack(p, e.getEntity())) {
                e.setCancelled(true);
                return;
            }
            if (e.getEntity() instanceof Enemy && !Army.isSoldier(e.getEntity())) Army.noteFight(p, e.getEntity());
        } else if (e.getEntity() instanceof Player victim && e.getDamager() instanceof Enemy && !Army.isSoldier(e.getDamager())) {
            Army.noteFight(victim, e.getDamager());
        }
    }

    // ---- duels never kill ----
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Duel d = Duel.of(p.getUniqueId());
        if (d == null) return;
        if (!d.isFighting()) {
            e.setCancelled(true);
            return;
        }
        if (e.getFinalDamage() >= p.getHealth()) {
            e.setCancelled(true);
            Duel.onLethal(p);
        }
    }

    // ---- soldiers only attack hostile mobs ----
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent e) {
        if (!Army.isSoldier(e.getEntity())) return;
        Entity t = e.getTarget();
        if (t == null) return;
        if (!(t instanceof Enemy) || Army.isSoldier(t)) e.setCancelled(true);
    }

    // ---- chat disabler ----
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        if (!Store.chatDisabled) return;
        Player p = e.getPlayer();
        if (p.hasPermission("coresmp.admin")) return;
        for (String n : Cfg.chatExempt) if (n.equalsIgnoreCase(p.getName())) return;
        e.setCancelled(true);
        Msg.send(p, "§cChat is disabled on this server.");
    }
}
