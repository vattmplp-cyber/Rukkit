/*
 * Copyright 2020-2022 RukkitDev Team and contributors.
 *
 * This project uses GNU Affero General Public License v3.0.You can find the license at:
 * https://github.com/RukkitDev/Rukkit/blob/master/LICENSE
 */

package cn.rukkit.command;

import java.util.*;

import cn.rukkit.util.LangUtil;
import org.slf4j.*;
import cn.rukkit.network.*;
import cn.rukkit.*;
import cn.rukkit.network.packet.*;
import cn.rukkit.service.ServerConfigManagerCommand;
import java.io.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class CommandManager 
{
    private Logger log = LoggerFactory.getLogger(CommandManager.class);
    private HashMap<String, ChatCommand> loadedCommand = new HashMap<String, ChatCommand>();
    private HashMap<String, ServerCommand> loadedServerCommand = new HashMap<String, ServerCommand>();

    private List<String> serverCmdString = new ArrayList<>();
    private final List<Consumer<RoomConnection>> adminActivityListeners = new CopyOnWriteArrayList<>();
    
    public void registerCommand(ChatCommand cmd) {
        log.debug(String.format("Registering Command '%s' from plugin '%s'...",cmd.cmd,cmd.getFromPlugin().config.name));
        if (fetchCommand(cmd.cmd) != null) {
            log.warn(String.format("Command '%s' had already registered.",cmd.cmd));
        } else {
            loadedCommand.put(cmd.cmd, cmd);
        }
    }

    public void registerServerCommand(ServerCommand cmd) {
        log.debug(String.format("Registering ServerCommand '%s' from plugin '%s'...",cmd.cmd,cmd.getFromPlugin().config.name));
        if (fetchServerCommand(cmd.cmd) != null) {
            log.warn(String.format("ServerCommand '%s' had already registered.",cmd.cmd));
        } else {
            loadedServerCommand.put(cmd.cmd, cmd);
            serverCmdString.add(cmd.cmd);
        }
    }

    /** Register a listener that is notified when an admin performs a command/action. */
    public void registerAdminCommandActivityListener(Consumer<RoomConnection> listener) {
        if (listener != null && !adminActivityListeners.contains(listener)) {
            adminActivityListeners.add(listener);
        }
    }

    /** Notify listeners about an administrator action. Safe to call from packet handlers. */
    public void notifyAdminActivity(RoomConnection connection) {
        if (connection == null || connection.player == null || !connection.player.isAdmin) return;
        for (Consumer<RoomConnection> listener : adminActivityListeners) {
            try {
                listener.accept(connection);
            } catch (RuntimeException e) {
                log.warn("Admin activity listener failed", e);
            }
        }
    }

    public void executeChatCommand(RoomConnection connection, String cmd) {
        String[] cmds = cmd.split("\\s+", 2);
        ChatCommand cmdObj = fetchCommand(cmds[0]);
        if (cmdObj == null) {
            connection.sendServerMessage(LangUtil.getString("chat.invalidCommand"));
            return;
        }

        if (connection.player == null) {
            return;
        }

        boolean isAdmin = connection.player.isAdmin;
        Boolean allowed = isAdmin
                ? Rukkit.getConfig().adminPermissions.get(cmdObj.cmd)
                : Rukkit.getConfig().playerPermissions.get(cmdObj.cmd);

        if (allowed == null) {
            allowed = cmdObj.adminRequired ? isAdmin : true;
        }

        if (!allowed) {
            log.debug("Permission denied: command={}, player={}, admin={}",
                    cmdObj.cmd, connection.player.name, isAdmin);
            connection.sendServerMessage(LangUtil.getString("chat.privDenied"));
            return;
        }

        if (isAdmin && !"afk".equalsIgnoreCase(cmdObj.cmd)) {
            notifyAdminActivity(connection);
        }

        boolean result;
        log.trace("cmd is:" + cmds[0]);
        if (cmds.length > 1 && cmdObj.args > 0) {
            String[] args = cmds[1].split(" ", cmdObj.args);
            result = cmdObj.getListener().onSend(connection,args);
        } else {
            result = cmdObj.getListener().onSend(connection,new String[0]);
        }
        if (result == true) {
            try {
                connection.currectRoom.broadcast(
                    Packet.chat(connection.player.name,
                            "-" + cmd,
                            connection.player.playerIndex));
            } catch (IOException e) {}
        }
    }

    public void executeServerCommand(String cmd) {
        String trimmed = cmd == null ? "" : cmd.trim();
        if (trimmed.isEmpty()) return;

        String[] cmds = trimmed.split("\\s+", 2);

        // "server" is a nested command. The edit action is handled separately
        // because it accepts variable-length key=value assignments and quoted values.
        if (cmds.length > 1 && "server".equalsIgnoreCase(cmds[0])) {
            String[] serverArgs = cmds[1].trim().split("\\s+");
            if (serverArgs.length > 0 && "edit".equalsIgnoreCase(serverArgs[0])) {
                ServerConfigManagerCommand.execute(serverArgs);
                return;
            }
        }

        ServerCommand cmdObj = fetchServerCommand(cmds[0]);
        if (cmdObj == null) {
            System.out.println("Command not exist.Try 'help' to list all commands.");
            return;
        }
        log.trace("cmd is:" + cmds[0]);
        if (cmds.length > 1) {
            String[] args;
            if (cmdObj.args > 0) {
                args = cmds[1].trim().split("\\s+", cmdObj.args);
            } else {
                args = cmds[1].trim().split("\\s+");
            }
            cmdObj.getListener().onSend(args);
        } else {
            cmdObj.getListener().onSend(new String[0]);
        }
    }

    public ChatCommand fetchCommand(String cmd){
        return loadedCommand.getOrDefault(cmd, null);
    }

    public ServerCommand fetchServerCommand(String cmd) {
        return loadedServerCommand.getOrDefault(cmd, null);
    }

    public HashMap<String, ChatCommand> getLoadedCommand() {
        return loadedCommand;
    }

    public HashMap<String, ServerCommand> getLoadedServerCommand() {
        return loadedServerCommand;
    }

    public List<String> getLoadedServerCommandStringList() {
        return serverCmdString;
    }
}
