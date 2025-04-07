package remotecontrol.utils;

import java.io.Serializable;

public class ScreenCapture implements Serializable {
    private static final long serialVersionUID = 1L;

    private byte[] imageData;
    private int width;
    private int height;

    public ScreenCapture(byte[] imageData, int width, int height) {
        this.imageData = imageData;
        this.width = width;
        this.height = height;
    }

    public byte[] getImageData() {
        return imageData;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
