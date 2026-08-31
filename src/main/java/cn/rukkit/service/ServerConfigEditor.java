package cn.rukkit.service;

import cn.rukkit.config.RukkitConfig;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Safe editor for child-server rukkit.yml files.
 * Only explicitly supported settings can be changed from the server-manager console.
 */
public final class ServerConfigEditor {
    private ServerConfigEditor() {}

    public static String edit(File configFile, String key, String rawValue) throws Exception {
        if (configFile == null || !configFile.isFile()) throw new IllegalArgumentException("Config not found");
        if (key == null || key.trim().isEmpty()) throw new IllegalArgumentException("Missing setting name");
        if (rawValue == null) throw new IllegalArgumentException("Missing setting value");

        Yaml yaml = new Yaml();
        RukkitConfig cfg;
        try (FileReader reader = new FileReader(configFile)) {
            cfg = yaml.loadAs(reader, RukkitConfig.class);
        }
        if (cfg == null) throw new IllegalArgumentException("Cannot read rukkit.yml");

        String normalized = normalizeKey(key);
        String value = stripQuotes(rawValue.trim());

        switch (normalized) {
            case "name":
            case "serveruser":
                requireText(value);
                cfg.serverUser = value;
                break;

            case "motd":
            case "servermotd":
                cfg.serverMotd = value;
                break;

            case "welcomemsg":
                cfg.welcomeMsg = value;
                break;

            case "maxplayer":
                cfg.maxPlayer = positiveInt(value, "maxPlayer");
                break;

            case "maxroom":
                cfg.maxRoom = positiveInt(value, "maxRoom");
                break;

            case "minstartplayer":
            case "startminplayers":
                cfg.minStartPlayer = nonNegativeInt(value, "minStartPlayer");
                break;

            case "officialmapfilterenabled":
            case "mapfilter":
                cfg.officialMapFilterEnabled = bool(value, "officialMapFilterEnabled");
                break;

            case "officialmapminplayers":
            case "mapminplayers":
                cfg.officialMapMinPlayers = nonNegativeInt(value, "officialMapMinPlayers");
                break;

            case "officialmapmaxplayers":
            case "mapmaxplayers":
                cfg.officialMapMaxPlayers = nonNegativeInt(value, "officialMapMaxPlayers");
                break;

            case "gamestartcountdownenabled":
                cfg.gameStartCountdownEnabled = bool(value, "gameStartCountdownEnabled");
                break;

            case "gamestartcountdownseconds":
                cfg.gameStartCountdownSeconds = nonNegativeInt(value, "gameStartCountdownSeconds");
                break;

            case "afkenabled":
                cfg.afkEnabled = bool(value, "afkEnabled");
                break;

            case "afkcountdownseconds":
                cfg.afkCountdownSeconds = nonNegativeInt(value, "afkCountdownSeconds");
                break;

            case "afkwarningintervalseconds":
                cfg.afkWarningIntervalSeconds = nonNegativeInt(value, "afkWarningIntervalSeconds");
                break;

            case "afkfinalwarningseconds":
                cfg.afkFinalWarningSeconds = nonNegativeInt(value, "afkFinalWarningSeconds");
                break;

            case "afkcancelonadminchat":
                cfg.afkCancelOnAdminChat = bool(value, "afkCancelOnAdminChat");
                break;

            case "afkcancelonadmincommand":
                cfg.afkCancelOnAdminCommand = bool(value, "afkCancelOnAdminCommand");
                break;

            case "afktransfercontrol":
                cfg.afkTransferControl = bool(value, "afkTransferControl");
                break;

            case "maxunitsperplayer":
                cfg.maxUnitsPerPlayer = positiveInt(value, "maxUnitsPerPlayer");
                break;

            case "pingtimeout":
                cfg.pingTimeout = positiveInt(value, "pingTimeout");
                break;

            case "maxpacketframe":
                cfg.maxPacketFrame = positiveInt(value, "maxPacketFrame");
                break;

            case "syncenabled":
                cfg.syncEnabled = bool(value, "syncEnabled");
                break;

            case "checksumSync":
            case "checksumsync":
                cfg.checksumSync = bool(value, "checksumSync");
                break;

            case "onlinemode":
                cfg.onlineMode = bool(value, "onlineMode");
                break;

            case "singleplayermode":
                cfg.singlePlayerMode = bool(value, "singlePlayerMode");
                break;

            case "isdebug":
                cfg.isDebug = bool(value, "isDebug");
                break;

            case "helpPageSize":
            case "helppagesize":
                cfg.helpPageSize = positiveInt(value, "helpPageSize");
                break;

            default:
                throw new IllegalArgumentException("Unsupported setting: " + key + ". Use 'server edit help'.");
        }

        // Keep the map filter internally sane after every edit.
        if (cfg.officialMapMinPlayers < 0) cfg.officialMapMinPlayers = 0;
        if (cfg.officialMapMaxPlayers < cfg.officialMapMinPlayers) {
            cfg.officialMapMaxPlayers = cfg.officialMapMinPlayers;
        }

        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write(yaml.dumpAs(cfg, Tag.MAP, DumperOptions.FlowStyle.BLOCK));
        }

        return key + " = " + value;
    }

    public static String editMany(File configFile, List<String> assignments) throws Exception {
        if (assignments == null || assignments.isEmpty()) throw new IllegalArgumentException("No settings supplied");
        List<String> changed = new ArrayList<>();
        for (String assignment : assignments) {
            int equals = assignment.indexOf('=');
            if (equals <= 0) throw new IllegalArgumentException("Expected key=value: " + assignment);
            String key = assignment.substring(0, equals).trim();
            String value = assignment.substring(equals + 1).trim();
            changed.add(edit(configFile, key, value));
        }
        return String.join(", ", changed);
    }

    public static String help() {
        return "Server edit settings:\n" +
            "  name=<text> / serverUser=<text>\n" +
            "  serverMotd=<text>\n" +
            "  welcomeMsg=<text>\n" +
            "  maxPlayer=<n>\n" +
            "  maxRoom=<n>\n" +
            "  minStartPlayer=<n>\n" +
            "  officialMapFilterEnabled=<true|false>\n" +
            "  officialMapMinPlayers=<n>\n" +
            "  officialMapMaxPlayers=<n>\n" +
            "  gameStartCountdownEnabled=<true|false>\n" +
            "  gameStartCountdownSeconds=<n>\n" +
            "  afkEnabled=<true|false>\n" +
            "  afkCountdownSeconds=<n>\n" +
            "  afkWarningIntervalSeconds=<n>\n" +
            "  afkFinalWarningSeconds=<n>\n" +
            "  afkCancelOnAdminChat=<true|false>\n" +
            "  afkCancelOnAdminCommand=<true|false>\n" +
            "  afkTransferControl=<true|false>\n" +
            "  maxUnitsPerPlayer=<n>\n" +
            "  syncEnabled=<true|false>\n" +
            "  checksumSync=<true|false>\n" +
            "  onlineMode=<true|false>\n" +
            "  singlePlayerMode=<true|false>\n" +
            "  isDebug=<true|false>\n" +
            "\nExample:\n" +
            "  server edit 1-5 officialMapFilterEnabled=true officialMapMinPlayers=4 officialMapMaxPlayers=10 minStartPlayer=4\n" +
            "  server edit server2 name=\"Canada #2\" serverMotd=\"Welcome\"";
    }

    private static String normalizeKey(String key) {
        return key.trim().replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static void requireText(String value) {
        if (value.isEmpty()) throw new IllegalArgumentException("Text value cannot be empty");
    }

    private static boolean bool(String value, String key) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalArgumentException(key + " must be true or false");
        }
        return Boolean.parseBoolean(value);
    }

    private static int positiveInt(String value, String key) {
        int n = nonNegativeInt(value, key);
        if (n < 1) throw new IllegalArgumentException(key + " must be >= 1");
        return n;
    }

    private static int nonNegativeInt(String value, String key) {
        try {
            int n = Integer.parseInt(value);
            if (n < 0) throw new IllegalArgumentException(key + " must be >= 0");
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
    }
}
