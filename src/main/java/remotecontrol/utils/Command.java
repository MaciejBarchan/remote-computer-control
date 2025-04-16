package remotecontrol.utils;

import java.io.Serializable;

public class Command implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum CommandType {
        MOUSE_MOVE,
        MOUSE_PRESS,
        MOUSE_RELEASE,
        KEY_PRESS,
        KEY_RELEASE,
        SCREEN_CAPTURE,
        DISCONNECT
    }

    private CommandType type;
    private int x;
    private int y;
    private int button;
    private int keyCode;

    private Command(CommandType type) {
        this.type = type;
    }

    public static Command createMouseMoveCommand(int x, int y) {
        Command command = new Command(CommandType.MOUSE_MOVE);
        command.x = x;
        command.y = y;
        return command;
    }

    public static Command createMousePressCommand(int button) {
        Command command = new Command(CommandType.MOUSE_PRESS);
        command.button = button;
        return command;
    }

    public static Command createMouseReleaseCommand(int button) {
        Command command = new Command(CommandType.MOUSE_RELEASE);
        command.button = button;
        return command;
    }

    public static Command createKeyPressCommand(int keyCode) {
        Command command = new Command(CommandType.KEY_PRESS);
        command.keyCode = keyCode;
        return command;
    }

    public static Command createKeyReleaseCommand(int keyCode) {
        Command command = new Command(CommandType.KEY_RELEASE);
        command.keyCode = keyCode;
        return command;
    }

    public static Command createScreenCaptureCommand() {
        return new Command(CommandType.SCREEN_CAPTURE);
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
}
