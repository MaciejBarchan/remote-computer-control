package remotecontrol.utils;

import java.awt.*;
import java.io.IOException;

public class Notification {
    private String getOsName() {
        return System.getProperty("os.name").toLowerCase();
    }

    public void sendNotification(String message) {
        String osName = getOsName();
        if(osName.contains("win")) {
            if (SystemTray.isSupported()) {
                SystemTray tray = SystemTray.getSystemTray();
                Image image = Toolkit.getDefaultToolkit().createImage("icon.png");

                TrayIcon trayIcon = new TrayIcon(image, "App");
                trayIcon.setImageAutoSize(true);
                try {
                    tray.add(trayIcon);
                } catch (AWTException e) {
                    throw new RuntimeException(e);
                }

                trayIcon.displayMessage("Nowa wiadomość!", "Otrzymano wiadomość: " + message, TrayIcon.MessageType.INFO);
            } else {
                System.out.println("System tray not supported!");
            }
        } else if (osName.contains("nux")) {
            try {
                Runtime.getRuntime().exec(new String[]{"Remote Desktop", "Nowa wiadomość!", "Otrzymano wiadomość: " + message});
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
