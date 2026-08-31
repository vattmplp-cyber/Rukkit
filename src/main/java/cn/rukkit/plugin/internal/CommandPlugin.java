/*
 * Copyright 2020-2022 RukkitDev Team and contributors.
 *
 * This project uses GNU Affero General Public License v3.0.You can find this license in the following link.
 * 本项目使用 GNU Affero General Public License v3.0 许可证，你可以在下方链接查看:
 *
 * https://github.com/RukkitDev/Rukkit/blob/master/LICENSE
 */

package cn.rukkit.plugin.internal;
//import cn.rukkit.plugin.InternalRukkitPlugin;
import cn.rukkit.Rukkit;
import cn.rukkit.command.ChatCommand;
import cn.rukkit.command.ChatCommandListener;
import cn.rukkit.command.CommandManager;
import cn.rukkit.command.ServerCommandListener;
import cn.rukkit.config.RoundConfig;
import cn.rukkit.event.EventHandler;
import cn.rukkit.event.EventListener;
import cn.rukkit.event.player.PlayerChatEvent;
import cn.rukkit.game.NetworkPlayer;
import cn.rukkit.game.PingType;
import cn.rukkit.game.PlayerManager;
import cn.rukkit.game.map.CustomMapLoader;
import cn.rukkit.game.map.OfficialMap;
import cn.rukkit.network.NetworkRoom;
import cn.rukkit.network.RoomConnection;
import cn.rukkit.network.RoomConnectionManager;
import cn.rukkit.network.packet.Packet;
import cn.rukkit.plugin.PluginConfig;
import cn.rukkit.util.LangUtil;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandPlugin extends InternalRukkitPlugin implements ChatCommandListener {

	private final Map<NetworkRoom, AfkSession> afkSessions = new ConcurrentHashMap<>();

	private static final class AfkSession {
		final NetworkPlayer admin;
		final NetworkPlayer requester;
		volatile int remainingSeconds;
		volatile ScheduledFuture<?> future;

		AfkSession(NetworkPlayer admin, NetworkPlayer requester, int remainingSeconds) {
			this.admin = admin;
			this.requester = requester;
			this.remainingSeconds = remainingSeconds;
		}
	}

	int totalInfo = 0;
	Logger log = LoggerFactory.getLogger(CommandPlugin.class);

	public class CommandEventListener implements EventListener {
		@EventHandler
		public void playerChat(PlayerChatEvent e) {
			if (e.getPlayer() == null || !e.getPlayer().isAdmin) return;
			if (Rukkit.getConfig().afkCancelOnAdminChat) {
				stopAfkCountdown(e.getPlayer().getRoom(),
						Rukkit.getConfig().notification(
							"rukkit.afk.cancelled",
							"Countdown stopped!",
							"adminName", e.getPlayer().name));
			}
		}
	}

	@Override
	public boolean onSend(RoomConnection con, String[] args) {
		// TODO: Implement this method
		StringBuilder build = new StringBuilder();
		build.append("Rukkit Server v" + Rukkit.RUKKIT_VERSION + "\n");
		build.append("Rukkit Plugin API v" + Rukkit.PLUGIN_API_VERSION);
		con.sendServerMessage(build.toString());
		return false;
	}

	static class VersionCallback implements ChatCommandListener {
		@Override
		public boolean onSend(RoomConnection con, String[] args) {
			StringBuilder build = new StringBuilder();
			build.append("Rukkit Server v" + Rukkit.RUKKIT_VERSION + "\n");
			build.append("Rukkit Plugin API v" + Rukkit.PLUGIN_API_VERSION);
			con.sendServerMessage(build.toString());
			return false;
		}
	}

	@Override
	public void loadConfig() {
		// TODO: Implement this method
		config = new PluginConfig();
		config.name = "Basic Chat Command Plugin";
		config.author = "rukkit";
		config.version = Rukkit.RUKKIT_VERSION;
		config.id = "command-plugin";
		config.pluginClass = "cn.rukkit.plugin.internal.CommandPlugin";
		config.apiVersion = Rukkit.PLUGIN_API_VERSION;
	}

	public class KickCallBack implements ChatCommandListener {
		@Override
		public boolean onSend(RoomConnection con, String[] args) {
			// TODO: Implement this method
			if (con.player.isAdmin && args.length > 1 || !con.currectRoom.isGaming()) {
				int id = Integer.parseInt(args[1]);
				NetworkPlayer player = con.currectRoom.playerManager.get(id);
				try {
					player.isNull();
					player.getConnection().kick(LangUtil.getString("chat.kicked"));
				} catch (ArrayIndexOutOfBoundsException e) {
					con.sendServerMessage(LangUtil.getString("chat.playerEmpty"));
				}
			}
			return true;
		}
	}

	public static class TeamChatCallback implements ChatCommandListener {
		@Override
		public boolean onSend(RoomConnection con, String[] args) {
			// TODO: Implement this method
			if (args.length < 1) return false;
			con.player.sendTeamMessage(args[0]);
			return false;
		}
	}

	public static class MapsCallback implements ChatCommandListener {
		private final int type;
		public MapsCallback(int type) { this.type = type; }

		private String mapFilterMessage() {
			int min = Rukkit.getConfig().officialMapMinPlayers;
			int max = Rukkit.getConfig().officialMapMaxPlayers;
			if (min == max) return "Map selection is restricted to " + min + "-player maps on this server.";
			return "Map selection is restricted to maps for " + min + "-" + max + " players on this server.";
		}

		@Override
		public boolean onSend(RoomConnection con, String[] args) {
			if (type == 0) {
				StringBuilder build = new StringBuilder();
				if (args.length > 0) {
					build.append("- Maps -  Page ").append(args[0]).append(" \n");
					int page = Integer.parseInt(args[0]) - 1;
					int from = Math.max(0, page * 10);
					int to = Math.min(OfficialMap.maps.length, from + 10);
					for (int i = from; i < to; i++) build.append(String.format("[%d] %s", i, OfficialMap.maps[i])).append("\n");
				} else {
					build.append("- Help -  Page 1 \n");
					int to = Math.min(10, OfficialMap.maps.length);
					for (int i = 0; i < to; i++) build.append(String.format("[%d] %s", i, OfficialMap.maps[i])).append("\n");
				}
				con.sendServerMessage(build.toString());
				return false;
			}

			if (!con.player.isAdmin || args.length == 0) return false;

			if (args[0].startsWith("'")) {
				String[] parts = args[0].split("'");
				String mapString = parts.length > 1 ? parts[1] : args[0];
				boolean found = false;
				for (int i = 0; i < OfficialMap.mapsName.length; i++) {
					if (OfficialMap.mapsName[i].toLowerCase(java.util.Locale.ROOT).contains(mapString.toLowerCase(java.util.Locale.ROOT))) {
						Rukkit.getRoundConfig().mapName = OfficialMap.maps[i];
						Rukkit.getRoundConfig().mapType = 0;
						found = true;
						try { con.currectRoom.broadcast(Packet.serverInfo(con.currectRoom.config)); con.handler.ctx.writeAndFlush(Packet.serverInfo(con.currectRoom.config, true)); } catch (IOException ignored) {}
						break;
					}
				}
				if (!found && Rukkit.getConfig().officialMapFilterEnabled && OfficialMap.isMapFilteredOut(mapString, Rukkit.getConfig().officialMapMinPlayers, Rukkit.getConfig().officialMapMaxPlayers)) con.sendServerMessage(mapFilterMessage());
				return false;
			}

			try {
				int id = Integer.parseInt(args[0]);
				if (id < 0 || id >= OfficialMap.maps.length) {
					con.sendServerMessage("Invalid map selection. Use 'maps' to see the available maps.");
					return false;
				}
				Rukkit.getRoundConfig().mapName = OfficialMap.maps[id];
				Rukkit.getRoundConfig().mapType = 0;
			} catch (NumberFormatException e) {
				con.sendServerMessage("Invalid map selection. Use 'maps' to see the available maps.");
			}
			return false;
		}
	}

	public static class CustomMapsCallback implements ChatCommandListener {
		private final int type;
		public CustomMapsCallback(int type) {
			this.type = type;
		}
		@Override
		public boolean onSend(RoomConnection con, String[] args) {
			// TODO: Implement this method
			// Maps
			if (type == 0) {
				StringBuilder build = new StringBuilder();
				List<String> li = CustomMapLoader.getMapNameList();
					if (args.length > 0) {
						build.append("- CustomMaps -  Page ").append(args[0]).append(" \n");
						int page = Integer.parseInt(args[0]) - 1;
						for (int i = page * 10;i < li.size();i++) {
							if (i > page * 10 + 10) break;
							build.append(String.format("[%d] %s", i, li.get(i))).append("\n");
						}
					} else {
						build.append("- Help -  Page 1 \n");
						for (int i = 0; i < (Math.min(li.size(), 10)); i++) {
							build.append(String.format("[%d] %s", i, li.get(i))).append("\n");
						}
					}
					con.sendServerMessage(build.toString());
			} else {
				if (con.player.isAdmin && args.length > 0) {
					ArrayList<String> mapList = CustomMapLoader.getMapNameList();
					int id = Integer.parseInt(args[0]);
					Rukkit.getRoundConfig().mapName = mapList.get(id).toString();
					Rukkit.getRoundConfig().mapType = 1;
					try {
						con.currectRoom.broadcast(Packet.serverInfo(con.currectRoom.config));
						con.handler.ctx.writeAndFlush(Packet.serverInfo(con.currectRoom.config, true));
					} catch (IOException ignored) {}
				}
			}
			return false;
		}
	}


	// TODO: -move && -self-move 操作
	class MoveCallback implements ChatCommandListener {
		private int type;
		public MoveCallback(int type) {
			this.type = type;
		}
		@Override
		public boolean onSend(RoomConnection con, String[] cmd) {
			switch (type) {
					//move
				case 0:
					if (!con.player.isAdmin || con.currectRoom.isGaming() || cmd.length < 2) {
						// Do nothing.
					} else {
						PlayerManager playerGroup = con.currectRoom.playerManager;
						NetworkPlayer fromPlayer = playerGroup.get(Integer.parseInt(cmd[0]) - 1);
						NetworkPlayer targetPlayer = playerGroup.get(Integer.parseInt(cmd[1]) - 1);
						if (cmd.length == 3) {
							int team = Integer.parseInt(cmd[2]);
							if (team == -1 || team == -2)
							{
								if (targetPlayer.playerIndex % 2 == 1) {
									fromPlayer.team = 1;
								} else {
									fromPlayer.team = 0;
								}
							} else {
								fromPlayer.team = team;
							}
						}
						try {
							if (fromPlayer.movePlayer(Integer.parseInt(cmd[1]) - 1)) {
								con.sendServerMessage(LangUtil.getString("chat.moveComplete"));
							} else {
								int fromslot, toslot;
								fromslot = fromPlayer.playerIndex;
								toslot = targetPlayer.playerIndex;
								if (fromslot == toslot) {
									con.sendServerMessage("not same player!");
									break;
								}
								playerGroup.remove(targetPlayer);
								fromPlayer.movePlayer(toslot);
								targetPlayer.movePlayer(fromslot);
							}
						} catch (Exception e) {
							//fromPlayer.movePlayer(Integer.parseInt(cmd[1]) - 1);
							e.printStackTrace();
						}
					}
					break;
					// Self-move
				case 1:
					if (con.currectRoom.isGaming() || cmd.length < 1) {
						// Do nothing.
					} else {
						try {
							if (cmd.length == 2) {
								int team = Integer.parseInt(cmd[1]);
								if (team == -1 || team == -2)
								{
									if ((Integer.parseInt(cmd[0]) - 1) % 2 == 1) {
										con.player.team = 1;
									} else {
										con.player.team = 0;
									}
								} else {
									con.player.team = team;
								}
							}
							if (con.player.movePlayer(Integer.parseInt(cmd[0]) - 1)) {
								con.sendServerMessage(LangUtil.getString("chat.moveComplete"));
							} else {
								con.sendServerMessage(LangUtil.getString("chat.playerExist"));
							}
						} catch (Exception e) {
							log.error("Error:", e);
						}
					}
			}
			return false;
		}
	}

	// TODO: -qc 操作
	class QcCallback implements ChatCommandListener {
		@Override
		public boolean onSend(RoomConnection con, String[] args) {
			if (args.length <= 0) return false;
			getLogger().info("Player {} issued command: {}", con.player.name, args[0]);
			Rukkit.getCommandManager().executeChatCommand(con, args[0].substring(1));
			return false;
		}
	}

	class TeamCallback implements ChatCommandListener {
		private int type;
		public TeamCallback(int type) {
			this.type = type;
		}
		@Override
		public boolean onSend(RoomConnection con, String[] args) {
			switch (type) {
					//team
				case 0:
					if (con.currectRoom.isGaming() || !con.player.isAdmin || args.length < 2) {
						// Do nothing.
					} else {
						try {
							int team = (Integer.parseInt(args[1]) - 1);
							int slot = Integer.parseInt(args[0]) - 1;
							if (team == -1 || team == -2) {
								if (slot % 2 == 1) {
									con.currectRoom.playerManager
											.get(slot).team = 1;
								} else {
									con.currectRoom.playerManager
											.get(slot).team = 2;
								}
							}
							con.currectRoom.playerManager
								.get(slot).team = team;
						} catch (NullPointerException e) {
							con.sendServerMessage(LangUtil.getString("chat.playerEmpty"));
						}
					}
					break;
					//self-team
				case 1:
					if (args.length < 1) return false;
					// Never got exceptions...
					con.player.team = Integer.parseInt(args[0]) - 1;

			}
			return false;
		}
	}

	static class HelpCallback implements ChatCommandListener {

    @Override
    public boolean onSend(RoomConnection con, String[] args) {
        List<ChatCommand> availableCommands = new ArrayList<>();

        for (Object value : Rukkit.getCommandManager()
                .getLoadedCommand()
                .values()) {

            ChatCommand cmd = (ChatCommand) value;

            boolean allowed = hasPermission(con, cmd.cmd);

            if (allowed || Rukkit.getConfig().helpShowDisabledCommands) {
                availableCommands.add(cmd);
            }
        }

        int pageSize = Rukkit.getConfig().helpPageSize;
        if (pageSize < 1) {
            pageSize = 10;
        }

        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }

        if (page < 1) {
            page = 1;
        }

        int totalPages = Math.max(
                1,
                (availableCommands.size() + pageSize - 1) / pageSize
        );

        if (page > totalPages) {
            page = totalPages;
        }

        StringBuilder build = new StringBuilder();
        build.append("- Help - Page ")
                .append(page)
                .append("/")
                .append(totalPages)
                .append("\n");

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, availableCommands.size());

        for (int i = start; i < end; i++) {
            ChatCommand cmd = availableCommands.get(i);

            build.append("-")
                    .append(cmd.cmd);

            if (Rukkit.getConfig().helpShowDescriptions) {
                build.append(" : ")
                        .append(cmd.helpMessage);
            }

            build.append("\n");
        }

        con.sendServerMessage(build.toString());
        return false;
    }
}

	class InfoCallback implements ChatCommandListener {
		@Override
		public boolean onSend(RoomConnection con, String[] args) {
			totalInfo++;
			log.warn("{} send a info: {}", con.player.name, args[0]);
//			if (totalInfo >= 3) {
//				con.currectRoom.connectionManager.broadcastServerMessage("Desync founded!Server is resyncing...");
//				con.currectRoom.syncGame();
//				totalInfo = 0;
//			}
			return false;
		}
	}

	class StartCallback implements ChatCommandListener {
		@Override
		public boolean onSend(RoomConnection con, String[] args) {
			if (con.currectRoom.isGaming() || !con.player.isAdmin) {
				// Do nothing.
			} else {
				if (con.currectRoom.connectionManager.size() < Rukkit.getConfig().minStartPlayer) {
					con.currectRoom.connectionManager.broadcastServerMessage(MessageFormat.format(LangUtil.getString("chat.minStartPlayer"), Rukkit.getConfig().minStartPlayer));
				} else {
					con.currectRoom.startGame();
				}
			}
			return true;
		}
	}

	class SetFogCallback implements ChatCommandListener {
		@Override
		public boolean onSend(RoomConnection con, String[] args) {
			if (con.currectRoom.isGaming() || !con.player.isAdmin || args.length < 1) {
				// Do nothing.
			} else {
				RoundConfig cfg = Rukkit.getRoundConfig();
				switch (args[0]) {
					case "off":
						cfg.fogType = 0;
						break;
					case "basic":
						cfg.fogType = 1;
						break;
					case "los":
						cfg.fogType = 2;
						break;
					default:
						cfg.fogType = 2;
				}
				try {
					con.currectRoom.broadcast(Packet.serverInfo(con.currectRoom.config));
					con.handler.ctx.writeAndFlush(Packet.serverInfo(con.currectRoom.config, true));
				} catch (IOException ignored) {}
			}
			return false;
		}
	}

	class StartingUnitCallback implements ChatCommandListener {
		@Override
		public boolean onSend(RoomConnection con, String[] args) {
			if (con.currectRoom.isGaming() || !con.player.isAdmin || args.length < 1) {
				// Do nothing.
			} else {
				Rukkit.getRoundConfig().startingUnits = Integer.parseInt(args[0]);
				try {
					con.currectRoom.broadcast(Packet.serverInfo(con.currectRoom.config));
					con.handler.ctx.writeAndFlush(Packet.serverInfo(con.currectRoom.config, true));
				} catch (IOException ignored) {}
			}
			return false;
		}
	}

	class ShareCallback implements ChatCommandListener {
		@Override
		public boolean onSend(RoomConnection con, String[] args) {
			if (con.currectRoom.isGaming() || args.length < 1) {
				// Do nothing.
			} else {
				RoomConnectionManager ChannelGroups = con.currectRoom.connectionManager;
				switch (args[0]) {
					case "on":
						con.player.isSharingControl = true;
						ChannelGroups.broadcastServerMessage(con.player.name + "stopped Shared control!");
						break;
					case "off":
						con.player.isSharingControl = false;
						ChannelGroups.broadcastServerMessage(con.player.name + "started Shared control.");
						break;
					default:
						con.player.isSharingControl = false;
						ChannelGroups.broadcastServerMessage(con.player.name + "started Shared control!");
				}
			}
			return false;
		}
	}

	class SharedControlCallback implements ChatCommandListener {
		@Override
		public boolean onSend(RoomConnection con, String[] args) {
			if (con.currectRoom.isGaming() || !con.player.isAdmin || args.length < 1) {
				// Do nothing.
			} else {
				Rukkit.getRoundConfig().sharedControl = Boolean.parseBoolean(args[0]);
				try {
					con.currectRoom.broadcast(Packet.serverInfo(con.currectRoom.config));
					con.handler.ctx.writeAndFlush(Packet.serverInfo(con.currectRoom.config, true));
				} catch (IOException ignored) {}
			}
			return false;
		}
	}

	class NukeCallback implements ChatCommandListener {
		@Override
		public boolean onSend(RoomConnection con, String[] args) {
			if (con.currectRoom.isGaming() || !con.player.isAdmin || args.length < 1) {
				// Do nothing.
			} else {
				Rukkit.getRoundConfig().disableNuke = !Boolean.parseBoolean(args[0]);
				try {
					con.currectRoom.broadcast(Packet.serverInfo(con.currectRoom.config));
					con.handler.ctx.writeAndFlush(Packet.serverInfo(con.currectRoom.config, true));
				} catch (IOException ignored) {}
			}
			return false;
		}
	}

	class IncomeCallback implements ChatCommandListener {
		@Override
		public boolean onSend(RoomConnection con, String[] args) {
			if (con.currectRoom.isGaming() || !con.player.isAdmin || args.length < 1) {
				// Do nothing.
			} else {
				Rukkit.getRoundConfig().income = Float.parseFloat(args[0]);
				if (Rukkit.getRoundConfig().income > 100 || Rukkit.getRoundConfig().income < 0) {
					Rukkit.getRoundConfig().income = 1;
				}
				try {
					con.currectRoom.broadcast(Packet.serverInfo(con.currectRoom.config));
					con.handler.ctx.writeAndFlush(Packet.serverInfo(con.currectRoom.config, true));
				} catch (IOException ignored) {}
			}
			return false;
		}
	}

	class CreditsCallback implements ChatCommandListener {
		@Override
		public boolean onSend(RoomConnection con, String[] args) {
			if (con.currectRoom.isGaming() || !con.player.isAdmin || args.length < 1) {
				// Do nothing.
			} else {
				Rukkit.getRoundConfig().credits = Integer.parseInt(args[0]);
				try {
					con.currectRoom.broadcast(Packet.serverInfo(con.currectRoom.config));
					con.handler.ctx.writeAndFlush(Packet.serverInfo(con.currectRoom.config, true));
				} catch (IOException ignored) {}
			}
			return false;
		}
	}

    class SyncCallback implements ChatCommandListener {
        @Override
        public boolean onSend(RoomConnection con, String[] args) {
			if (con.currectRoom.isGaming()) {
				con.currectRoom.vote.submitVoting(() -> {
					con.currectRoom.syncGame();
				}, "sync", "有玩家发起了同步！输入-y或者-n来投票！", 15);
			}
			return false;
        }
    }

	public class AgreeCallback implements ChatCommandListener {
		@Override
		public boolean onSend(RoomConnection con, String[] args) {
			if (con.currectRoom.vote.disabledVote) {
				con.sendServerMessage("投票已禁用！");
				return false;
			}
			if (con.currectRoom.vote.isVoting) {
				if (con.currectRoom.vote.agree(con.player.playerIndex)) {
					con.sendServerMessage(LangUtil.getString("nostop.vote.submit"));
				} else {
					con.sendServerMessage(LangUtil.getString("nostop.vote.alreadySubmit"));
				}
			} else {
				con.sendServerMessage(LangUtil.getString("nostop.vote.noCurrentVote"));
			}
			return false;
		}
	}



	public class DisagreeCallback implements ChatCommandListener {
		@Override
		public boolean onSend(RoomConnection con, String[] args) {
			if (con.currectRoom.vote.disabledVote) {
				con.sendServerMessage("投票已禁用！");
				return false;
			}
			if (con.currectRoom.vote.isVoting) {
				if (con.currectRoom.vote.disagree(con.player.playerIndex)) {
					con.sendServerMessage(LangUtil.getString("nostop.vote.submit"));
				} else {
					con.sendServerMessage(LangUtil.getString("nostop.vote.alreadySubmit"));
				}
			} else {
				con.sendServerMessage(LangUtil.getString("nostop.vote.noCurrentVote"));
			}
			return false;
		}
	}

    class DumpSyncCallBack implements ChatCommandListener {
        @Override
        public boolean onSend(RoomConnection con, String[] args) {

            return false;
        }
    }

	class ChksumCallback implements ChatCommandListener {
		@Override
		public boolean onSend(RoomConnection con, String[] args) {
			try {
				con.currectRoom.broadcast(Packet.syncCheckSum(con.currectRoom.getCurrentStep()));
			} catch (IOException e) {
				//con.sendChat(
			}
			return false;
		}
	}

	class PingCallBack implements ChatCommandListener {
		@Override
		public boolean onSend(RoomConnection con, String[] args) {
			if (args.length >= 2) {
				float x = Float.parseFloat(args[0]);
				float y = Float.parseFloat(args[1]);
				//String name = args[0];
				try {
					con.currectRoom.broadcast(Packet.gamePing(con.currectRoom, con.player.playerIndex, PingType.happy, x, y));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			return false;
		}
	}

	static class StateCallback implements ChatCommandListener {
		@Override
		public boolean onSend(RoomConnection con, String[] args) {
			// TODO: Implement this method
			StringBuilder build = new StringBuilder();
			build.append("- State - \n");
			build.append("RAM Usage: " +  (Runtime.getRuntime().freeMemory() / 10240) + "M/" + (Runtime.getRuntime().totalMemory()) / 10240 + "M\n");
			build.append("Connections: " + Rukkit.getGlobalConnectionManager().size());
			build.append("ThreadManager Tasks: " + Rukkit.getThreadManager().getActiveThreadCount() + "/" + Rukkit.getConfig().threadPoolCount);
			try {
				con.handler.ctx.writeAndFlush(Packet.chat("SERVER",
						build.toString(), -1));
			} catch (IOException e) {}
			return false;
		}
	}

	class PlayerListCallback implements ChatCommandListener {
		@Override
		public boolean onSend(RoomConnection con, String[] args) {
			StringBuffer buffer = new StringBuffer("- Players -\n");
			for (RoomConnection conn: con.currectRoom.connectionManager.getConnections()) {
				buffer.append(String.format("%s (Team %d) (%d ms)\n",conn.player.name, conn.player.team, (System.currentTimeMillis() - conn.pingTime)));
			}
			con.sendServerMessage(buffer.toString());
			return false;
		}
	}

	class SurrenderCallback implements ChatCommandListener {
		@Override
		public boolean onSend(RoomConnection con, String[] args) {
			if (!con.player.isSurrounded) {
				try {
					con.currectRoom.broadcast(Packet.gameSurrounder(con.currectRoom, con.player.playerIndex));
					con.currectRoom.connectionManager.broadcastServerMessage(String.format("Player %s surrounded!", con.player.name));
					con.player.isSurrounded = true;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			return false;
		}
	}

	static class AfkCallback implements ChatCommandListener {
		@Override
		public boolean onSend(RoomConnection con, String[] args) {
			// The callback is static for compatibility with the existing command registration.
			CommandPlugin plugin = activeInstance;
			if (plugin == null) return false;
			return plugin.startAfkCountdown(con);
		}
	}

	private static volatile CommandPlugin activeInstance;

	private boolean startAfkCountdown(RoomConnection con) {
		if (!Rukkit.getConfig().afkEnabled) {
			con.sendServerMessage("AFK control is disabled on this server.");
			return false;
		}

		NetworkRoom room = con.currectRoom;
		if (room == null || room.isGaming()) {
			con.sendServerMessage("AFK control is available only in the lobby.");
			return false;
		}

		NetworkPlayer admin = room.playerManager.getAdmin();
		if (admin == null || admin.isEmpty) {
			con.sendServerMessage("There is no active server administrator.");
			return false;
		}

		if (con.player == admin) {
			con.sendServerMessage("You are already in control of this server.");
			return false;
		}

		if (con.player.isEmpty || con.player.getConnection() == null
				|| con.player.getConnection().handler == null
				|| con.player.getConnection().handler.ctx == null
				|| !con.player.getConnection().handler.ctx.channel().isActive()) {
			con.sendServerMessage("You must stay connected to request AFK control.");
			return false;
		}

		if (afkSessions.containsKey(room)) {
			con.sendServerMessage("An AFK countdown is already running.");
			return false;
		}

		int countdown = Math.max(1, Rukkit.getConfig().afkCountdownSeconds);
		AfkSession session = new AfkSession(admin, con.player, countdown);
		AfkSession previous = afkSessions.putIfAbsent(room, session);
		if (previous != null) {
			con.sendServerMessage("An AFK countdown is already running.");
			return false;
		}

		broadcastAfkStart(room, session);
		session.future = Rukkit.getThreadManager().schedule(new Runnable() {
			@Override
			public void run() {
				AfkSession current = afkSessions.get(room);
				if (current != session) return;

				NetworkPlayer currentAdmin = room.playerManager.getAdmin();
				if (room.isGaming() || currentAdmin != session.admin || !isConnected(session.requester)) {
					stopAfkSession(room, session, false,
							"AFK countdown cancelled.");
					return;
				}

				int left = --session.remainingSeconds;
				if (left <= 0) {
					finishAfkCountdown(room, session);
				} else if (shouldAnnounceAfk(left, countdown)) {
					broadcastAfkWarning(room, session, left);
				}
			}
		}, 1000, 1000);

		return false;
	}

	private boolean isConnected(NetworkPlayer player) {
		return player != null && !player.isEmpty && player.getConnection() != null
				&& player.getConnection().handler != null
				&& player.getConnection().handler.ctx != null
				&& player.getConnection().handler.ctx.channel().isActive();
	}

	private boolean shouldAnnounceAfk(int seconds, int total) {
		int interval = Math.max(1, Rukkit.getConfig().afkWarningIntervalSeconds);
		int finalSeconds = Math.max(0, Rukkit.getConfig().afkFinalWarningSeconds);
		return seconds == total || seconds % interval == 0 || (finalSeconds > 0 && seconds == finalSeconds);
	}

	private void broadcastAfkStart(NetworkRoom room, AfkSession session) {
		String msg = Rukkit.getConfig().notification(
				"rukkit.afk.start",
				"AFK timer started.\n'{adminName}' has {seconds} seconds to send any chat message",
				"adminName", session.admin.name,
				"seconds", session.remainingSeconds,
				"requesterName", session.requester.name);
		room.connectionManager.broadcastServerMessage(msg);
	}

	private void broadcastAfkWarning(NetworkRoom room, AfkSession session, int seconds) {
		String msg = Rukkit.getConfig().notification(
				"rukkit.afk.warning",
				"'{adminName}' has {seconds} seconds to send any chat message",
				"adminName", session.admin.name,
				"seconds", seconds);
		room.connectionManager.broadcastServerMessage(msg);
	}

	private void finishAfkCountdown(NetworkRoom room, AfkSession session) {
		if (!afkSessions.remove(room, session)) return;
		if (session.future != null) {
			Rukkit.getThreadManager().shutdownTask(session.future);
			session.future = null;
		}

		NetworkPlayer admin = room.playerManager.getAdmin();
		NetworkPlayer requester = session.requester;
		if (room.isGaming() || admin != session.admin || !isConnected(requester)) {
			return;
		}

		if (Rukkit.getConfig().afkTransferControl) {
			if (admin.giveAdmin(requester.playerIndex)) {
				admin.isAfk = true;
				requester.isAfk = false;
				admin.updateServerInfo();
				requester.updateServerInfo();
				String msg = Rukkit.getConfig().notification(
						"rukkit.afk.transferred",
						"'{adminName}' is AFK, control switched to: '{newAdminName}'",
						"adminName", admin.name,
						"newAdminName", requester.name);
				room.connectionManager.broadcastServerMessage(msg);
			}
		}
	}

	private boolean stopAfkCountdown(NetworkRoom room, String message) {
		AfkSession session = afkSessions.remove(room);
		if (session == null) return false;
		if (session.future != null) {
			Rukkit.getThreadManager().shutdownTask(session.future);
			session.future = null;
		}
		if (message != null && !message.isEmpty()) room.connectionManager.broadcastServerMessage(message);
		return true;
	}

	private void stopAfkSession(NetworkRoom room, AfkSession session, boolean announce, String message) {
		if (!afkSessions.remove(room, session)) return;
		if (session.future != null) {
			Rukkit.getThreadManager().shutdownTask(session.future);
			session.future = null;
		}
		if (announce && message != null) room.connectionManager.broadcastServerMessage(message);
	}

	private void onAdminCommandActivity(RoomConnection con) {
		if (!Rukkit.getConfig().afkCancelOnAdminCommand) return;
		stopAfkCountdown(con.currectRoom, Rukkit.getConfig().notification(
				"rukkit.afk.cancelled",
				"Countdown stopped!",
				"adminName", con.player.name));
	}

	/*class InfoCallback implements ChatCommandListener {
	 @Override
	 public boolean onSend(Connection con, String[] args) {
	 return false;
	 }
	 }*/

	private static boolean hasPermission(RoomConnection con, String command) {
    if (con == null || con.player == null) {
        return false;
    }

    boolean isAdmin = con.player.isAdmin;

    Boolean allowed;

    if (isAdmin) {
        allowed = Rukkit.getConfig().adminPermissions.get(command);
    } else {
        allowed = Rukkit.getConfig().playerPermissions.get(command);
    }

    return allowed != null && allowed;
}

	@Override
	public void onLoad() {
		// TODO: Implement this method
		getLogger().info("CommandPlugin::onLoad()");
		if (Rukkit.getConfig().officialMapFilterEnabled) {
			OfficialMap.applyPlayerCountFilter(
					Rukkit.getConfig().officialMapMinPlayers,
					Rukkit.getConfig().officialMapMaxPlayers);
		}
		activeInstance = this;
		CommandManager mgr = Rukkit.getCommandManager();
		mgr.registerAdminCommandActivityListener(this::onAdminCommandActivity);
		mgr.registerCommand(new ChatCommand("help", LangUtil.getString("chat.help"), 1, new HelpCallback(), this));
		mgr.registerCommand(new ChatCommand("state", LangUtil.getString("chat.state"), 0, new StateCallback(), this));
		mgr.registerCommand(new ChatCommand("version", LangUtil.getString("chat.version"), 0, this, this));
		//mgr.registerCommand(new ChatCommand("team", "Send a team message.", 1, new TeamChatCallback(), this));
		mgr.registerCommand(new ChatCommand("t", LangUtil.getString("chat.t"), 1, new TeamChatCallback(), this));
		mgr.registerCommand(new ChatCommand("maps", LangUtil.getString("chat.maps"), 1, new MapsCallback(0), this));
		mgr.registerCommand(new ChatCommand("map", LangUtil.getString("chat.map"), 1, new MapsCallback(1), this, true));
		mgr.registerCommand(new ChatCommand("cmaps", LangUtil.getString("chat.cmaps"), 1, new CustomMapsCallback(0), this));
		mgr.registerCommand(new ChatCommand("cmap", LangUtil.getString("chat.cmap"), 1, new CustomMapsCallback(1), this, true));
		mgr.registerCommand(new ChatCommand("kick", LangUtil.getString("chat.kick"), 1, new KickCallBack(), this, true));
		mgr.registerCommand(new ChatCommand("team", LangUtil.getString("chat.team"), 2, new TeamCallback(0), this, true));
		mgr.registerCommand(new ChatCommand("self_team", LangUtil.getString("chat.self_team"), 1, new TeamCallback(1), this));
		mgr.registerCommand(new ChatCommand("move", LangUtil.getString("chat.move"), 3, new MoveCallback(0), this, true));
		mgr.registerCommand(new ChatCommand("self_move", LangUtil.getString("chat.self_move"), 2, new MoveCallback(1), this));
		mgr.registerCommand(new ChatCommand("qc", LangUtil.getString("chat.qc"), 1, new QcCallback(), this));
		mgr.registerCommand(new ChatCommand("fog", LangUtil.getString("chat.fog"), 1, new SetFogCallback(), this, true));
		mgr.registerCommand(new ChatCommand("nukes", LangUtil.getString("chat.nukes"), 1, new NukeCallback(), this, true));
		mgr.registerCommand(new ChatCommand("startingunits", LangUtil.getString("chat.startingunits"), 1, new StartingUnitCallback(), this, true));
		mgr.registerCommand(new ChatCommand("income", LangUtil.getString("chat.income"), 1, new IncomeCallback(), this, true));
		mgr.registerCommand(new ChatCommand("share", LangUtil.getString("chat.share"), 1, new ShareCallback(), this));
		mgr.registerCommand(new ChatCommand("credits", LangUtil.getString("chat.credits"), 1, new CreditsCallback(), this, true));
		mgr.registerCommand(new ChatCommand("start", LangUtil.getString("chat.start"), 1, new StartCallback(), this, true));
        mgr.registerCommand(new ChatCommand("sync", LangUtil.getString("chat.sync"), 0, new SyncCallback(), this, true));
		mgr.registerCommand(new ChatCommand("i", LangUtil.getString("chat.i"), 1, new InfoCallback(), this));
		mgr.registerCommand(new ChatCommand("chksum", LangUtil.getString("chat.chksum"), 0, new ChksumCallback(), this));
		mgr.registerCommand(new ChatCommand("maping", LangUtil.getString("chat.maping"), 2, new PingCallBack(), this));
		mgr.registerCommand(new ChatCommand("list", LangUtil.getString("chat.list"), 0, new PlayerListCallback(), this));
		mgr.registerCommand(new ChatCommand("surrender", LangUtil.getString("chat.surrender"), 0, new SurrenderCallback(), this));
		mgr.registerCommand(new ChatCommand("afk", LangUtil.getString("chat.afk"), 0, new AfkCallback(), this));
		mgr.registerCommand(new ChatCommand("y", LangUtil.getString("nostop.y"), 0, new AgreeCallback(), this));
		mgr.registerCommand(new ChatCommand("n", LangUtil.getString("nostop.n"), 0, new DisagreeCallback(), this));
		getPluginManager().registerEventListener(new CommandEventListener(), this);
	}

	@Override
	public void onEnable() {
		// TODO: Implement this method
		getLogger().info("CommandPlugin::onEnable()");
	}

	@Override
	public void onDisable() {
		for (NetworkRoom room : new ArrayList<>(afkSessions.keySet())) {
			stopAfkCountdown(room, null);
		}
		if (activeInstance == this) activeInstance = null;
	}

	@Override
	public void onStart() {
		// TODO: Implement this method
	}

	@Override
	public void onDone() {
		// TODO: Implement this method
	}

}
