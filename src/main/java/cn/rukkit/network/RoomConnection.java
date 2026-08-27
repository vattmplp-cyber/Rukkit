/*
 * Copyright 2020-2022 RukkitDev Team and contributors.
 *
 * This project uses GNU Affero General Public License v3.0.You can find this license in the following link.
 * 本项目使用 GNU Affero General Public License v3.0 许可证，你可以在下方链接查看:
 *
 * https://github.com/RukkitDev/Rukkit/blob/master/LICENSE
 */

package cn.rukkit.network;

import cn.rukkit.Rukkit;
import cn.rukkit.game.NetworkPlayer;
import cn.rukkit.game.SaveData;
import cn.rukkit.network.command.GameCommand;
import cn.rukkit.network.packet.Packet;
import cn.rukkit.util.GameUtils;

import java.io.IOError;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoomConnection {
	private static final Logger log = LoggerFactory.getLogger(RoomConnection.class);

	public NetworkPlayer player;
	public ConnectionHandler handler;
	public NetworkRoom currectRoom;
	public volatile long pingTime;
	/** Timestamp of the most recent heartbeat response received from this client. */
	private volatile long lastPongTime;
	public int lastSyncTick = 0;
	public boolean checkSumSent = false;
	public int numberOfDesyncError = 0;

	private ScheduledFuture pingFuture;
	private ScheduledFuture teamFuture;
    public SaveData save;
	//public ChannelHandlerContext ctx;

	/**
	 * 心跳包任务
	 * Ping runnable.
	 */
	public class PingTasker implements Runnable {
		@Override
		public void run() {
			try {
				if (handler == null || handler.ctx == null || !handler.ctx.channel().isActive()) {
					stopPingTask();
					return;
				}

				long now = System.currentTimeMillis();
				if (Rukkit.getConfig().playerConnectionWatchdogEnabled
						&& lastPongTime > 0L
						&& now - lastPongTime > Math.max(1, Rukkit.getConfig().playerConnectionTimeoutSeconds) * 1000L) {
					long staleMs = now - lastPongTime;
					log.warn("Closing stale player connection: player={}, uuid={}, no heartbeat response for {} ms",
							player == null ? "<unknown>" : player.name,
							player == null ? "<unknown>" : player.uuid,
							staleMs);
					stopPingTask();
					handler.ctx.close();
					return;
				}

				GameOutputStream o = new GameOutputStream();
				o.writeLong(new Random().nextLong());
				o.writeByte(0);
				Packet p = o.createPacket(108);
				handler.ctx.writeAndFlush(p);
				pingTime = now;
			} catch (IOException | RuntimeException e) {
				stopPingTask();
				try {
					if (handler != null && handler.ctx != null) handler.ctx.close();
				} catch (RuntimeException ignored) {}
			}
		}
	}

	/**
	 * 队伍列表任务
	 * TeamTask Scheduler.
	 */
	public class TeamTasker implements Runnable {
		@Override
		public void run() {
			// TODO: Implement this method
			try
			{
				updateTeamList();
			}
			catch (IOException e)
			{
				stopTeamTask();
				//log.e(e);
				//e.printStackTrace();
				//cancel();
			}
		}
	}

	public RoomConnection(ConnectionHandler handler, NetworkRoom currectRoom) {
		this.handler = handler;
		this.currectRoom = currectRoom;
	}
	
	public void startPingTask() {
		if (pingFuture != null) return;
		long now = System.currentTimeMillis();
		lastPongTime = now;
		pingTime = 0L;
		int intervalSeconds = Math.max(1, Rukkit.getConfig().playerPingIntervalSeconds);
		int intervalMs = intervalSeconds * 1000;
		pingFuture = Rukkit.getThreadManager().schedule(new PingTasker(), intervalMs, intervalMs);
	}
	
	public void startTeamTask() {
		if (teamFuture != null) return;
		teamFuture = Rukkit.getThreadManager().schedule(new TeamTasker(), 1000, 1000);
	}
	
	public void stopPingTask() {
		if (pingFuture == null) return;
		Rukkit.getThreadManager().shutdownTask(pingFuture);
		pingFuture = null;
	}
	
	public void stopTeamTask() {
		if (teamFuture == null) return;
		Rukkit.getThreadManager().shutdownTask(teamFuture);
		teamFuture = null;
	}

	public void doChecksum() {
		try {
			handler.ctx.writeAndFlush(Packet.syncCheckSum(lastSyncTick));
		} catch (IOException ignored) {}
	}

	/**
	 * 发送公开聊天
	 * @param msg
	 */
	public void sendChat(String msg) {
		try {
			currectRoom.connectionManager.broadcast(Packet.chat(player.name, msg, player.playerIndex));
		} catch (IOException ignored) {}
	}

	/**
	 * 发送服务器信息 ([SERVER])
	 * @param msg
	 */
	public void sendServerMessage(String msg) {
		try {
			handler.ctx.writeAndFlush(Packet.chat("SERVER", msg, -1));
		} catch (IOException e) {}
	}

	/**
	 * 发送玩家信息
	 * @param from 来源玩家名
	 * @param msg 信息
	 * @param team 队伍
	 */
	public void sendMessage(String from, String msg, int team) {
		try {
			handler.ctx.writeAndFlush(Packet.chat(from, msg, team));
		} catch (IOException e) {}
	}

	/**
	 * 发送游戏指令
	 * @param cmd GameCommand实例.
	 */
	public void sendGameCommand(GameCommand cmd) {
        // If game is paused, throw everything.
        if (currectRoom.isPaused()) {
            return;
        }
		if (Rukkit.getConfig().useCommandQuere) {
			currectRoom.addCommand(cmd);
		} else {
			try {
				currectRoom.connectionManager.broadcast(Packet.gameCommand(currectRoom.getTickTime(), cmd));
			} catch (IOException ignored) {}
		}
	}
	
	public void updateTeamList() throws IOException {
		updateTeamList(currectRoom.isGaming());
	}

	/**
	 * 更新队伍列表。
	 * @param simpleMode 简单模式(1.14+).减少网络数据通信。
	 * @throws IOException
	 */
	public void updateTeamList(boolean simpleMode) throws IOException {
		GameOutputStream o = new GameOutputStream();
		//log.d("Sending teamlist...");
		o.writeInt(player.playerIndex);
		// 1.14新增
		o.writeBoolean(simpleMode);
		o.writeInt(Rukkit.getConfig().maxPlayer); //maxPlayer
		//1.14启用Gzip压缩
		GzipEncoder enc = o.getEncodeStream("teams", true);

		for (int i =0;i < Rukkit.getConfig().maxPlayer;i++)
		{
			NetworkPlayer playerp = currectRoom.playerManager.get(i);

			enc.stream.writeBoolean(!playerp.isEmpty);

			// Ignore empty player
			if (playerp.isEmpty) {
				continue;
			}

			//1.14
			//enc.stream.writeByte(0);

			enc.stream.writeInt(255);
			playerp.writePlayer(enc.stream, simpleMode);
		}
		o.flushEncodeData(enc);

		o.writeInt(currectRoom.config.fogType);
		o.writeInt(GameUtils.getMoneyFormat(currectRoom.config.credits));
		o.writeBoolean(true);
		//ai
		o.writeInt(1);
		//
		o.writeByte(4);
		//maxUnit
		o.writeInt(250);
		o.writeInt(250);

		//初始单位
		o.writeInt(currectRoom.config.startingUnits);
		o.writeFloat(currectRoom.config.income);
		o.writeBoolean(currectRoom.config.disableNuke);
		o.writeBoolean(false);
		o.writeBoolean(false);
		o.writeBoolean(currectRoom.config.sharedControl);

		Packet p = o.createPacket(Packet.PACKET_TEAM_LIST);

		handler.ctx.writeAndFlush(p);
	}

	/**
	 * 踢出玩家
	 * @param reason 踢出理由
	 */
	public void kick(String reason) {
		try {
			handler.ctx.writeAndFlush(Packet.kick(reason));
		} catch (IOException e) {}
	}

	/**
	 * 心跳包返回
	 */
	public void pong() {
		long now = System.currentTimeMillis();
		if (pingTime > 0L) {
			player.ping = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, now - pingTime));
		}
		lastPongTime = now;
	}
}
