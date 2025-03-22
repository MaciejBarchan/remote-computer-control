package remotecontrol.utils;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.ScrollPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Log {
    private static TextFlow textFlow = new TextFlow();
    private static ScrollPane scrollPane = new ScrollPane();
    public enum TypeMessage{
        ERROR,
        WARNING,
        INFO
    }

    public static void setControls(TextFlow textFlow, ScrollPane scrollPane) {
        Log.textFlow = textFlow;
        Log.scrollPane = scrollPane;
    }

    public static void addLog(String details, TypeMessage type) {
        if (textFlow == null) {
            throw new IllegalStateException("TextFlow nie został ustawiony.");
        }

        String  message;
        Text text;
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String time = now.format(formatter);

        switch (type) {
            case ERROR:
                message = time + " [ERROR] " + details;
                text = new Text(message + "\n");
                text.setFill(Color.RED);
                Platform.runLater(() -> textFlow.getChildren().add(text));
                break;
            case WARNING:
                message = time + " [WARNING] " + details;
                text = new Text(message + "\n");
                text.setFill(Color.YELLOW);
                Platform.runLater(() -> textFlow.getChildren().add(text));                break;
            case INFO:
                message = time + " [INFO] " + details;
                text = new Text(message + "\n");
                text.setFill(Color.GREEN);
                Platform.runLater(() -> textFlow.getChildren().add(text));        }
        //Platform.runLater(() -> scrollPane.setVvalue(1.0));

        PauseTransition pause = new PauseTransition(Duration.millis(50)); // Opóźnienie 50 ms
        pause.setOnFinished(event -> {
            // Przewijanie ScrollPane na dół po zakończeniu opóźnienia
            Platform.runLater(() ->
                    scrollPane.setVvalue(1.0));
        });
        pause.play(); // Rozpocznij opóźnienie
    }
}
