package remotecontrol.service;

import remotecontrol.utils.Log;
import remotecontrol.utils.ScreenCapture;
import remotecontrol.utils.ScreenDetails;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

    public ServerService(int port) {
        this.port = port;
        isRunning = false;
        screenSize = Toolkit.getDefaultToolkit().getScreenSize();
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
                Socket clientSocket = serverSocket.accept();
                Log.addLog("The client has been connected: " + clientSocket.getInetAddress(), Log.TypeMessage.INFO);

                Thread clientThread = new Thread(new ClientHandler(clientSocket, robot));
                clientThread.setDaemon(true);
                clientThread.start();

            } catch (IOException e) {
                Log.addLog(e.getMessage(), Log.TypeMessage.ERROR);
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
        private java.awt.Robot robot;
        private ObjectOutputStream output;
        private boolean isRunning = true;

        ClientHandler(Socket clientSocket, Robot robot) {
            this.clientSocket = clientSocket;
            this.robot = robot;
        }

        @Override
        public void run() {
            try {
                output = new ObjectOutputStream(clientSocket.getOutputStream());

                output.writeObject(new ScreenDetails(screenSize.width, screenSize.height));
                output.flush();
                while (isRunning) {
                    sendScreenCapture();
                    Thread.sleep(100);
                }
            } catch (IOException | InterruptedException e) {
                Log.addLog("Customer service error. Details: " + e.getMessage(), Log.TypeMessage.ERROR);
                System.err.println("Customer service error: " + e.getMessage());
            } finally {
                close();
            }
        }

        private void sendScreenCapture() {
            try {
                Rectangle screenRect = new Rectangle(screenSize);
                BufferedImage screenCapture = robot.createScreenCapture(screenRect);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(screenCapture, "jpg", baos);
                byte[] imageBytes = baos.toByteArray();

                synchronized (output) {
                    output.writeObject(new ScreenCapture(imageBytes, screenSize.width, screenSize.height));
                    output.flush();
                }
            } catch (Exception e) {
                Log.addLog("Error while sending screen. Details: " + e.getMessage(), Log.TypeMessage.ERROR);
                throw new RuntimeException("Error while sending screen: " + e.getMessage());
            }
        }

        private void close() {
            isRunning = false;
            try {
                if (output != null) output.close();
                if (clientSocket != null) clientSocket.close();
            } catch (IOException e) {
                Log.addLog("Error while closing the connection. Details: " + e.getMessage(), Log.TypeMessage.ERROR);
                System.err.println("Error while closing the connection: " + e.getMessage());
            }
        }
    }
}
