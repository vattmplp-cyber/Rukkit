package cn.rukkit.service;

import cn.rukkit.Rukkit;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Localhost control endpoint for parent ServerInstanceManager. */
public final class ServerControlServer {
    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread thread;

    public void start() throws IOException {
        if (!Rukkit.getConfig().serverManagerControlEnabled) return;
        if (Rukkit.getConfig().serverManagerControlPort < 1 || Rukkit.getConfig().serverManagerControlPort > 65535) return;
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), Rukkit.getConfig().serverManagerControlPort));
        running = true;
        thread = new Thread(this::acceptLoop, "Rukkit-Control");
        thread.setDaemon(true);
        thread.start();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                Thread t = new Thread(() -> handle(socket), "Rukkit-Control-Client");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (running) Rukkit.getLogger().warn("Server control accept failed", e);
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket s = socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8))) {
            if (!InetAddress.getLoopbackAddress().equals(s.getInetAddress())) { out.write("DENIED"); out.newLine(); out.flush(); return; }
            String token = in.readLine();
            String command = in.readLine();
            if (token == null || command == null || !token.equals(Rukkit.getConfig().serverManagerControlToken)) {
                out.write("DENIED"); out.newLine(); out.flush(); return;
            }
            if ("__ping__".equals(command)) { out.write("PONG"); out.newLine(); out.flush(); return; }
            Rukkit.getCommandManager().executeServerCommand(command);
            out.write("OK"); out.newLine(); out.flush();
        } catch (Exception e) {
            // Child command errors are logged locally; the manager receives a simple failure response.
        }
    }

    public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
    }
}
