# CoreSmp (Paper plugin, Minecraft 1.21.x)

Put the jar in the server's `plugins` folder. The server software must be **Paper** (or a Paper fork like Purpur) - not Fabric.
Everything new is under **/core** (type `/core help` in game).

## Features
- Teleports: /spawn /setspawn /home /sethome /delhome /homes /warp /warps /setwarp /delwarp /tpa /tpaccept /tpdeny /rtp, `/core back`
- Kits, editable in game: `/core kit <name>`, `/core kits`. Admins: put the kit in your inventory (hotbar, armor, offhand) and run
  `/core kit create <name> [cooldownSeconds]` (run again to edit). Also `kit delete|setcooldown|addcmd|clearcmds|info`.
- Quests -> army: `/core quest`. Quests (kill mobs / mine blocks / play time) earn soldier slots. `/core army summon` spawns a soldier
  (a husk named after you, wearing a copy of YOUR armor, weapon and shield, never attacks players). `/core army gear` re-copies your gear.
- 30 minute deathban (`deathban.minutes`), `/core deathban pardon <name>`, revive tokens: `/core revive <name>`
- Chat disabler: `/core chat off|on`
- Anti-cheat: speed, hover-fly, reach, auto-click. `/core ac alerts`, `/core ac status <player>`
- Unstable extras: random world events + `/core extras toggle|trigger|strike <player>` (orbital strike)
- Duels: `/core duel <player|@p>`, `/core duel accept|deny|quit`. Safe (no deathban, no item loss), NPC crowd, countdown, fireworks.
- Stats: `/core stats [player]`, `/core top <kills|pvp|deaths|mined|time|duels>`, `/core daily`, `/core info`

Permissions: `coresmp.admin` (default op), `coresmp.deathban.exempt`, `coresmp.ac.bypass` (works with LuckPerms).
Config: `plugins/CoreSmp/config.yml` (`/core reload`). Data: `plugins/CoreSmp/data.yml`.
If a crash leaves duel crowd NPCs behind: `/kill @e[tag=coresmp_crowd]`.
