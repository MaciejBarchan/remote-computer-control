package remotecontrol.service;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import remotecontrol.utils.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
    private MessageService messageService;
    private AtomicBoolean connectionHealthy = new AtomicBoolean(true);
    private long lastResponseTime = System.currentTimeMillis();
    private ObjectInputStream input;
    private ObjectOutputStream output;
    private int serverScreenWidth;
    private int serverScreenHeight;
    private Thread connectionWatchdog;

    public ClientService(String ipServer, int serverPort) {
        this.ipServer = ipServer;
        this.serverPort = serverPort;
        connected = false;
    }

    public Socket getSocket() {
        return socket;
    }

    public void connect() {
        if(connected)
            return;

        try {
            socket = new Socket(ipServer, serverPort);
            socket.setSoTimeout(10000); // 10 seconds timeout for I/O operations

            output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            input = new ObjectInputStream(socket.getInputStream());

            connected = true;

            Log.addLog("Connected with server", Log.TypeMessage.INFO);

            try {
                Object initialInfo = input.readObject();
                if (initialInfo instanceof ScreenDetails) {
                    ScreenDetails screenDetails = (ScreenDetails) initialInfo;
                    serverScreenWidth = screenDetails.getWidth();
                    serverScreenHeight = screenDetails.getHeight();
                    System.out.println("Received screen information: " + serverScreenWidth + "x" + serverScreenHeight);
                }
            } catch (ClassNotFoundException e) {
                System.err.println("Error while receiving screen information: " + e.getMessage());
                Log.addLog("Error while receiving screen information. Details:" + e.getMessage(), Log.TypeMessage.ERROR);
            }

            Thread responseThread = new Thread(this::listenForResponses);
            responseThread.setDaemon(true);
            responseThread.start();

            startConnectionWatchdog();

            lastResponseTime = System.currentTimeMillis();
        } catch (IOException ex) {
            Log.addLog("Could not connect to server. Details:" + ex.getMessage(), Log.TypeMessage.ERROR);
            throw new RuntimeException("Could not connect to server", ex);
        }
    }

    public void disconnect() {
        if (!connected) {
            return;
        }

        try {
            if (output != null) {
                try {
                    sendCommand(Command.createDisconnectCommand());
                } catch (IOException e) {
                }
            }

            if (input != null) {
                input.close();
            }
            if (output != null) {
                output.close();
            }
            if (socket != null) {
                socket.close();
            }

            if (connectionWatchdog != null) {
                connectionWatchdog.interrupt();
            }
        } catch (IOException e) {
            Log.addLog("Error while closing the connection. Details: " + e.getMessage(), Log.TypeMessage.ERROR);
            System.err.println("Error while closing the connection: " + e.getMessage());
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
            remoteStage.setTitle("Remote - " + ipServer + ":" + serverPort);

            screenView = new ImageView();
            screenView.setPreserveRatio(true);
            screenView.setFitWidth(1024);

            StackPane root = new StackPane(screenView);
            Scene scene = new Scene(root, 1024, 760);

            screenView.setOnMouseMoved(this::handleMouseMove);
            screenView.setOnMousePressed(this::handleMousePress);
            screenView.setOnMouseReleased(this::handleMouseRelease);

            scene.setOnKeyPressed(this::handleKeyPress);
            scene.setOnKeyReleased(this::handleKeyRelease);

            remoteStage.setScene(scene);
            remoteStage.show();

            screenUpdateService = Executors.newSingleThreadScheduledExecutor();
            screenUpdateService.scheduleAtFixedRate(() -> {
                if (connected && connectionHealthy.get()) {
                    try {
                        sendCommand(Command.createScreenCaptureCommand());
                    } catch (IOException e) {
                        Log.addLog("Error when requesting a screenshot. Details: " + e.getMessage(), Log.TypeMessage.ERROR);
                        System.err.println("Error when requesting a screenshot: " + e.getMessage());
                        connectionHealthy.set(false);
                    }
                }
            }, 0, 100, TimeUnit.MILLISECONDS);  // Update every 100ms

            remoteStage.setOnCloseRequest((WindowEvent we) -> {
                disconnect();
            });
        });
    }

    private void startConnectionWatchdog() {
        connectionWatchdog = new Thread(() -> {
            while (connected) {
                try {
                    Thread.sleep(2000);  // Check every 2 seconds

                    // Check timeout - if no response for 15 seconds
                    if (System.currentTimeMillis() - lastResponseTime > 15000) {
                        System.out.println("Watchdog: No response from server for 15 seconds");
                        connectionHealthy.set(false);

                        // Automatic disconnect and reconnect
                        Platform.runLater(() -> {
                            try {
                                disconnect();
                                Thread.sleep(1000);
                                connect();
                                showRemoteDesktop();
                            } catch (Exception e) {
                                Log.addLog("Failed to automatically reconnect. Details: " + e.getMessage(), Log.TypeMessage.ERROR);
                                System.err.println("Failed to automatically reconnect: " + e.getMessage());
                            }
                        });

                        break;  // Exit loop, new watchdog will be started after reconnect
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        connectionWatchdog.setDaemon(true);
        connectionWatchdog.start();
    }

    private void listenForResponses() {
        try {
            while (connected) {
                try {
                    Object response = input.readObject();

                    lastResponseTime = System.currentTimeMillis();

                    if (response instanceof ScreenCapture) {
                        updateScreenImage((ScreenCapture) response);
                    } else if (response instanceof KeepAlive) {
                        connectionHealthy.set(true);
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
        } finally {
            if (connected) {
                Platform.runLater(() -> {
                    try {
                        System.out.println("Attempting to reconnect...");
                        disconnect();
                        Thread.sleep(1000);
                        connect();
                        showRemoteDesktop();
                    } catch (Exception e) {
                        Log.addLog("It failed to automatically reconnect. Details: " + e.getMessage(), Log.TypeMessage.ERROR);
                        System.err.println("It failed to automatically reconnect: " + e.getMessage());
                    }
                });
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

    private void handleMouseMove(MouseEvent event) {
        if (!connected || !connectionHealthy.get()) return;

        try {
            // Calculate proportions for screen scaling
            double imageWidth = screenView.getBoundsInLocal().getWidth();
            double imageHeight = screenView.getBoundsInLocal().getHeight();

            // Get mouse coordinates
            double mouseX = event.getX();
            double mouseY = event.getY();

            // Convert to server screen coordinates
            int serverX = (int) (mouseX / imageWidth * serverScreenWidth);
            int serverY = (int) (mouseY / imageHeight * serverScreenHeight);

            // Ensure coordinates are within screen bounds
            serverX = Math.max(0, Math.min(serverX, serverScreenWidth - 1));
            serverY = Math.max(0, Math.min(serverY, serverScreenHeight - 1));

            sendCommand(Command.createMouseMoveCommand(serverX, serverY));
        } catch (IOException e) {
            Log.addLog("Error when sending mouse movement. Details: " + e.getMessage(), Log.TypeMessage.ERROR);
            System.err.println("Error when sending mouse movement: " + e.getMessage());
            connectionHealthy.set(false);
        }
    }

    private void handleMousePress(MouseEvent event) {
        if (!connected || !connectionHealthy.get()) return;

        try {
            int button = convertMouseButton(event.getButton());
            sendCommand(Command.createMousePressCommand(button));
        } catch (IOException e) {
            Log.addLog("Error while sending mouse press. Details: " + e.getMessage(), Log.TypeMessage.ERROR);
            System.err.println("Error while sending mouse press: " + e.getMessage());
            connectionHealthy.set(false);
        }
    }

    private void handleMouseRelease(MouseEvent event) {
        if (!connected || !connectionHealthy.get()) return;

        try {
            int button = convertMouseButton(event.getButton());
            sendCommand(Command.createMouseReleaseCommand(button));
        } catch (IOException e) {
            Log.addLog("Error while sending mouse release. Details: " + e.getMessage(), Log.TypeMessage.ERROR);
            System.err.println("Error while sending mouse release: " + e.getMessage());
            connectionHealthy.set(false);
        }
    }

    private void handleKeyPress(KeyEvent event) {
        if (!connected || !connectionHealthy.get()) return;

        try {
            int keyCode = event.getCode().getCode();
            sendCommand(Command.createKeyPressCommand(keyCode));
        } catch (IOException e) {
            Log.addLog("Error while sending key press. Details: " + e.getMessage(), Log.TypeMessage.ERROR);
            System.err.println("Error while sending key press: " + e.getMessage());
            connectionHealthy.set(false);
        }
    }

    private void handleKeyRelease(KeyEvent event) {
        if (!connected || !connectionHealthy.get()) return;

        try {
            int keyCode = event.getCode().getCode();
            sendCommand(Command.createKeyReleaseCommand(keyCode));
        } catch (IOException e) {
            Log.addLog("Error while sending key release. Details: " + e.getMessage(), Log.TypeMessage.ERROR);
            System.err.println("Error while sending key release: " + e.getMessage());
            connectionHealthy.set(false);
        }
    }

    private int convertMouseButton(MouseButton button) {
        switch (button) {
            case PRIMARY:
                return java.awt.event.InputEvent.BUTTON1_DOWN_MASK;
            case SECONDARY:
                return java.awt.event.InputEvent.BUTTON3_DOWN_MASK;
            case MIDDLE:
                return java.awt.event.InputEvent.BUTTON2_DOWN_MASK;
            default:
                return java.awt.event.InputEvent.BUTTON1_DOWN_MASK;
        }
    }

    private void sendCommand(Command command) throws IOException {
        if (connected && output != null) {
            synchronized (output) {
                output.writeObject(command);
                output.flush();
            }
        }
    }
}