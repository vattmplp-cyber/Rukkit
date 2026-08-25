package cn.rukkit.plugin.internal;

import cn.rukkit.Rukkit;
import cn.rukkit.event.EventHandler;
import cn.rukkit.event.EventListener;
import cn.rukkit.event.player.PlayerChatEvent;
import cn.rukkit.event.player.PlayerJoinEvent;
import cn.rukkit.event.player.PlayerLeftEvent;
import cn.rukkit.event.player.PlayerReconnectEvent;
import cn.rukkit.event.room.RoomStartGameEvent;
import cn.rukkit.network.NetworkRoom;
import cn.rukkit.plugin.PluginConfig;
import cn.rukkit.util.LangUtil;
import org.slf4j.LoggerFactory;

public class BasePlugin extends InternalRukkitPlugin implements EventListener {

    @EventHandler
    public void onPlayerJoinTip(PlayerJoinEvent event) {
        NetworkRoom room = event.getPlayer().getRoom();
        room.connectionManager.broadcastServerMessage(Rukkit.getConfig().notification(
                "rukkit.playerJoin", LangUtil.getString("rukkit.playerJoin"),
                "playerName", event.getPlayer().name,
                "serverName", Rukkit.getConfig().serverUser,
                "serverPort", Rukkit.getConfig().serverPort,
                "roomId", room.roomId));
        LoggerFactory.getLogger("Room #" + room.roomId).info("Player {} joined!", event.getPlayer().name);
    }

    @EventHandler
    public void onPlayerLeaveTip(PlayerLeftEvent event) {
        NetworkRoom room = event.getPlayer().getRoom();
        room.connectionManager.broadcastServerMessage(Rukkit.getConfig().notification(
                "rukkit.playerLeft", LangUtil.getString("rukkit.playerLeft"),
                "playerName", event.getPlayer().name,
                "reason", event.getReason(),
                "serverName", Rukkit.getConfig().serverUser,
                "serverPort", Rukkit.getConfig().serverPort,
                "roomId", room.roomId));
        if (room.isGaming()) {
            event.getPlayer().sendTeamMessage(Rukkit.getConfig().notification(
                    "rukkit.playerSharingControlDueDisconnected",
                    LangUtil.getString("rukkit.playerSharingControlDueDisconnected"),
                    "playerName", event.getPlayer().name,
                    "serverName", Rukkit.getConfig().serverUser,
                    "serverPort", Rukkit.getConfig().serverPort));
        }
        LoggerFactory.getLogger("Room #" + room.roomId).info("Player {} left!({})", event.getPlayer().name, event.getReason());
        event.getPlayer().savePlayerData();
    }

    @EventHandler
    public void onPlayerChatInfo(PlayerChatEvent event) {
        LoggerFactory.getLogger("Room #" + event.getPlayer().getRoom().roomId).info("[{}] {}", event.getPlayer().name, event.getMessage());
    }

    @EventHandler
    public void onPlayerReconnected(PlayerReconnectEvent event) {
        NetworkRoom room = event.getPlayer().getRoom();
        room.connectionManager.broadcastServerMessage(Rukkit.getConfig().notification(
                "rukkit.playerReconnect", LangUtil.getString("rukkit.playerReconnect"),
                "playerName", event.getPlayer().name,
                "serverName", Rukkit.getConfig().serverUser,
                "serverPort", Rukkit.getConfig().serverPort,
                "roomId", room.roomId));
        LoggerFactory.getLogger("Room #" + room.roomId).info("Player {} reconnected!", event.getPlayer().name);
    }

    @EventHandler
    public void onGameStarted(RoomStartGameEvent event) {
        NetworkRoom room = event.getRoom();
        room.connectionManager.broadcastServerMessage(Rukkit.getConfig().notification(
                "rukkit.gameStarted", "Started game on server: {serverName}",
                "serverName", Rukkit.getConfig().serverUser,
                "serverPort", Rukkit.getConfig().serverPort,
                "roomId", room.roomId));
        LoggerFactory.getLogger("Room #" + room.roomId).info("Game started on {}", Rukkit.getConfig().serverUser);
    }

    @Override public void onLoad() {
        getLogger().info("BasePlugin::Load");
        getPluginManager().registerEventListener(this, this);
    }
    @Override public void onEnable() {}
    @Override public void onDisable() { getLogger().info("PlayerManager::Saving Player Data..."); }
    @Override public void onStart() {}
    @Override public void onDone() {}
    @Override public void loadConfig() {
        config = new PluginConfig();
        config.name = "Basic Game Plugin";
        config.author = "rukkit";
        config.version = Rukkit.RUKKIT_VERSION;
        config.id = "base-plugin";
        config.pluginClass = "cn.rukkit.plugin.internal.BasePlugin";
        config.apiVersion = Rukkit.PLUGIN_API_VERSION;
    }
}
