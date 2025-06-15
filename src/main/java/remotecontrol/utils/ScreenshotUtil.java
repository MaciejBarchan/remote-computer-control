package remotecontrol.utils;

import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import javax.imageio.ImageIO;

public class ScreenshotUtil {
    public static void takeScreenshot(String fileName) throws AWTException, IOException {
        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage screenshot = new Robot().createScreenCapture(screenRect);
        ImageIO.write(screenshot, "png", new File(fileName));
    }

    public static ScreenCapture takeScreenshotAsCapture() throws AWTException, IOException {
        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage screenshot = new Robot().createScreenCapture(screenRect);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(screenshot, "png", baos);
        byte[] imageBytes = baos.toByteArray();

        return new ScreenCapture(imageBytes, screenshot.getWidth(), screenshot.getHeight());
    }

    public static void sendScreenshotToClient(ObjectOutputStream output) throws AWTException, IOException {
        ScreenCapture capture = takeScreenshotAsCapture();
        synchronized (output) {
            output.writeObject(capture);
            output.flush();
        }
    }
}