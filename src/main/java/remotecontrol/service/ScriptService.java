package remotecontrol.service;

import remotecontrol.utils.*;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ScriptService {
    String ipServer;
    int serverPort;
    Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private static final String SCRIPTS_FOLDER = "scripts";
    private static final String RESULT_FOLDER = "result";
    private int screenshotCounter = 1;

    public ScriptService(String ipServer, int serverPort) {
        this.ipServer = ipServer;
        this.serverPort = serverPort;
    }

    public void connect() {
        try {
            socket = new Socket(ipServer, serverPort);
            output = new ObjectOutputStream(socket.getOutputStream());
            input = new ObjectInputStream(socket.getInputStream());

            startListening();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        try {
            if (output != null) output.close();
            if (input != null) input.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void executeScript(String pathToScript) {
        if (!isValidScriptPath(pathToScript)) {
            Log.addLog("Invalid script path", Log.TypeMessage.WARNING);
            return;
        }

        try {
            FileInfo fileInfo = new FileInfo();
            fileInfo.setPathToFile(pathToScript);
            output.writeObject(fileInfo);
            output.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startListening() {
        Thread listenerThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object received = input.readObject();
                    if (received instanceof ScreenCapture) {
                        saveScreenshot((ScreenCapture) received);
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Connection lost: " + e.getMessage());
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void saveScreenshot(ScreenCapture screenCapture) {
        try {
            File resultDir = new File(RESULT_FOLDER);
            if (!resultDir.exists()) {
                resultDir.mkdirs();
            }

            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
            String timestamp = now.format(formatter);
            String filename = timestamp + "_" + screenshotCounter + ".png";
            screenshotCounter++;

            File outputFile = new File(resultDir, filename);
            Files.write(outputFile.toPath(), screenCapture.getImageData());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isValidScriptPath(String pathToScript) {
        if (pathToScript == null || pathToScript.trim().isEmpty()) {
            return false;
        }

        if (!pathToScript.toLowerCase().endsWith(".bat")) {
            return false;
        }

        try {
            Path scriptPath = Paths.get(pathToScript);
            Path scriptsDir = Paths.get(System.getProperty("user.dir"), SCRIPTS_FOLDER);
            Path resolvedPath = scriptsDir.resolve(scriptPath).normalize();

            return resolvedPath.startsWith(scriptsDir.normalize());
        } catch (Exception e) {
            return false;
        }
    }
}