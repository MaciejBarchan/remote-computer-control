package remotecontrol.service;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import remotecontrol.utils.Log;
import remotecontrol.utils.ScreenCapture;

import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientService {
    String ipServer;
    int serverPort;
    Socket socket;
    boolean connected;
    private Stage remoteStage;
    private ImageView screenView;
    private ScheduledExecutorService screenUpdateService;
    private AtomicBoolean connectionHealthy = new AtomicBoolean(true);
    private long lastResponseTime = System.currentTimeMillis();
    private ObjectInputStream input;

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
            socket.setSoTimeout(10000);
            input = new ObjectInputStream(socket.getInputStream());
            connected = true;

            Log.addLog("Connected with server", Log.TypeMessage.INFO);
            Thread responseThread = new Thread(this::listenForResponses);
            responseThread.setDaemon(true);
            responseThread.start();

            Platform.runLater(this::showRemoteDesktop);

            lastResponseTime = System.currentTimeMillis();
        } catch (IOException ex) {
            Log.addLog("Could not connect to server" + ex.getMessage(), Log.TypeMessage.ERROR);
            throw new RuntimeException("Could not connect to server", ex);
        }
    }

    public void disconnect() {
        if (!connected) {
            return;
        }

        try {
            if (input != null) {
                input.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            Log.addLog("Error while closing the connection. Details: " + e.getMessage(), Log.TypeMessage.ERROR);
            System.err.println("Error while closing the connection. Details: " + e.getMessage());
        } finally {
            connected = false;
            if (screenUpdateService != null) {
                screenUpdateService.shutdown();
            }
            Platform.runLater(() -> {
                if (remoteStage != null) {
                    remoteStage.close();
                    remoteStage = null;
                }
            });
        }
    }

    public void showRemoteDesktop() {
        Platform.runLater(() -> {
            remoteStage = new Stage();
            remoteStage.setTitle("Remote Desktop - " + ipServer + ":" + serverPort);

            screenView = new ImageView();
            screenView.setPreserveRatio(true);
            screenView.setFitWidth(1024);

            StackPane root = new StackPane(screenView);
            Scene scene = new Scene(root, 1024, 768);

            remoteStage.setScene(scene);
            remoteStage.show();

            screenUpdateService = Executors.newSingleThreadScheduledExecutor();
            screenUpdateService.scheduleAtFixedRate(this::requestScreenUpdate, 0, 100, TimeUnit.MILLISECONDS);

            remoteStage.setOnCloseRequest((WindowEvent we) -> disconnect());
        });
    }

    private void requestScreenUpdate() {
        if (connected && connectionHealthy.get()) {
            lastResponseTime = System.currentTimeMillis();
        }
    }

    private void listenForResponses() {
        try {
            while (connected) {
                try {
                    Object response = input.readObject();
                    lastResponseTime = System.currentTimeMillis();
                    if (response instanceof ScreenCapture) {
                        updateScreenImage((ScreenCapture) response);
                    }
                } catch (SocketException e) {
                    if (connected) {
                        Log.addLog("The connection has been severed. Details: " + e.getMessage(), Log.TypeMessage.ERROR);
                        System.err.println("The connection has been severed: " + e.getMessage());
                        connectionHealthy.set(false);
                        break;
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            if (connected) {
                Log.addLog("Error while receiving data. Details: " + e.getMessage(), Log.TypeMessage.ERROR);
                System.err.println("Error while receiving data: " + e.getMessage());
                connectionHealthy.set(false);
            }
        }
    }

    private void updateScreenImage(ScreenCapture screenCapture) {
        try {
            byte[] imageData = screenCapture.getImageData();
            ByteArrayInputStream bis = new ByteArrayInputStream(imageData);
            Image image = new Image(bis);
            Platform.runLater(() -> screenView.setImage(image));
        } catch (Exception e) {
            Log.addLog("Error during update. Details: " + e.getMessage(), Log.TypeMessage.ERROR);
            System.err.println("Error during update: " + e.getMessage());
        }
    }
}
