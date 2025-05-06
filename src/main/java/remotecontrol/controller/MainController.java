package remotecontrol.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.text.TextFlow;
import remotecontrol.service.ClientService;
import remotecontrol.service.MessageService;
import remotecontrol.service.ServerService;
import remotecontrol.utils.Log;
import remotecontrol.utils.Notification;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.Objects;

public class MainController {
    @FXML
    private Button startButton;
    @FXML
    private Button sendButton;
    @FXML
    private TextArea msgTextArea;
    @FXML
    private TextField portTextField;
    @FXML
    private TextField ipAddressTextField;
    @FXML
    private ToggleGroup typeModeToggle;
    @FXML
    private TextFlow consoleLogTextFlow;
    @FXML
    private RadioButton serverModeRadio;
    @FXML
    private RadioButton clientModeRadio;
    @FXML
    private ScrollPane consoleLogScrollPane;

    private ServerService serverService;
    private ClientService clientService;
    private MessageService messageService;

    @FXML
    private  void initialize() {
        Log.setControls(consoleLogTextFlow, consoleLogScrollPane);

        msgTextArea.setOnKeyPressed(event -> {
            if(event.getCode() == KeyCode.ENTER) {
                event.consume();
                try {
                    onSendButtonClick();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        typeModeToggle.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                ipAddressTextField.setDisable(!newValue.getToggleGroup().getSelectedToggle().equals(clientModeRadio));
                portTextField.setDisable(false);
                startButton.setDisable(false);
            } else {
                ipAddressTextField.setDisable(true);
                portTextField.setDisable(true);
                startButton.setDisable(true);
            }
        });
    }

    @FXML
    private void onStartButtonClick () throws UnknownHostException {
        RadioButton selectedRadioButton = (RadioButton) typeModeToggle.getSelectedToggle();
        String radioText = selectedRadioButton.getText();

        if(Objects.equals(startButton.getText(), "Start")) {
            start(radioText);
        } else if (Objects.equals(startButton.getText(), "Stop")) {
            stop(radioText);
        }
    }


    @FXML
    private void onSendButtonClick () throws IOException {
        if(!msgTextArea.getText().isEmpty()) {
            //if(messageService != null && messageService.isConnected()) {
                messageService.sendMessage(msgTextArea.getText());
                msgTextArea.clear();
           // } else {
            //    Log.addLog("No active message connection", Log.TypeMessage.ERROR);
           // }
        }
    }

    private String checkTextFields() {
        String ipAddress = ipAddressTextField.getText();
        if(!isValidIp(ipAddress) && ipAddress.isBlank() && clientModeRadio.isSelected()) {
            return "Invalid IP Address";
        }

        String port = portTextField.getText();
        if(!isValidPort(port) && port.isBlank()) {
            return "Invalid Port";
        }

        return "";
    }

    private boolean isValidIp(String ip) {
        return ip.matches("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");
    }

    private boolean isValidPort(String port) {
        try {
            int portNumber = Integer.parseInt(port);
            return portNumber >= 0 && portNumber <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }


    private void start(String type) throws UnknownHostException {
        consoleLogTextFlow.getChildren().clear();

        String message = "Starting " + type.toLowerCase();
        Log.addLog(message, Log.TypeMessage.INFO);

        if(!checkTextFields().isBlank()) {
            Log.addLog(checkTextFields(), Log.TypeMessage.ERROR);
            return;
        }

        if(type.equals("Client")) {
            ipAddressTextField.setDisable(true);
        } else {
            Inet4Address localHost = (Inet4Address) Inet4Address.getLocalHost();
            String ipAddress = localHost.getHostAddress();
            ipAddressTextField.setText(ipAddress);
        }
        portTextField.setDisable(true);
        serverModeRadio.setDisable(true);
        clientModeRadio.setDisable(true);

        message = "Start " + type.toLowerCase() + " IP address: " +
                ipAddressTextField.getText() + " port: " + portTextField.getText();
        Log.addLog(message, Log.TypeMessage.INFO);

        int chatPort = Integer.parseInt(portTextField.getText()) + 1;

        if(typeModeToggle.getSelectedToggle().equals(serverModeRadio)) {
            serverService = new ServerService(Integer.parseInt(portTextField.getText()));
            serverService.start();

            Notification notification = new Notification();
            try {
                messageService = new MessageService(chatPort);
                messageService.startServer();
                messageService.startReceivingMessages(msg -> {
                    Log.addLog("[from client] " + msg, Log.TypeMessage.MESSAGE);
                    notification.sendNotification(msg);
                });

                serverService.setMessageService(messageService);

                messageService.sendMessage("Server ready for connections");
            } catch (Exception e) {
                Log.addLog("Failed to start message service: " + e.getMessage(), Log.TypeMessage.ERROR);
            }

        } else if (typeModeToggle.getSelectedToggle().equals(clientModeRadio)) {
            clientService = new ClientService(ipAddressTextField.getText(), Integer.parseInt(portTextField.getText()));
            clientService.connect();
            clientService.showRemoteDesktop();

            Notification notification = new Notification();
            try {
                messageService = new MessageService(ipAddressTextField.getText(), chatPort);
                messageService.startReceivingMessages(msg -> {
                    Log.addLog("[from server] " + msg, Log.TypeMessage.MESSAGE);
                    notification.sendNotification(msg);
                });

                clientService.setMessageService(messageService);

                messageService.sendMessage("Client connected from " + Inet4Address.getLocalHost().getHostAddress());
            } catch (IOException e) {
                Log.addLog("Failed to connect to message service: " + e.getMessage(), Log.TypeMessage.ERROR);
            }
        } else {
            Log.addLog("Type has not been selected", Log.TypeMessage.ERROR);
        }

        startButton.setText("Stop");
        sendButton.setDisable(false);
        msgTextArea.setDisable(false);
    }


    private void stop(String type) {
        Log.addLog("Stopped " + type.toLowerCase(), Log.TypeMessage.INFO);

        if(typeModeToggle.getSelectedToggle().equals(serverModeRadio)) {
            if (serverService != null) {
                serverService.stop();
                serverService = null;
            }
            if (messageService != null) {
                messageService.stopServer();
                messageService = null;
            }
        } else if(typeModeToggle.getSelectedToggle().equals(clientModeRadio)) {
            if (clientService != null) {
                clientService.disconnect();
                clientService = null;
            }
            if (messageService != null) {
                messageService.disconnect();
                messageService = null;
            }
        }

        if(clientModeRadio.isSelected()) {
            ipAddressTextField.setDisable(false);
        } else {
            ipAddressTextField.clear();
        }
        portTextField.setDisable(false);
        serverModeRadio.setDisable(false);
        clientModeRadio.setDisable(false);
        sendButton.setDisable(true);
        msgTextArea.setDisable(true);

        startButton.setText("Start");
    }
}
