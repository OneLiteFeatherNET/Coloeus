package net.onelitefeather.coloeus.paper;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Hands every join to {@link PlayerSync}, which returns at once. */
public final class JoinListener implements Listener {

    private final PlayerSync sync;

    JoinListener(PlayerSync sync) {
        this.sync = sync;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        sync.join(player.getUniqueId(), player.getName());
    }
}
