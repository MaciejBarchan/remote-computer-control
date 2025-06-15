package remotecontrol.utils;

import java.io.Serial;
import java.io.Serializable;

public class FileInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    String pathToFile;

    public String getPathToFile() {
        return pathToFile;
    }

    public void setPathToFile(String pathToFile) {
        this.pathToFile = pathToFile;
    }
}
