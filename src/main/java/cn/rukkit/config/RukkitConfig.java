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

public class RukkitConfig extends BaseConfig
{
	public String serverUser = "RUKKIT";
	public String welcomeMsg = "Welcome to Rukkit server, {playerName}!";
	public String serverMotd = "My Rukkit server";
	public int serverPort = 5123;
	public int maxPlayer = 10;
	public int maxRoom = 5;
	public int minStartPlayer = 4;
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

    public java.util.Map<String, Boolean> playerPermissions = new java.util.HashMap<>();
	public java.util.Map<String, Boolean> adminPermissions = new java.util.HashMap<>();

	private void initDefaultPermissions() {
    playerPermissions.clear();
    adminPermissions.clear();

    playerPermissions.put("help", true);
    playerPermissions.put("state", true);
    playerPermissions.put("version", true);
    playerPermissions.put("t", true);
    playerPermissions.put("maps", true);
    playerPermissions.put("map", false);
    playerPermissions.put("cmaps", true);
    playerPermissions.put("cmap", false);
    playerPermissions.put("kick", false);
    playerPermissions.put("team", true);
    playerPermissions.put("self_team", true);
    playerPermissions.put("move", false);
    playerPermissions.put("self_move", true);
    playerPermissions.put("qc", false);
    playerPermissions.put("fog", false);
    playerPermissions.put("nukes", false);
    playerPermissions.put("startingunits", false);
    playerPermissions.put("income", false);
    playerPermissions.put("share", true);
    playerPermissions.put("credits", false);
    playerPermissions.put("start", false);
    playerPermissions.put("sync", false);
    playerPermissions.put("i", true);
    playerPermissions.put("chksum", true);
    playerPermissions.put("maping", true);
    playerPermissions.put("list", true);
    playerPermissions.put("surrender", true);
    playerPermissions.put("afk", false);
    playerPermissions.put("y", true);
    playerPermissions.put("n", true);

    adminPermissions.putAll(playerPermissions);

    adminPermissions.put("map", true);
    adminPermissions.put("cmap", true);
    adminPermissions.put("move", true);
    adminPermissions.put("fog", true);
    adminPermissions.put("nukes", true);
    adminPermissions.put("credits", true);
    adminPermissions.put("start", true);
    adminPermissions.put("sync", true);

    adminPermissions.put("state", false);
    adminPermissions.put("version", false);
    adminPermissions.put("cmaps", false);
    adminPermissions.put("kick", false);
    adminPermissions.put("qc", false);
    adminPermissions.put("startingunits", false);
    adminPermissions.put("income", false);
    adminPermissions.put("share", false);
}
	
	public RukkitConfig() {
    this.configName = "rukkit.yml";
    initDefaultPermissions();
	}
}
