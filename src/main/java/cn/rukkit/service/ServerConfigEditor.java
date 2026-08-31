package cn.rukkit.service;

import cn.rukkit.config.RoundConfig;
import cn.rukkit.config.RukkitConfig;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Editor for child-server configuration files.
 *
 * rukkit.yml -> Rukkit/server settings
 * uplist_config.properties -> master-list publication fields
 * round.yml -> default/new-game round settings
 *
 * Each file is edited independently.
 */
public final class ServerConfigEditor {
    private ServerConfigEditor() {}

    public static String edit(File configFile, String key, String rawValue) throws Exception {
        if (configFile == null || !configFile.isFile()) throw new IllegalArgumentException("Config not found");
        if (key == null || key.trim().isEmpty()) throw new IllegalArgumentException("Missing setting name");
        if (rawValue == null) throw new IllegalArgumentException("Missing setting value");

        String normalized = normalizeKey(key);
        String value = stripQuotes(rawValue.trim());

        // Uplist fields belong only to uplist_config.properties.
        if (isUplistKey(normalized)) {
            return editUplist(configFile.getParentFile(), normalized, value);
        }

        // Round/game defaults belong only to round.yml.
        if (isRoundKey(normalized)) {
            return editRound(configFile.getParentFile(), normalized, value);
        }

        // Everything else in this editor is a Rukkit setting.
        Yaml yaml = new Yaml();
        RukkitConfig cfg;
        try (FileReader reader = new FileReader(configFile)) { cfg = yaml.loadAs(reader, RukkitConfig.class); }
        if (cfg == null) throw new IllegalArgumentException("Cannot read rukkit.yml");

        switch (normalized) {
            case "serveruser": case "rukkitname": requireText(value); cfg.serverUser = value; break;
            case "motd": case "servermotd": cfg.serverMotd = value; break;
            case "welcomemsg": cfg.welcomeMsg = value; break;
            case "maxplayer": cfg.maxPlayer = positiveInt(value, "maxPlayer"); break;
            case "maxroom": cfg.maxRoom = positiveInt(value, "maxRoom"); break;
            case "minstartplayer": case "startminplayers": cfg.minStartPlayer = nonNegativeInt(value, "minStartPlayer"); break;
            case "officialmapfilterenabled": case "mapfilter": cfg.officialMapFilterEnabled = bool(value, "officialMapFilterEnabled"); break;
            case "officialmapminplayers": case "mapminplayers": cfg.officialMapMinPlayers = nonNegativeInt(value, "officialMapMinPlayers"); break;
            case "officialmapmaxplayers": case "mapmaxplayers": cfg.officialMapMaxPlayers = nonNegativeInt(value, "officialMapMaxPlayers"); break;
            case "gamestartcountdownenabled": cfg.gameStartCountdownEnabled = bool(value, "gameStartCountdownEnabled"); break;
            case "gamestartcountdownseconds": cfg.gameStartCountdownSeconds = nonNegativeInt(value, "gameStartCountdownSeconds"); break;
            case "afkenabled": cfg.afkEnabled = bool(value, "afkEnabled"); break;
            case "afkcountdownseconds": cfg.afkCountdownSeconds = nonNegativeInt(value, "afkCountdownSeconds"); break;
            case "afkwarningintervalseconds": cfg.afkWarningIntervalSeconds = nonNegativeInt(value, "afkWarningIntervalSeconds"); break;
            case "afkfinalwarningseconds": cfg.afkFinalWarningSeconds = nonNegativeInt(value, "afkFinalWarningSeconds"); break;
            case "afkcancelonadminchat": cfg.afkCancelOnAdminChat = bool(value, "afkCancelOnAdminChat"); break;
            case "afkcancelonadmincommand": cfg.afkCancelOnAdminCommand = bool(value, "afkCancelOnAdminCommand"); break;
            case "afktransfercontrol": cfg.afkTransferControl = bool(value, "afkTransferControl"); break;
            case "maxunitsperplayer": cfg.maxUnitsPerPlayer = positiveInt(value, "maxUnitsPerPlayer"); break;
            case "pingtimeout": cfg.pingTimeout = positiveInt(value, "pingTimeout"); break;
            case "maxpacketframe": cfg.maxPacketFrame = positiveInt(value, "maxPacketFrame"); break;
            case "syncenabled": cfg.syncEnabled = bool(value, "syncEnabled"); break;
            case "checksumsync": cfg.checksumSync = bool(value, "checksumSync"); break;
            case "onlinemode": cfg.onlineMode = bool(value, "onlineMode"); break;
            case "singleplayermode": cfg.singlePlayerMode = bool(value, "singlePlayerMode"); break;
            case "isdebug": cfg.isDebug = bool(value, "isDebug"); break;
            case "helppagesize": cfg.helpPageSize = positiveInt(value, "helpPageSize"); break;
            default: throw new IllegalArgumentException("Unsupported setting: " + key + ". Use 'server edit help'.");
        }

        if (cfg.officialMapMinPlayers < 0) cfg.officialMapMinPlayers = 0;
        if (cfg.officialMapMaxPlayers < cfg.officialMapMinPlayers) cfg.officialMapMaxPlayers = cfg.officialMapMinPlayers;

        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write(yaml.dumpAs(cfg, Tag.MAP, DumperOptions.FlowStyle.BLOCK));
        }
        return "rukkit " + key + " = " + value;
    }

    private static boolean isUplistKey(String key) {
        switch (key) {
            case "name": case "servername": case "uplistname": case "gamename":
            case "map": case "mapname": case "uplistmap": case "gamemap":
            case "host": case "hostname": case "createdby": case "uplisthost":
            case "maxplayercount": case "uplistmaxplayercount":
            case "portnumber": case "uplistport":
                return true;
            default:
                return false;
        }
    }

    private static boolean isRoundKey(String key) {
        switch (key) {
            case "roundmap": case "roundmapname": case "mapname":
            case "roundmaptype": case "maptype":
            case "roundincome": case "income":
            case "roundcredits": case "credits":
            case "rounddisablenuke": case "disablenuke":
            case "roundsharedcontrol": case "sharedcontrol":
            case "roundfogtype": case "fogtype":
            case "roundstartingunits": case "startingunits":
                return true;
            default:
                return false;
        }
    }

    private static String editUplist(File serverDir, String key, String value) throws Exception {
        if (serverDir == null) throw new IllegalArgumentException("Server directory not found");
        requireText(value);

        File file = new File(serverDir, "uplist_config.properties");
        Properties p = loadProperties(file);

        String property;
        switch (key) {
            case "name": case "servername": case "uplistname": case "gamename":
            case "map": case "mapname": case "uplistmap": case "gamemap":
                // For this setup the field displayed by the master list as the
                // server name is game_map. Keep game_name independent.
                property = "game_map";
                break;
            case "host": case "hostname": case "createdby": case "uplisthost":
                property = "created_by";
                break;
            case "maxplayercount": case "uplistmaxplayercount":
                property = "max_player_count";
                break;
            case "portnumber": case "uplistport":
                property = "port_number";
                break;
            default:
                throw new IllegalArgumentException("Unsupported Uplist setting: " + key);
        }

        if ("max_player_count".equals(property) || "port_number".equals(property)) {
            value = Integer.toString(positiveInt(value, property));
        }

        p.setProperty(property, value);
        saveProperties(file, p, "Rusted Warfare 1.15 Master Server Configuration");
        return "uplist " + property + " = " + value;
    }

    private static String editRound(File serverDir, String key, String value) throws Exception {
        if (serverDir == null) throw new IllegalArgumentException("Server directory not found");
        requireText(value);

        File file = new File(serverDir, "round.yml");
        Yaml yaml = new Yaml();
        RoundConfig cfg;

        if (file.isFile()) {
            try (FileReader reader = new FileReader(file)) {
                cfg = yaml.loadAs(reader, RoundConfig.class);
            }
        } else {
            cfg = new RoundConfig();
        }
        if (cfg == null) cfg = new RoundConfig();

        switch (key) {
            case "roundmap": case "roundmapname": case "mapname":
                cfg.mapName = value; break;
            case "roundmaptype": case "maptype":
                cfg.mapType = nonNegativeInt(value, "mapType"); break;
            case "roundincome": case "income":
                cfg.income = nonNegativeFloat(value, "income"); break;
            case "roundcredits": case "credits":
                cfg.credits = nonNegativeInt(value, "credits"); break;
            case "rounddisablenuke": case "disablenuke":
                cfg.disableNuke = bool(value, "disableNuke"); break;
            case "roundsharedcontrol": case "sharedcontrol":
                cfg.sharedControl = bool(value, "sharedControl"); break;
            case "roundfogtype": case "fogtype":
                cfg.fogType = nonNegativeInt(value, "fogType"); break;
            case "roundstartingunits": case "startingunits":
                cfg.startingUnits = nonNegativeInt(value, "startingUnits"); break;
            default:
                throw new IllegalArgumentException("Unsupported round setting: " + key + ". Use 'server edit help'.");
        }

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(yaml.dumpAs(cfg, Tag.MAP, DumperOptions.FlowStyle.BLOCK));
        }
        return "round " + key + " = " + value;
    }

    private static Properties loadProperties(File file) throws IOException {
        Properties p = new Properties();
        if (file.isFile()) {
            try (InputStream in = new FileInputStream(file)) { p.load(in); }
        }
        return p;
    }

    private static void saveProperties(File file, Properties p, String header) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) { p.store(out, header); }
    }

    public static String editMany(File configFile, List<String> assignments) throws Exception {
        if (assignments == null || assignments.isEmpty()) throw new IllegalArgumentException("No settings supplied");
        List<String> changed = new ArrayList<>();
        for (String assignment : assignments) {
            int equals = assignment.indexOf('=');
            if (equals <= 0) throw new IllegalArgumentException("Expected key=value: " + assignment);
            changed.add(edit(configFile, assignment.substring(0, equals).trim(), assignment.substring(equals + 1).trim()));
        }
        return String.join(", ", changed);
    }

    public static String describe(File configFile) throws Exception {
        Yaml yaml = new Yaml();
        RukkitConfig c;
        try (FileReader reader = new FileReader(configFile)) { c = yaml.loadAs(reader, RukkitConfig.class); }
        if (c == null) throw new IllegalArgumentException("Cannot read rukkit.yml");

        Properties p = new Properties();
        File uplist = new File(configFile.getParentFile(), "uplist_config.properties");
        if (uplist.isFile()) {
            try (InputStream in = new FileInputStream(uplist)) { p.load(in); }
        }

        File roundFile = new File(configFile.getParentFile(), "round.yml");
        RoundConfig r = roundFile.isFile() ? yaml.loadAs(new FileReader(roundFile), RoundConfig.class) : new RoundConfig();
        if (r == null) r = new RoundConfig();

        return "Uplist name=" + p.getProperty("game_map", "<unset>") +
                " | host=" + p.getProperty("created_by", "<unset>") +
                " | uplistGameName=" + p.getProperty("game_name", "<unset>") +
                " | uplistMap=" + p.getProperty("game_map", "<unset>") +
                " | maxPlayers=" + p.getProperty("max_player_count", "<unset>") +
                " | Round map=" + r.mapName +
                " | income=" + r.income +
                " | disableNuke=" + r.disableNuke +
                " | Rukkit filter=" + c.officialMapFilterEnabled +
                " | mapPlayers=" + c.officialMapMinPlayers + "-" + c.officialMapMaxPlayers +
                " | startMin=" + c.minStartPlayer +
                " | Rukkit maxPlayer=" + c.maxPlayer +
                " | maxRoom=" + c.maxRoom +
                " | AFK=" + c.afkEnabled;
    }

    public static String help() {
        return "Server edit:\n" +
            "  server edit <target> [--stop] key=value [key=value ...]\n" +
            "  server edit <target> --show\n" +
            "  server edit help\n\n" +
            "Rukkit settings (rukkit.yml):\n" +
            "  serverUser, serverMotd, welcomeMsg, maxPlayer, maxRoom, minStartPlayer\n" +
            "  officialMapFilterEnabled, officialMapMinPlayers, officialMapMaxPlayers\n" +
            "  gameStartCountdownEnabled, gameStartCountdownSeconds\n" +
            "  afkEnabled, afkCountdownSeconds, afkWarningIntervalSeconds, afkFinalWarningSeconds\n" +
            "  afkCancelOnAdminChat, afkCancelOnAdminCommand, afkTransferControl\n" +
            "  maxUnitsPerPlayer, syncEnabled, checksumSync, onlineMode, singlePlayerMode, isDebug\n\n" +
            "Uplist settings (uplist_config.properties ONLY):\n" +
            "  name -> game_map (public server name in master list)\n" +
            "  host/created_by -> created_by\n" +
            "  max_player_count -> max_player_count\n" +
            "  port_number -> port_number\n\n" +
            "Round settings (round.yml ONLY):\n" +
            "  roundMapName/mapName, mapType, income, credits\n" +
            "  disableNuke, sharedControl, fogType, startingUnits\n\n" +
            "Examples:\n" +
            "  server edit 11 name=\"DUELS 1 VS 1 [CA-C #1]\" host=SERVER\n" +
            "  server edit 11 mapName=\"[p2]Dire Straight (2p)\" income=1 disableNuke=false\n" +
            "  server edit 11-26 mapName=\"[p2]Dire Straight (2p)\" income=1 disableNuke=false";
    }

    private static String normalizeKey(String key) { return key.trim().replace("-", "").replace("_", "").toLowerCase(Locale.ROOT); }
    private static String stripQuotes(String value) { if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) return value.substring(1, value.length() - 1); return value; }
    private static void requireText(String value) { if (value.isEmpty()) throw new IllegalArgumentException("Text value cannot be empty"); }
    private static boolean bool(String value, String key) { if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) throw new IllegalArgumentException(key + " must be true or false"); return Boolean.parseBoolean(value); }
    private static int positiveInt(String value, String key) { int n = nonNegativeInt(value, key); if (n < 1) throw new IllegalArgumentException(key + " must be >= 1"); return n; }
    private static int nonNegativeInt(String value, String key) { try { int n = Integer.parseInt(value); if (n < 0) throw new IllegalArgumentException(key + " must be >= 0"); return n; } catch (NumberFormatException e) { throw new IllegalArgumentException(key + " must be an integer"); } }
    private static float nonNegativeFloat(String value, String key) { try { float n = Float.parseFloat(value); if (Float.isNaN(n) || Float.isInfinite(n) || n < 0f) throw new IllegalArgumentException(key + " must be >= 0"); return n; } catch (NumberFormatException e) { throw new IllegalArgumentException(key + " must be a number"); } }
}
