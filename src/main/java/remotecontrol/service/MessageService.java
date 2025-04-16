package remotecontrol.service;


import remotecontrol.utils.MessageHandler;

import java.io.*;
import java.net.Socket;

public class MessageService {
    private final Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private Thread receiveThread;
    private volatile boolean running = false;

    public MessageService(String ip, int port) throws IOException {
        this.socket = new Socket(ip, port + 100);
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
    }

    public void sendMessage(String message) throws IOException {
        writer.write(message);
        writer.newLine();
        writer.flush();
    }

    public void startReceivingMessages(MessageHandler handler) {
        running = true;
        receiveThread = new Thread(() -> {
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    handler.onMessageReceived(line);
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error while receiving messages: " + e.getMessage());
                }
            }
        });
        receiveThread.start();
    }

    public void stop() {
        running = false;
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Error during closing: " + e.getMessage());
        }
    }
}
