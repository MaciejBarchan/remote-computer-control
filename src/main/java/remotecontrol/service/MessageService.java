package remotecontrol.service;

import remotecontrol.utils.Log;
import remotecontrol.utils.Notification;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class MessageService {
    private String ipAddress;
    private int port;
    private Socket socket;
    private ServerSocket serverSocket;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private Thread receiverThread;
    private boolean isRunning;
    private CopyOnWriteArrayList<Consumer<String>> messageListeners;

    public MessageService(String ipAddress, int port) throws IOException {
        this.ipAddress = ipAddress;
        this.port = port;
        this.messageListeners = new CopyOnWriteArrayList<>();
        connectAsClient();
    }

    public MessageService(int port) {
        this.port = port;
        this.messageListeners = new CopyOnWriteArrayList<>();
    }

    private void connectAsClient() throws IOException {
        try {
            socket = new Socket(ipAddress, port);
            output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            input = new ObjectInputStream(socket.getInputStream());
            Log.addLog("Message service connected to " + ipAddress + ":" + port, Log.TypeMessage.INFO);
        } catch (IOException e) {
            Log.addLog("Failed to connect message service: " + e.getMessage(), Log.TypeMessage.ERROR);
            throw e;
        }
    }

    public void startServer() {
        if (isRunning) return;

        Thread serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                isRunning = true;
                Log.addLog("Message service started on port " + port, Log.TypeMessage.INFO);

                while (isRunning) {
                    Socket clientSocket = serverSocket.accept();
                    Log.addLog("Message client connected from " + clientSocket.getInetAddress(), Log.TypeMessage.INFO);

                    Thread clientHandler = new Thread(new MessageClientHandler(clientSocket));
                    clientHandler.setDaemon(true);
                    clientHandler.start();
                }
            } catch (IOException e) {
                if (isRunning) {
                    Log.addLog("Message server error: " + e.getMessage(), Log.TypeMessage.ERROR);
                }
            } finally {
                isRunning = false;
            }
        });

        serverThread.setDaemon(true);
        serverThread.start();
    }

    public void startReceivingMessages(Consumer<String> messageHandler) {
        if (messageHandler != null) {
            messageListeners.add(messageHandler);
        }

        if (receiverThread != null && receiverThread.isAlive()) {
            return;
        }

        receiverThread = new Thread(() -> {
            try {
                while (socket != null && !socket.isClosed()) {
                    try {
                        Object obj = input.readObject();
                        if (obj instanceof String) {
                            String message = (String) obj;
                            Log.addLog("Received: " + message, Log.TypeMessage.INFO);

                            for (Consumer<String> listener : messageListeners) {
                                listener.accept(message);
                            }
                        }
                    } catch (ClassNotFoundException e) {
                        Log.addLog("Received an unknown message type", Log.TypeMessage.ERROR);
                    }
                }
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    Log.addLog("Error while receiving messages: " + e.getMessage(), Log.TypeMessage.ERROR);
                }
            }
        });

        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    public void sendMessage(String message) {
        message = message.trim();
        try {
            if (socket != null && !socket.isClosed() && output != null) {
                synchronized (output) {
                    output.writeObject(message);
                    output.flush();
                    Log.addLog("[Sent] " + message, Log.TypeMessage.INFO);
                }
            }
            else if (!clientHandlers.isEmpty()) {
                for (MessageClientHandler handler : clientHandlers) {
                    handler.sendMessageToClient(message);
                }
                Log.addLog("[Sent]: " + message, Log.TypeMessage.INFO);
            } else {
                Log.addLog("Cannot send message, no connections established", Log.TypeMessage.ERROR);
                if (serverSocket != null && !serverSocket.isClosed()) {
                    Log.addLog("Server message (no clients): " + message, Log.TypeMessage.INFO);
                }
            }
        } catch (IOException e) {
            Log.addLog("Error sending message: " + e.getMessage(), Log.TypeMessage.ERROR);
        }
    }

    public void stopServer() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.addLog("Error stopping message server: " + e.getMessage(), Log.TypeMessage.ERROR);
        }
    }

    public void disconnect() {
        try {
            if (receiverThread != null) {
                receiverThread.interrupt();
            }

            if (input != null) {
                input.close();
            }

            if (output != null) {
                output.close();
            }

            if (socket != null && !socket.isClosed()) {
                socket.close();
            }

            Log.addLog("Message service disconnected", Log.TypeMessage.INFO);
        } catch (IOException e) {
            Log.addLog("Error disconnecting message service: " + e.getMessage(), Log.TypeMessage.ERROR);
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    private CopyOnWriteArrayList<MessageClientHandler> clientHandlers = new CopyOnWriteArrayList<>();

    private class MessageClientHandler implements Runnable {
        private Socket clientSocket;
        private ObjectOutputStream clientOutput;
        private ObjectInputStream clientInput;

        public MessageClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try {
                clientOutput = new ObjectOutputStream(clientSocket.getOutputStream());
                clientOutput.flush();
                clientInput = new ObjectInputStream(clientSocket.getInputStream());

                clientHandlers.add(this);

                while (!clientSocket.isClosed()) {
                    try {
                        Object obj = clientInput.readObject();
                        if (obj instanceof String) {
                            String message = (String) obj;
                            Log.addLog("Received from client: " + message, Log.TypeMessage.INFO);

                            for (Consumer<String> listener : messageListeners) {
                                listener.accept(message);
                            }
                        }
                    } catch (ClassNotFoundException e) {
                        Log.addLog("Received unknown message type from client", Log.TypeMessage.ERROR);
                    }
                }
            } catch (IOException e) {
                if (!clientSocket.isClosed()) {
                    Log.addLog("Error in message client handler: " + e.getMessage(), Log.TypeMessage.ERROR);
                }
            } finally {
                clientHandlers.remove(this);
                try {
                    if (clientInput != null) clientInput.close();
                    if (clientOutput != null) clientOutput.close();
                    if (clientSocket != null) clientSocket.close();
                } catch (IOException e) {
                    Log.addLog("Error closing client resources: " + e.getMessage(), Log.TypeMessage.ERROR);
                }
            }
        }

        public void sendMessageToClient(String message) {
            try {
                if (clientSocket != null && !clientSocket.isClosed() && clientOutput != null) {
                    synchronized (clientOutput) {
                        clientOutput.writeObject(message);
                        clientOutput.flush();
                    }
                }
            } catch (IOException e) {
                Log.addLog("Error sending message to client: " + e.getMessage(), Log.TypeMessage.ERROR);
            }
        }
    }
}