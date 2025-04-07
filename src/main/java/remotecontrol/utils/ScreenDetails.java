package remotecontrol.utils;

import java.io.Serializable;

public class ScreenDetails implements Serializable {
    private static final long serialVersionUID = 1L;

    private int width;
    private int height;

    public ScreenDetails(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
