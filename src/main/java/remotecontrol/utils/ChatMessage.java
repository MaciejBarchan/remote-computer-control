package remotecontrol.utils;

import java.io.Serializable;

public class ChatMessage implements Serializable {
    private final String content;

    public ChatMessage(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }
}

