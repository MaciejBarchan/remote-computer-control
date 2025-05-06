package remotecontrol.service;

import remotecontrol.utils.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerService {
    int port;
    private ServerSocket serverSocket;
    private Thread serverThread;
    boolean isRunning;
    private Dimension screenSize;
    private Robot robot;
    private MessageService messageService;

    public ServerService(int port) {
        this.port = port;
        isRunning = false;
        screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    }

    public void setMessageService(MessageService messageService) {
        this.messageService = messageService;
    }

    public void start() {
        if(isRunning)
            return;

        try {
            robot = new java.awt.Robot();
        } catch (AWTException e) {
            Log.addLog("There was a problem with the application. Details: " + e.getMessage(), Log.TypeMessage.ERROR);
            throw new RuntimeException("Robot cannot be created: " + e.getMessage());
        }

        serverThread = new Thread(() -> {
            try{
                serverSocket = new ServerSocket(port);
                isRunning = true;
                Log.addLog("The server has been started", Log.TypeMessage.INFO);

                while(isRunning) {
                    Socket clientSocket = serverSocket.accept();
                    Log.addLog("The client has been connected: " + clientSocket.getInetAddress(), Log.TypeMessage.INFO);

                    Thread clientThread = new Thread(new ClientHandler(clientSocket, robot));
                    clientThread.setDaemon(true);
                    clientThread.start();
                }
            } catch (IOException e) {
                if(isRunning) {
                    Log.addLog(e.getMessage(), Log.TypeMessage.ERROR);
                }
            } finally {
                isRunning = false;
            }
        });

        serverThread.setDaemon(true);
        serverThread.start();
    }

    public void stop() {
        isRunning = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.addLog("Error when shutting down the server. Details: " + e.getMessage(), Log.TypeMessage.ERROR);
                System.err.println("Error when shutting down the server: " + e.getMessage());
            }
        }
    }

    public class ClientHandler implements Runnable {
        private Socket clientSocket;
        private Robot robot;
        private ObjectOutputStream output;
        private ObjectInputStream input;
        private boolean running;
        private boolean keepAlive;
        private Thread keepAliveThread;

        ClientHandler(Socket clientSocket, Robot robot) {
            this.clientSocket = clientSocket;
            this.robot = robot;
            this.running = true;
            this.keepAlive = true;
        }

        @Override
        public void run() {
            try {
                output = new ObjectOutputStream(clientSocket.getOutputStream());
                input = new ObjectInputStream(clientSocket.getInputStream());

                // Send screen size information
                output.writeObject(new ScreenDetails(screenSize.width, screenSize.height));
                output.flush();

                if (messageService != null) {
                    messageService.sendMessage("New client connected from " + clientSocket.getInetAddress().getHostAddress());
                }

                startKeepAliveThread();
                while (running) {
                    Command command = (Command) input.readObject();
                    processCommand(command);
                }
            } catch (IOException | ClassNotFoundException e) {
                Log.addLog("Customer service error. Details: " + e.getMessage(), Log.TypeMessage.ERROR);
                System.err.println("Customer service error: " + e.getMessage());
            } finally {
                close();
            }
        }

        private void startKeepAliveThread() {
            keepAliveThread = new Thread(() -> {
                try {
                    while (keepAlive && running) {
                        Thread.sleep(5000);
                        if (output != null && running) {
                            synchronized (output) {
                                output.writeObject(new KeepAlive());
                                output.flush();
                            }
                        }
                    }
                } catch (InterruptedException | IOException e) {
                    System.err.println("Error in keep-alive thread: " + e.getMessage());
                }
            });
            keepAliveThread.setDaemon(true);
            keepAliveThread.start();
        }

        private void sendScreenCapture() {
            try {
                Rectangle screenRect = new Rectangle(screenSize);
                BufferedImage screenCapture = robot.createScreenCapture(screenRect);

                // Scale down the image for better performance
                int newWidth = screenCapture.getWidth() / 2;
                int newHeight = screenCapture.getHeight() / 2;

                BufferedImage resizedImage = new BufferedImage(
                        newWidth,
                        newHeight,
                        BufferedImage.TYPE_INT_RGB);

                Graphics2D g = resizedImage.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(screenCapture, 0, 0, newWidth, newHeight, null);
                g.dispose();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(resizedImage, "jpg", baos);
                byte[] imageBytes = baos.toByteArray();

                synchronized (output) {
                    output.writeObject(new ScreenCapture(imageBytes, newWidth, newHeight));
                    output.flush();
                }
            } catch (Exception e) {
                Log.addLog("Error while sending screen. Details: " + e.getMessage(), Log.TypeMessage.ERROR);
                throw new RuntimeException("Error while sending screen: " + e.getMessage());
            }
        }

        private void close() {
            if (messageService != null) {
                messageService.sendMessage("Client disconnected from " + clientSocket.getInetAddress().getHostAddress());
            }
            keepAlive = false;
            running = false;
            try {
                if (output != null) output.close();
                if (input != null) input.close();
                if (clientSocket != null) clientSocket.close();
            } catch (IOException e) {
                Log.addLog("Error while closing the connection. Details: " + e.getMessage(), Log.TypeMessage.ERROR);
                System.err.println("Error while closing the connection: " + e.getMessage());
            }
        }

        private void processCommand(Command command) throws IOException {
            switch (command.getType()) {
                case MOUSE_MOVE:
                    robot.mouseMove(command.getX(), command.getY());
                    break;
                case MOUSE_PRESS:
                    robot.mousePress(command.getButton());
                    break;
                case MOUSE_RELEASE:
                    robot.mouseRelease(command.getButton());
                    break;
                case KEY_PRESS:
                    robot.keyPress(command.getKeyCode());
                    break;
                case KEY_RELEASE:
                    robot.keyRelease(command.getKeyCode());
                    break;
                case SCREEN_CAPTURE:
                    sendScreenCapture();
                    break;
                case DISCONNECT:
                    running = false;
                    break;
            }
        }

        public Socket getClientSocket() {
            return clientSocket;
        }
    }
}