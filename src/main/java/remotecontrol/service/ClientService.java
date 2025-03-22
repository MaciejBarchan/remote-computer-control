package remotecontrol.service;

import remotecontrol.utils.Log;

import java.io.IOException;
import java.net.Socket;

public class ClientService {
    String ipServer;
    int serverPort;
    Socket socket;
    boolean connected;

    public ClientService(String ipServer, int serverPort) {
        this.ipServer = ipServer;
        this.serverPort = serverPort;
        connected = false;
    }

    public void connect() {
        if(connected)
            return;

        try {
            socket = new Socket(ipServer, serverPort);
            connected = true;

            Log.addLog("Connected with server", Log.TypeMessage.INFO);
            //Thread responseThread = new Thread(this::listenForResponses);
            //responseThread.setDaemon(true);
            //responseThread.start();
        } catch (IOException ex) {
            Log.addLog(ex.getMessage(), Log.TypeMessage.ERROR);
        }
    }
}
