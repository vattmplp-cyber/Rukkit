/*
 * Copyright 2020-2022 RukkitDev Team and contributors.
 *
 * This project uses GNU Affero General Public License v3.0.You can find this license in the following link.
 * 本项目使用 GNU Affero General Public License v3.0 许可证，你可以在下方链接查看:
 *
 * https://github.com/RukkitDev/Rukkit/blob/master/LICENSE
 */

package cn.rukkit.config;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

public class RukkitConfig extends BaseConfig
{
	public String serverUser = "RUKKIT";
	public String welcomeMsg = "Welcome to Rukkit server, {playerName}!";
	public String serverMotd = "My Rukkit server";
	public int serverPort = 5123;
	public int maxPlayer = 10;
	public int maxRoom = 5;
	public int minStartPlayer = 4;
	public boolean gameStartCountdownEnabled = true;
	public int gameStartCountdownSeconds = 5;
    public boolean syncEnabled = true;
	public boolean singlePlayerMode = false;
	public boolean isDebug = true;
	public boolean onlineMode = false;
	public String logPath = "/sdcard/rukkit-error.log";
	public int maxPacketFrame = 8192;
	public String UUID = "00000000-0000-0000-0000-000000000000";
	public String lang = Locale.getDefault().toString();
	//max threads in manager.Default = 8;
	public int threadPoolCount = 8;
	// max unit in per player 单玩家最大单位
	public int maxUnitsPerPlayer = 250;
	// using question system to vote 投票系统使用提示框模式实现
	// public boolean usingPopupInVote = false;
	
	//Ping packet receive timeout.default = 8000 (ms)
	public int pingTimeout = 8000;
	
	//registerTimeout default = 5 (s)
	public int registerTimeout = 5;
	
	//Using commandQuere to manage game commands
	public boolean useCommandQuere = false;

	public boolean checksumSync = false;

	// Shared data/plugin locations. Relative paths are resolved from the server's working directory.
	public String pluginsPath = "plugins";
	public String mapsPath = "maps";
	public String modsPath = "mods";

	// Multi-server manager.
	public boolean serverManagerEnabled = true;
	public boolean serverManagerOpenConsole = true;
	public String serverManagerRoot = "servers";
	public int serverManagerBasePort = 5200;
	public int serverManagerMaxServers = 50;
	public boolean serverManagerControlEnabled = true;
	public int serverManagerControlPortOffset = 10000;
	public int serverManagerControlPort = 15123;
	public String serverManagerControlToken = java.util.UUID.randomUUID().toString();
	// Help configuration.
	public int helpPageSize = 10;
	public boolean helpShowDisabledCommands = false;
	public boolean helpShowDescriptions = true;

	// Team/spawn rules.
	public int maxTeams = 2;

	public java.util.Map<String, Boolean> playerPermissions = new java.util.HashMap<>();
	public java.util.Map<String, Boolean> adminPermissions = new java.util.HashMap<>();
	public java.util.List<Float> allowedIncomeValues = new java.util.ArrayList<>();
	public java.util.List<Integer> allowedCreditsValues = new java.util.ArrayList<>();

	public java.util.Map<String, String> notifications = new java.util.LinkedHashMap<>();
	public java.util.List<String> helpHiddenCommands = new java.util.ArrayList<>();

	private java.util.Map<String, Boolean> defaultPlayerPermissions() {
		java.util.Map<String, Boolean> m = new java.util.LinkedHashMap<>();
		m.put("help", true);
		m.put("state", true);
		m.put("version", true);
		m.put("t", true);
		m.put("maps", true);
		m.put("map", false);
		m.put("cmaps", true);
		m.put("cmap", false);
		m.put("kick", false);
		m.put("team", true);
		m.put("self_team", true);
		m.put("move", false);
		m.put("self_move", true);
		m.put("qc", true); // required by the client during connection; hidden from help by default
		m.put("fog", false);
		m.put("nukes", false);
		m.put("startingunits", false);
		m.put("income", false);
		m.put("share", true);
		m.put("credits", false);
		m.put("start", false);
		m.put("sync", false);
		m.put("i", true);
		m.put("chksum", true);
		m.put("maping", true);
		m.put("list", true);
		m.put("surrender", true);
		m.put("afk", false);
		m.put("y", true);
		m.put("n", true);
		return m;
	}

	private java.util.Map<String, Boolean> defaultAdminPermissions() {
		java.util.Map<String, Boolean> m = new java.util.LinkedHashMap<>();
		m.putAll(defaultPlayerPermissions());
		m.put("map", true);
		m.put("cmap", true);
		m.put("move", true);
		m.put("self_move", true);
		m.put("fog", true);
		m.put("nukes", true);
		m.put("credits", true);
		m.put("start", true);
		m.put("sync", true);
		m.put("team", true);
		m.put("self_team", true);
		m.put("qc", true);
		// Explicitly disabled for admins, per your server policy.
		m.put("state", false);
		m.put("version", false);
		m.put("cmaps", false);
		m.put("kick", false);
		m.put("startingunits", false);
		m.put("income", false);
		m.put("share", false);
		return m;
	}


	public String notification(String key, String fallback, Object... replacements) {
		String value = notifications.getOrDefault(key, fallback);
		for (int i = 0; i + 1 < replacements.length; i += 2) {
			String placeholder = "{" + String.valueOf(replacements[i]) + "}";
			value = value.replace(placeholder, String.valueOf(replacements[i + 1]));
		}
		return value;
	}

	private void setNotificationDefault(String key, String value) {
		if (!notifications.containsKey(key)) notifications.put(key, value);
	}

	public void applyDefaults() {
		if (playerPermissions == null) playerPermissions = new java.util.HashMap<>();
		if (adminPermissions == null) adminPermissions = new java.util.HashMap<>();
		for (java.util.Map.Entry<String, Boolean> e : defaultPlayerPermissions().entrySet()) {
			playerPermissions.putIfAbsent(e.getKey(), e.getValue());
		}
		for (java.util.Map.Entry<String, Boolean> e : defaultAdminPermissions().entrySet()) {
			adminPermissions.putIfAbsent(e.getKey(), e.getValue());
		}
		if (allowedIncomeValues == null || allowedIncomeValues.isEmpty()) {
			allowedIncomeValues = new java.util.ArrayList<>(java.util.Arrays.asList(1.0f, 2.0f, 2.5f, 3.0f));
		}
		if (allowedCreditsValues == null || allowedCreditsValues.isEmpty()) {
			allowedCreditsValues = new java.util.ArrayList<>(java.util.Arrays.asList(0, 1000, 2000, 5000, 10000, 50000, 100000, 200000));
		}
		if (maxTeams < 1) maxTeams = 2;
		if (helpPageSize < 1) helpPageSize = 10;
		if (serverManagerBasePort < 1 || serverManagerBasePort > 65535) serverManagerBasePort = 5200;
		if (serverManagerMaxServers < 1) serverManagerMaxServers = 50;
		if (serverManagerControlPortOffset < 1) serverManagerControlPortOffset = 10000;
		if (serverManagerControlPort < 1 || serverManagerControlPort > 65535) serverManagerControlPort = serverPort + serverManagerControlPortOffset;
		if (serverManagerControlToken == null || serverManagerControlToken.trim().isEmpty()) serverManagerControlToken = java.util.UUID.randomUUID().toString();
		if (notifications == null) notifications = new LinkedHashMap<>();
		setNotificationDefault("rukkit.playerJoin", "Player {playerName} joined the server!");
		setNotificationDefault("rukkit.playerLeft", "Player {playerName} left the server({reason})!");
		setNotificationDefault("rukkit.playerReconnect", "Player {playerName} reconnected!");
		setNotificationDefault("rukkit.playerSharingControlDueDisconnected", "Player {playerName} shared control because of disconnect.");
		setNotificationDefault("rukkit.gameFull", "Game is full!");
		setNotificationDefault("rukkit.gameStarted", "Started game on server: {serverName}");
		if (helpHiddenCommands == null) helpHiddenCommands = new java.util.ArrayList<>();
		if (!helpHiddenCommands.contains("qc")) helpHiddenCommands.add("qc");
	}

	public RukkitConfig() {
    this.configName = "rukkit.yml";
    applyDefaults();
	}
}
