package cn.rukkit.service;

import cn.rukkit.Rukkit;
import cn.rukkit.config.RukkitConfig;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages independent child Rukkit instances. Child instances share the same
 * Rukkit jar/plugins/maps/mods, while keeping their own config, port and UUID.
 * A loopback control socket lets the parent manage child consoles and send
 * commands to one, many, ranges, or all servers.
 */
public final class ServerInstanceManager {
    private final Map<String, Process> launchers = new ConcurrentHashMap<>();
    private volatile String selectedTarget = "all";

    public String create(String name, Integer requestedPort) throws IOException {
        String safeName = sanitize(name);
        File root = root();
        if (!root.exists() && !root.mkdirs()) throw new IOException("Cannot create root: " + root);
        File dir = new File(root, safeName);
        if (dir.exists()) throw new IOException("Server already exists: " + safeName);
        if (countServers(root) >= Rukkit.getConfig().serverManagerMaxServers) {
            throw new IOException("Server manager limit reached: " + Rukkit.getConfig().serverManagerMaxServers);
        }
        if (!dir.mkdirs()) throw new IOException("Cannot create: " + dir);

        int port = requestedPort != null ? requestedPort : choosePort(root, Rukkit.getConfig().serverManagerBasePort);
        RukkitConfig cfg = copyCurrentConfig(port);
        cfg.serverUser = Rukkit.getConfig().serverUser + " [" + safeName + "]";
        cfg.serverPort = port;
        cfg.logPath = new File(dir, "rukkit-error.log").getAbsolutePath();
        cfg.serverManagerEnabled = false;
        cfg.serverManagerControlEnabled = true;
        cfg.serverManagerRoot = "servers";
        cfg.serverManagerControlPort = controlPortFor(port);
        cfg.serverManagerControlToken = UUID.randomUUID().toString();
        cfg.configName = "rukkit.yml";
        cfg.UUID = UUID.randomUUID().toString();
        cfg.pluginsPath = sharedPath(dir, Rukkit.getConfig().pluginsPath);
        cfg.mapsPath = sharedPath(dir, Rukkit.getConfig().mapsPath);
        cfg.modsPath = sharedPath(dir, Rukkit.getConfig().modsPath);

        writeConfig(new File(dir, "rukkit.yml"), cfg);
        writeRoundConfig(new File(dir, "round.yml"), Rukkit.getRoundConfig());
        return safeName + " (port " + port + ", control " + cfg.serverManagerControlPort + ")";
    }

    public String cloneServer(String sourceName, String newName, Integer requestedPort) throws IOException {
        String source = sanitize(sourceName);
        RukkitConfig src = readConfig(new File(serverDir(source), "rukkit.yml"));
        if (src == null) throw new IOException("Source server does not exist: " + source);
        String safeName = sanitize(newName);
        File dir = new File(root(), safeName);
        if (dir.exists()) throw new IOException("Server already exists: " + safeName);
        if (countServers(root()) >= Rukkit.getConfig().serverManagerMaxServers) throw new IOException("Server manager limit reached");
        if (!dir.mkdirs()) throw new IOException("Cannot create: " + dir);
        int port = requestedPort != null ? requestedPort : choosePort(root(), Rukkit.getConfig().serverManagerBasePort);
        RukkitConfig cfg = copyConfig(src, port);
        cfg.serverUser = src.serverUser + " [" + safeName + "]";
        cfg.serverPort = port;
        cfg.logPath = new File(dir, "rukkit-error.log").getAbsolutePath();
        cfg.UUID = UUID.randomUUID().toString();
        cfg.serverManagerEnabled = false;
        cfg.serverManagerControlEnabled = true;
        cfg.serverManagerRoot = "servers";
        cfg.serverManagerControlPort = controlPortFor(port);
        cfg.serverManagerControlToken = UUID.randomUUID().toString();
        writeConfig(new File(dir, "rukkit.yml"), cfg);
        File sourceRound = new File(serverDir(source), "round.yml");
        if (sourceRound.isFile()) copyFile(sourceRound, new File(dir, "round.yml"));
        else writeRoundConfig(new File(dir, "round.yml"), Rukkit.getRoundConfig());
        return "cloned " + source + " -> " + safeName + " (port " + port + ")";
    }

    public String infoTarget(String target) {
        List<String> names = resolveTargets(target);
        if (names.isEmpty()) return "No matching servers.";
        StringBuilder out = new StringBuilder();
        for (String n : names) {
            out.append(info(n)).append("\n");
        }
        return out.toString().trim();
    }

    public String killTargets(String target) {
        List<String> names = resolveTargets(target);
        if (names.isEmpty()) return "No matching servers.";
        StringBuilder out = new StringBuilder();
        for (String name : names) {
            Process p = launchers.get(name);
            if (p != null && p.isAlive()) {
                p.destroyForcibly();
                out.append(name).append(": killed");
            } else {
                out.append(name).append(": not running or external");
            }
            out.append('\n');
        }
        return out.toString().trim();
    }

    public String start(String target) throws Exception {
        return startTargets(target, null);
    }

    public String start(String name, boolean openConsole) throws Exception {
        return startOne(name, openConsole);
    }

    public String startTargets(String target, Boolean consoleOverride) throws Exception {
        List<String> names = resolveTargets(target);
        if (names.isEmpty()) return "No matching servers.";
        StringBuilder out = new StringBuilder();
        for (String name : names) {
            try {
                boolean console = consoleOverride == null ? Rukkit.getConfig().serverManagerOpenConsole : consoleOverride;
                out.append(name).append(": ").append(startOne(name, console)).append('\n');
            } catch (Exception e) {
                out.append(name).append(": ERROR ").append(e.getMessage()).append('\n');
            }
        }
        return out.toString().trim();
    }

    private String startOne(String name, boolean openConsole) throws Exception {
        String safeName = sanitize(name);
        if (isRunning(safeName)) return "already running";
        File dir = serverDir(safeName);
        if (!dir.isDirectory()) throw new IOException("Server does not exist: " + safeName);

        String javaBin = new File(System.getProperty("java.home"), "bin" + File.separator + (isWindows() ? "java.exe" : "java")).getAbsolutePath();
        File jar = locateCurrentJar();
        if (jar == null || !jar.isFile()) throw new IOException("Rukkit JAR path could not be resolved");

        File logFile = new File(dir, "server.log");
        if (openConsole && isWindows()) {
            File bat = writeConsoleLauncher(safeName, dir, javaBin, jar);
            String startCommand = "start \"Rukkit-" + safeName + "\" cmd.exe /k \"" + bat.getAbsolutePath() + "\"";
            Process launcher = new ProcessBuilder("cmd.exe", "/c", startCommand).directory(dir).start();
            launchers.put(safeName, launcher);
            return "started in new console";
        }

        Process process = new ProcessBuilder(javaBin, "-jar", jar.getAbsolutePath())
                .directory(dir)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
                .start();
        launchers.put(safeName, process);
        return "started in background (pid " + process.pid() + ")";
    }

    public String stopTargets(String target) {
        return executeTargets(target, "shutdown");
    }

    public String restartTargets(String target) throws Exception {
        List<String> names = resolveTargets(target);
        if (names.isEmpty()) return "No matching servers.";
        StringBuilder out = new StringBuilder();
        for (String name : names) {
            stopOne(name);
            Thread.sleep(400);
            out.append(name).append(": ").append(startOne(name, Rukkit.getConfig().serverManagerOpenConsole)).append('\n');
        }
        return out.toString().trim();
    }

    private void stopOne(String name) {
        try { sendCommand(name, "shutdown"); } catch (Exception ignored) {}
        Process p = launchers.get(name);
        if (p != null && p.isAlive()) p.destroy();
    }

    public String sendTargets(String target, String command) {
        return executeTargets(target, command);
    }

    public String select(String target) {
        List<String> names = resolveTargets(target);
        if (names.isEmpty()) return "No matching servers.";
        selectedTarget = target;
        return "Selected: " + String.join(", ", names);
    }

    public String selected() {
        return "Selected target: " + selectedTarget;
    }

    private String executeTargets(String target, String command) {
        List<String> names = resolveTargets(target);
        if (names.isEmpty()) return "No matching servers.";
        StringBuilder out = new StringBuilder();
        for (String name : names) {
            try {
                out.append(name).append(": ").append(sendCommand(name, command)).append('\n');
            } catch (Exception e) {
                out.append(name).append(": ERROR ").append(e.getMessage()).append('\n');
            }
        }
        return out.toString().trim();
    }

    public String list(String filter) {
        File root = root();
        StringBuilder out = new StringBuilder("Servers (max ").append(Rukkit.getConfig().serverManagerMaxServers).append("):\n");
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null || dirs.length == 0) return out.append("(none)\n").toString();
        Arrays.sort(dirs, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        int index = 1;
        for (File dir : dirs) {
            boolean running = isRunning(dir.getName());
            if ("running".equalsIgnoreCase(filter) && !running) { index++; continue; }
            if ("stopped".equalsIgnoreCase(filter) && running) { index++; continue; }
            Integer port = readPort(new File(dir, "rukkit.yml"));
            out.append(String.format("%2d. %-20s %-8s", index, dir.getName(), running ? "RUNNING" : "STOPPED"));
            if (port != null) out.append(" port=").append(port);
            Integer control = readControlPort(new File(dir, "rukkit.yml"));
            if (control != null) out.append(" control=").append(control);
            Process p = launchers.get(dir.getName());
            if (p != null && p.isAlive()) out.append(" pid=").append(p.pid());
            out.append('\n');
            index++;
        }
        return out.toString();
    }

    public String info(String name) {
        String safeName = sanitize(name);
        File dir = serverDir(safeName);
        if (!dir.isDirectory()) return "Server does not exist: " + safeName;
        File cfgFile = new File(dir, "rukkit.yml");
        RukkitConfig cfg = readConfig(cfgFile);
        StringBuilder out = new StringBuilder();
        out.append("Server: ").append(safeName).append('\n');
        out.append("Status: ").append(isRunning(safeName) ? "RUNNING" : "STOPPED").append('\n');
        if (cfg != null) {
            out.append("PID: ");
            Process p = launchers.get(safeName);
            out.append(p != null && p.isAlive() ? p.pid() : "-").append('\n');
            out.append("Port: ").append(cfg.serverPort).append('\n');
            out.append("Control: ").append(cfg.serverManagerControlPort).append('\n');
            out.append("Name: ").append(cfg.serverUser).append('\n');
            out.append("Players: ").append(cfg.maxPlayer).append(" / Rooms: ").append(cfg.maxRoom).append('\n');
            out.append("Plugins: ").append(cfg.pluginsPath).append('\n');
            out.append("Maps: ").append(cfg.mapsPath).append('\n');
            out.append("Mods: ").append(cfg.modsPath).append('\n');
        }
        out.append("Directory: ").append(dir.getAbsolutePath()).append('\n');
        out.append("Config: ").append(cfgFile.getAbsolutePath()).append('\n');
        out.append("Log: ").append(new File(dir, "server.log").getAbsolutePath()).append('\n');
        return out.toString();
    }

    public String delete(String name, boolean force) throws IOException {
        String safeName = sanitize(name);
        if (isRunning(safeName) && !force) return "Server is running. Stop it first or use delete <name> force.";
        if (isRunning(safeName)) stopOne(safeName);
        File dir = serverDir(safeName);
        if (!dir.isDirectory()) return "Server does not exist: " + safeName;
        deleteRecursively(dir);
        return "deleted " + safeName;
    }

    public void stopAll() {
        for (String name : listNames()) stopOne(name);
        for (Process p : launchers.values()) if (p != null && p.isAlive()) p.destroy();
        launchers.clear();
    }

    public List<String> resolveTargets(String target) {
        if (target == null || target.trim().isEmpty()) return Collections.emptyList();
        if ("selected".equalsIgnoreCase(target)) target = selectedTarget;
        List<String> names = listNames();
        if ("all".equalsIgnoreCase(target)) return names;
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String token : target.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) continue;
            if (t.matches("\\d+-\\d+")) {
                String[] parts = t.split("-");
                int a = Integer.parseInt(parts[0]);
                int b = Integer.parseInt(parts[1]);
                if (a > b) { int tmp = a; a = b; b = tmp; }
                for (int i = Math.max(1, a); i <= Math.min(names.size(), b); i++) result.add(names.get(i - 1));
            } else if (t.matches("\\d+")) {
                int idx = Integer.parseInt(t);
                if (idx >= 1 && idx <= names.size()) result.add(names.get(idx - 1));
                String byName = "server" + t;
                if (new File(root(), byName).isDirectory()) result.add(byName);
            } else if (new File(root(), sanitize(t)).isDirectory()) {
                result.add(sanitize(t));
            }
        }
        return new ArrayList<>(result);
    }

    public boolean isRunning(String name) {
        try {
            RukkitConfig cfg = readConfig(new File(serverDir(name), "rukkit.yml"));
            if (cfg == null || !cfg.serverManagerControlEnabled) return false;
            return ping(cfg.serverManagerControlPort, cfg.serverManagerControlToken);
        } catch (Exception e) {
            Process p = launchers.get(name);
            return p != null && p.isAlive();
        }
    }

    private String sendCommand(String name, String command) throws IOException {
        RukkitConfig cfg = readConfig(new File(serverDir(name), "rukkit.yml"));
        if (cfg == null) throw new IOException("Cannot read server config");
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", cfg.serverManagerControlPort), 1500);
            socket.setSoTimeout(2500);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            out.write(cfg.serverManagerControlToken); out.newLine();
            out.write(command); out.newLine(); out.flush();
            String response = in.readLine();
            return response == null ? "sent" : response;
        } catch (IOException e) {
            throw new IOException("control channel unavailable", e);
        }
    }

    private boolean ping(int port, String token) {
        try {
            String r = sendRaw(port, token, "__ping__");
            return "PONG".equals(r);
        } catch (Exception e) { return false; }
    }

    private String sendRaw(int port, String token, String command) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 800);
            socket.setSoTimeout(1200);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            out.write(token); out.newLine();
            out.write(command); out.newLine(); out.flush();
            return in.readLine();
        }
    }

    private File root() { return new File(Rukkit.getEnvPath(), Rukkit.getConfig().serverManagerRoot); }
    private File serverDir(String name) { return new File(root(), sanitize(name)); }
    private String sharedPath(File childDir, String path) {
    if (path == null || path.isEmpty()) {
        return path;
    }

    File target = new File(path);

    if (!target.isAbsolute()) {
        target = new File(Rukkit.getEnvPath(), path);
    }

    try {
        return childDir.toPath()
                .relativize(target.toPath())
                .toString();
    } catch (IllegalArgumentException e) {
        return target.getAbsolutePath();
    }
}


    private RukkitConfig copyCurrentConfig(int port) {
        return copyConfig(Rukkit.getConfig(), port);
    }

    private RukkitConfig copyConfig(RukkitConfig src, int port) {
        RukkitConfig dst = new RukkitConfig();
        dst.serverUser = src.serverUser; dst.welcomeMsg = src.welcomeMsg; dst.serverMotd = src.serverMotd;
        dst.serverPort = port; dst.maxPlayer = src.maxPlayer; dst.maxRoom = src.maxRoom; dst.minStartPlayer = src.minStartPlayer;
        dst.syncEnabled = src.syncEnabled; dst.singlePlayerMode = src.singlePlayerMode; dst.isDebug = src.isDebug; dst.onlineMode = src.onlineMode;
        dst.logPath = src.logPath; dst.maxPacketFrame = src.maxPacketFrame; dst.lang = src.lang; dst.threadPoolCount = src.threadPoolCount;
        dst.maxUnitsPerPlayer = src.maxUnitsPerPlayer; dst.pingTimeout = src.pingTimeout; dst.registerTimeout = src.registerTimeout;
        dst.useCommandQuere = src.useCommandQuere; dst.checksumSync = src.checksumSync;
        dst.helpPageSize = src.helpPageSize; dst.helpShowDisabledCommands = src.helpShowDisabledCommands; dst.helpShowDescriptions = src.helpShowDescriptions;
        dst.helpHiddenCommands = new ArrayList<>(src.helpHiddenCommands); dst.maxTeams = src.maxTeams;
        dst.playerPermissions = new HashMap<>(src.playerPermissions); dst.adminPermissions = new HashMap<>(src.adminPermissions);
        dst.allowedIncomeValues = new ArrayList<>(src.allowedIncomeValues); dst.allowedCreditsValues = new ArrayList<>(src.allowedCreditsValues);
        dst.notifications = new LinkedHashMap<>(src.notifications);
        dst.pluginsPath = sharedPath(dir, src.pluginsPath);
        dst.mapsPath = sharedPath(dir, src.mapsPath);
        dst.modsPath = sharedPath(dir, src.modsPath);
        dst.serverManagerMaxServers = src.serverManagerMaxServers; dst.serverManagerControlEnabled = src.serverManagerControlEnabled;
        dst.serverManagerControlPortOffset = src.serverManagerControlPortOffset; dst.serverManagerOpenConsole = src.serverManagerOpenConsole;
        dst.serverManagerBasePort = src.serverManagerBasePort;
        return dst;
    }

    private int controlPortFor(int gamePort) {
        int port = gamePort + Rukkit.getConfig().serverManagerControlPortOffset;
        if (port > 65535) port = 10000 + (gamePort % 5000);
        return port;
    }

    private Integer readPort(File f) { RukkitConfig c = readConfig(f); return c == null ? null : c.serverPort; }
    private Integer readControlPort(File f) { RukkitConfig c = readConfig(f); return c == null ? null : c.serverManagerControlPort; }
    private RukkitConfig readConfig(File cfgFile) {
        if (!cfgFile.isFile()) return null;
        try (FileReader reader = new FileReader(cfgFile)) { return new Yaml().loadAs(reader, RukkitConfig.class); } catch (Exception e) { return null; }
    }

    private int choosePort(File root, int base) {
        Set<Integer> used = new HashSet<>();
        for (String n : listNames()) { Integer p = readPort(new File(root, n + File.separator + "rukkit.yml")); if (p != null) used.add(p); }
        int p = base;
        while (used.contains(p) || p == Rukkit.getConfig().serverPort) p++;
        return p;
    }

    private int countServers(File root) { File[] ds = root.listFiles(File::isDirectory); return ds == null ? 0 : ds.length; }
    private List<String> listNames() {
        File[] ds = root().listFiles(File::isDirectory);
        if (ds == null) return new ArrayList<>();
        Arrays.sort(ds, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        List<String> out = new ArrayList<>(); for (File d: ds) out.add(d.getName()); return out;
    }

    private File writeConsoleLauncher(String safeName, File dir, String javaBin, File jar) throws IOException {
        File bat = new File(dir, "start-console.bat");
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(bat), StandardCharsets.UTF_8))) {
            out.println("@echo off");
            out.println("title Rukkit - " + safeName);
            out.println("cd /d \"%~dp0\"");
            out.println("echo Starting Rukkit server '" + safeName + "'...");
            out.println("\"" + javaBin + "\" -jar \"" + jar.getAbsolutePath() + "\"");
            out.println("echo.");
            out.println("echo Rukkit stopped. Press any key to close this window.");
            out.println("pause >nul");
        }
        return bat;
    }

    private File locateCurrentJar() {
        try { File f = new File(Rukkit.class.getProtectionDomain().getCodeSource().getLocation().toURI()); if (f.isFile() && f.getName().endsWith(".jar")) return f.getAbsoluteFile(); } catch (Exception ignored) {}
        return null;
    }

    private void writeConfig(File file, RukkitConfig cfg) throws IOException {
        try (FileWriter writer = new FileWriter(file)) { writer.write(new Yaml().dumpAs(cfg, Tag.MAP, DumperOptions.FlowStyle.BLOCK)); }
    }

    private void writeRoundConfig(File file, cn.rukkit.config.RoundConfig cfg) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(new Yaml().dumpAs(cfg, Tag.MAP, DumperOptions.FlowStyle.BLOCK));
        }
    }

    private void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        }
    }

    private void deleteRecursively(File file) throws IOException {
        if (file.isDirectory()) { File[] children = file.listFiles(); if (children != null) for (File c : children) deleteRecursively(c); }
        if (!file.delete()) throw new IOException("Cannot delete: " + file);
    }

    private String sanitize(String name) {
        String safe = name == null ? "" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isEmpty()) throw new IllegalArgumentException("Invalid server name");
        return safe;
    }

    private boolean isWindows() { return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"); }
}
