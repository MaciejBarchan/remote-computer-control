package remotecontrol.utils;

import java.io.Serial;
import java.io.Serializable;

public class KeepAlive implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private long timestamp;

    public KeepAlive() {
        this.timestamp = System.currentTimeMillis();
    }

    public long getTimestamp() {
        return timestamp;
    }
}

