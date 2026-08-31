package cn.rukkit.service;

import cn.rukkit.Rukkit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Console implementation for: server edit ... */
public final class ServerConfigManagerCommand {
    private ServerConfigManagerCommand() {}

    public static boolean execute(String[] args) {
        if (!Rukkit.getConfig().serverManagerEnabled) {
            System.out.println("Server manager is disabled in rukkit.yml");
            return true;
        }
        if (args.length < 2 || "help".equalsIgnoreCase(args[1])) {
            System.out.println(ServerConfigEditor.help());
            return true;
        }

        ServerInstanceManager manager = Rukkit.getServerInstanceManager();
        if (manager == null) {
            System.out.println("Server manager is not initialized.");
            return true;
        }

        String target = args[1];
        List<String> rest = new ArrayList<>();
        boolean stopFirst = false;
        boolean show = false;
        for (int i = 2; i < args.length; i++) {
            if ("--stop".equalsIgnoreCase(args[i])) stopFirst = true;
            else if ("--show".equalsIgnoreCase(args[i])) show = true;
            else {
                String assignment = args[i];
                int equals = assignment.indexOf('=');
                if (equals > 0 && "mapName".equalsIgnoreCase(assignment.substring(0, equals).trim())) {
                    assignment = "roundMapName=" + assignment.substring(equals + 1);
                }
                rest.add(assignment);
            }
        }

        List<String> names = manager.resolveTargets(target);
        if (names.isEmpty()) {
            System.out.println("No matching servers: " + target);
            return true;
        }

        File root = new File(Rukkit.getEnvPath(), Rukkit.getConfig().serverManagerRoot);
        if (show || rest.isEmpty()) {
            for (String name : names) {
                File cfg = new File(root, name + File.separator + "rukkit.yml");
                try {
                    System.out.println(name + ": " + ServerConfigEditor.describe(cfg));
                } catch (Exception e) {
                    System.out.println(name + ": ERROR " + e.getMessage());
                }
            }
            if (rest.isEmpty()) System.out.println("Use 'server edit help' for settings.");
            return true;
        }

        for (String name : names) {
            File cfg = new File(root, name + File.separator + "rukkit.yml");
            if (!cfg.isFile()) {
                System.out.println(name + ": ERROR config not found");
                continue;
            }

            boolean running = manager.isRunning(name);
            if (running) {
                if (!stopFirst) {
                    System.out.println(name + ": RUNNING — stop it first, or use '--stop':");
                    System.out.println("  server stop " + name);
                    System.out.println("  server edit " + name + " --stop " + String.join(" ", rest));
                    continue;
                }
                System.out.println(name + ": stopping before edit...");
                try {
                    manager.stopTargets(name);
                    long deadline = System.currentTimeMillis() + 8000L;
                    while (manager.isRunning(name) && System.currentTimeMillis() < deadline) Thread.sleep(250L);
                    if (manager.isRunning(name)) {
                        System.out.println(name + ": ERROR server did not stop within 8 seconds; config was not changed.");
                        continue;
                    }
                } catch (Exception e) {
                    System.out.println(name + ": ERROR stopping server: " + e.getMessage());
                    continue;
                }
            }

            try {
                System.out.println(name + ": changed " + ServerConfigEditor.editMany(cfg, rest));
                System.out.println(name + ": saved to " + cfg.getAbsolutePath());
                if (running) System.out.println(name + ": restart it with 'server start " + name + " background' when ready.");
            } catch (Exception e) {
                System.out.println(name + ": ERROR " + e.getMessage());
            }
        }
        return true;
    }
}
