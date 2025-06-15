package remotecontrol.utils;

import remotecontrol.service.MessageService;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ScriptExecutor {
    private static final String SIGNAL_FILE_NAME = "screenshot.signal";
    private static final String SCRIPTS_FOLDER = "scripts";
    private ObjectOutputStream clientOutput;

    public ScriptExecutor(ObjectOutputStream clientOutput) {
        this.clientOutput = clientOutput;
    }

    public void executeScript(String pathToScript, MessageService msgService) throws IOException {
        if (!isValidScriptPath(pathToScript)) {
            msgService.sendMessage("Invalid script path");
            return;
        }

        Path scriptsDir = Paths.get(System.getProperty("user.dir"), SCRIPTS_FOLDER);
        File batchFile = new File(scriptsDir.toFile(), pathToScript);

        if (!batchFile.exists()) {
            msgService.sendMessage("Script file not found");
            return;
        }

        File signalFile = new File(SIGNAL_FILE_NAME);
        if (signalFile.exists()) {
            signalFile.delete();
        }

        try {
            Thread scrWatcher = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    if (signalFile.exists()) {
                        try {
                            ScreenshotUtil.sendScreenshotToClient(clientOutput);
                            Log.addLog("Screenshot was taken and sent to the client.", Log.TypeMessage.INFO);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        signalFile.delete();
                        break;
                    }
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            scrWatcher.start();
            Log.addLog("Script was run remotely", Log.TypeMessage.INFO);
            ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", "start", "cmd", "/k", "call", batchFile.getAbsolutePath());
            processBuilder.directory(new File(System.getProperty("user.dir")));
            processBuilder.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isValidScriptPath(String pathToScript) {
        if (pathToScript == null || pathToScript.trim().isEmpty()) {
            return false;
        }

        if (!pathToScript.toLowerCase().endsWith(".bat")) {
            return false;
        }

        try {
            Path scriptPath = Paths.get(pathToScript);
            return !scriptPath.isAbsolute() && !pathToScript.contains("..");
        } catch (Exception e) {
            return false;
        }
    }
}