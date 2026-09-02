package com.github.manolo8.darkbot.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StartupParamsTest {

    @Test
    void readsDirectGameSessionProperties(@TempDir Path temp) throws IOException {
        Path file = temp.resolve("login.properties");
        Files.write(file, (
                "gameSid=raw-sid\n"
                        + "server=es2\n"
                        + "userId=74357986\n"
                        + "instance=89\n"
                        + "miniClient=1\n"
                        + "mapId=12\n"
                        + "traceOutbound=1\n"
                        + "diagnosticMove=1\n"
                        + "diagnosticMoveDistance=250\n").getBytes(StandardCharsets.UTF_8));

        StartupParams.AutoLoginProps props =
                new StartupParams(new String[]{"-login", file.toString()}).getAutoLoginProps();

        assertEquals("raw-sid", props.getGameSID());
        assertEquals("74357986", props.getUserId());
        assertEquals("89", props.getInstance());
        assertEquals("1", props.getMiniClient());
        assertEquals("12", props.getMapId());
        assertEquals("1", props.getTraceOutbound());
        assertEquals("1", props.getDiagnosticMove());
        assertEquals("250", props.getDiagnosticMoveDistance());
    }
}
