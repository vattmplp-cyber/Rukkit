/*
 * Copyright 2020-2022 RukkitDev Team and contributors.
 *
 * This project uses GNU Affero General Public License v3.0.You can find this license in the following link.
 * 本项目使用 GNU Affero General Public License v3.0 许可证，你可以在下方链接查看:
 *
 * https://github.com/RukkitDev/Rukkit/blob/master/LICENSE
 */

package cn.rukkit.game;
import cn.rukkit.*;
import cn.rukkit.network.NetworkRoom;

import java.util.Arrays;
//import sun.nio.ch.Net;

public class PlayerManager
{
	private int max;
	private NetworkRoom currentRoom;
	
	/**
	* Init player manager.
	* @params maxPlayer set up maxPlayer
	*/
	public PlayerManager(NetworkRoom room, int maxPlayer) {
		this.max = maxPlayer;
		currentRoom = room;
		reset();
	}
	
	private volatile NetworkPlayer[] players;
	//private static Player[] inGamePlayers = new Player[ServerProperties.maxPlayer];

	/**
	* Add a player into Array.
	*/
	public int add(NetworkPlayer p) {
		for(int i=0;i<players.length;i++){
			if (players[i].isEmpty) {
				p.playerIndex = i;
				players[i] = p;
				return i;
			}
		}
		return p.playerIndex;
	}
	
	/**
	* Add a player with auto team.
	* team changed by index.
	*/
	public void addWithTeam(NetworkPlayer p){
		int maxTeams = Math.max(1, Rukkit.getConfig().maxTeams);
		int bestTeam = 0;
		int bestCount = Integer.MAX_VALUE;
		int bestFree = -1;

		// Auto-assign to the least populated team, while always choosing
		// a real free spawn belonging to that team. For maxTeams=2 this is
		// A: slots 1,3,5... and B: slots 2,4,6...
		for (int team = 0; team < maxTeams; team++) {
			int count = 0;
			int free = 0;
			for (int i = team; i < players.length; i += maxTeams) {
				if (players[i].isEmpty) {
					free++;
				} else {
					count++;
				}
			}
			if (free > 0 && (count < bestCount || (count == bestCount && free > bestFree))) {
				bestTeam = team;
				bestCount = count;
				bestFree = free;
			}
		}

		if (movePlayerToTeam(p, bestTeam)) {
			return;
		}

		// Last-resort fallback; should only happen if the manager is full.
		add(p);
		p.team = p.playerIndex % maxTeams;
	}

	/**
	 * Returns the first free spawn belonging to the requested team.
	 * With maxTeams=2, team 0 uses player slots 1,3,5,... and team 1 uses
	 * player slots 2,4,6,... (one-based slot numbers).
	 */
	public int findFreeSpawnForTeam(int team) {
		int maxTeams = Math.max(1, Rukkit.getConfig().maxTeams);
		if (team < 0 || team >= maxTeams) return -1;
		for (int i = team; i < players.length; i += maxTeams) {
			if (players[i].isEmpty) return i;
		}
		return -1;
	}

	/**
	 * Moves a player to any free spawn in the requested team. The exact spawn
	 * number requested by the client is intentionally ignored when this method
	 * is used; the server picks any free spawn for that team.
	 */
	public boolean movePlayerToTeam(NetworkPlayer p, int team) {
		int maxTeams = Math.max(1, Rukkit.getConfig().maxTeams);
		if (p == null || team < 0 || team >= maxTeams) return false;

		int current = p.playerIndex;
		if (current >= 0 && current < players.length && current % maxTeams == team) {
			p.team = team;
			players[current] = p;
			return true;
		}

		int target = findFreeSpawnForTeam(team);
		if (target < 0) return false;

		if (current >= 0 && current < players.length && players[current] == p) {
			NetworkPlayer empty = new NetworkPlayer();
			empty.playerIndex = current;
			empty.team = current % maxTeams;
			players[current] = empty;
		}

		p.playerIndex = target;
		p.team = team;
		players[target] = p;
		return true;
	}

	/**
	 * Add a player with auto-team when no-stop mode.
	 *
	 */
	public void addWithTeamNoStop() {}

	/**
	* Remove a player.
	*/
	public void remove(NetworkPlayer p){
		int index = getIndex(p);
		remove(index);
	}

	/**
	* Remove player by index.
	*/
	public void remove(int index){
//		if(Rukkit.getConfig().nonStopMode) {
//			players[index] = new NetworkPlayer();
//			return;
//		}
		if(currentRoom.isGaming()){
			players[index].ping = -1;
			players[index].isDisconnected = true;
			return;
		}
		players[index] = new NetworkPlayer();
	}
	
	/**
	* Get player by index.
	*/
	public NetworkPlayer get(int index){
		if (index > players.length - 1) return null;
		return players[index];
	}

	public NetworkPlayer getPlayerByUUID(String uuid) {
		for (NetworkPlayer p: players) {
			if (p.uuid.equals(uuid)) {
				return p;
			}
		}
		return null;
	}
	
	/**
	* get a player index.
	*/
	public int getIndex(NetworkPlayer p){
		for(int i=0;i<players.length;i++){
			if(players[i] == p) {
				return i;
			}
		}
		return -1;
	}
	
	/**
	* get admin player.
	*/
	public NetworkPlayer getAdmin(){
		for (NetworkPlayer p: players) {
			if (p.isAdmin && !p.isEmpty) {
				return p;
			}
		}
		return null;
	}
	
	/**
	* get player amount.
	*/
	public int getPlayerCount(){
		int size = 0;
		for (NetworkPlayer p: players) {
			if (!p.isEmpty) {
				size++;
			}
		}
		return size;
	}

	// ai方法
	public void addAI() {
		NetworkPlayer p = new NetworkPlayer();
		p.isEmpty = false;
		p.isAI = true;
		p.name = "AI - Idiot";
		p.ping = -1;
		add(p);
	}

	public void removeAI() {

	}
	
	/**
	* returns a player array INCLUDING null.
	* @return @nullable NetworkPlayer[] array
	*/
	public NetworkPlayer[] getPlayerArray(){
		return players;
	}
	
	/**
	* set player in a index
	*/
	public void set(int index, NetworkPlayer p){
		players[index] =  p;
	}
	
	/**
	* reset array.useful for reseting a game.
	*/
	public void reset(){
		players = new NetworkPlayer[max];
		for (int i = 0;i < players.length;i++) {
			NetworkPlayer emptyPlayer = new NetworkPlayer();
			emptyPlayer.playerIndex = i;
			if (i % 2 == 1) emptyPlayer.team = 1;
			players[i] = emptyPlayer;
		}
	}

	public void clearDisconnectedPlayers() {
		for (int i=0;i<players.length;i++) {
			if (!players[i].isEmpty) {
				if (players[i].ping == -1) {
					NetworkPlayer emptyPlayer = new NetworkPlayer();
					emptyPlayer.playerIndex = i;
					if (i % 2 == 1) emptyPlayer.team = 1;
					players[i] = emptyPlayer;
				}
			}
		}
	}

	public int getMaxPlayer() {
		return max;
	}
}
