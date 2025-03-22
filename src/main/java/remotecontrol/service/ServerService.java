package remotecontrol.service;

import remotecontrol.utils.Log;

import java.awt.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerService {
    int port;
    private ServerSocket serverSocket;
    private Thread serverThread;
    boolean isRunning;
    private Dimension screenSize;

    public ServerService(int port) {
        this.port = port;
        isRunning = false;
        screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    }

    public void start() {
        if(isRunning)
            return;

        serverThread = new Thread(() -> {
            try{
                serverSocket = new ServerSocket(port);
                isRunning = true;
                Log.addLog("The server has been started", Log.TypeMessage.INFO);
                Socket clientSocket = serverSocket.accept();
                Log.addLog("The client has been connected: " + clientSocket.getInetAddress(), Log.TypeMessage.INFO);

            } catch (IOException ex) {
                Log.addLog(ex.getMessage(), Log.TypeMessage.ERROR);
            } finally {
                isRunning = false;
            }
        });

        serverThread.setDaemon(true);
        serverThread.start();
    }
}
