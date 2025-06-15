package remotecontrol.utils;

import java.io.Serial;
import java.io.Serializable;

public class Command implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public enum CommandType {
        MOUSE_MOVE,
        MOUSE_PRESS,
        MOUSE_RELEASE,
        KEY_PRESS,
        KEY_RELEASE,
        SCREEN_CAPTURE,
        LATENCY_PING,
        LATENCY_PONG,
        DISCONNECT
    }

    private CommandType type;
    private int x;
    private int y;
    private int button;
    private int keyCode;
    private long timestamp;

    private Command(CommandType type) {
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

    private Command(CommandType type, int x, int y) {
        this(type);
        this.x = x;
        this.y = y;
    }

    private Command(CommandType type, int buttonOrKeyCode) {
        this(type);
        if (type == CommandType.KEY_PRESS || type == CommandType.KEY_RELEASE) {
            this.keyCode = buttonOrKeyCode;
        } else {
            this.button = buttonOrKeyCode;
        }
    }

    public static Command createMouseMoveCommand(int x, int y) {
        return new Command(CommandType.MOUSE_MOVE, x, y);
    }

    public static Command createMousePressCommand(int button) {
        return new Command(CommandType.MOUSE_PRESS, button);
    }

    public static Command createMouseReleaseCommand(int button) {
        return new Command(CommandType.MOUSE_RELEASE, button);
    }

    public static Command createKeyPressCommand(int keyCode) {
        return new Command(CommandType.KEY_PRESS, keyCode);
    }

    public static Command createKeyReleaseCommand(int keyCode) {
        return new Command(CommandType.KEY_RELEASE, keyCode);
    }

    public static Command createScreenCaptureCommand() {
        return new Command(CommandType.SCREEN_CAPTURE);
    }

    public static Command createLatencyPingCommand() {
        return new Command(CommandType.LATENCY_PING);
    }

    public static Command createLatencyPongCommand() {
        return new Command(CommandType.LATENCY_PONG);
    }

    public static Command createDisconnectCommand() {
        return new Command(CommandType.DISCONNECT);
    }

    public CommandType getType() {
        return type;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getButton() {
        return button;
    }

    public int getKeyCode() {
        return keyCode;
    }

    public long getTimestamp() {
        return timestamp;
    }
}