package vn.ptit.ltm.client.ui;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaFxResourcesTest {
    @Test
    void authenticationFxmlAndStylesheetResourcesArePackagedAndWellFormed() throws Exception {
        assertWellFormed("/fxml/login.fxml");
        assertWellFormed("/fxml/register.fxml");
        assertWellFormed("/fxml/authenticated.fxml");
        assertWellFormed("/fxml/lobby.fxml");
        assertWellFormed("/fxml/room.fxml");
        assertWellFormed("/fxml/game.fxml");
        assertWellFormed("/fxml/ranking.fxml");
        assertWellFormed("/fxml/match-history.fxml");
        assertWellFormed("/fxml/game-result.fxml");

        try (InputStream stylesheet = resource("/css/application.css")) {
            assertTrue(stylesheet.readAllBytes().length > 0);
        }
        try (InputStream loginBackground = resource("/images/login.png")) {
            assertTrue(loginBackground.readAllBytes().length > 0);
        }
    }

    @Test
    void rankingHistoryAndResultResourcesExposeTheirPrimaryActions() throws Exception {
        assertResourceContains("/fxml/lobby.fxml", "#handleShowRanking", "#handleShowMatchHistory");
        assertResourceContains("/fxml/ranking.fxml", "fx:id=\"rankingList\"", "#handleRefresh");
        assertResourceContains("/fxml/match-history.fxml", "fx:id=\"historyList\"", "#handleRefresh");
        assertResourceContains("/fxml/game-result.fxml", "fx:id=\"standingsList\"", "#handleReturnToLobby");
    }

    @Test
    void gameBoardResourcesDeclareBoardAndStateDrivenStyles() throws Exception {
        String gameFxml;
        try (InputStream resource = resource("/fxml/game.fxml")) {
            gameFxml = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(gameFxml.contains("fx:id=\"boardPane\""));
        assertTrue(gameFxml.contains("fx:id=\"piecesList\""));
        assertTrue(gameFxml.contains("fx:id=\"leaveGameButton\""));
        assertTrue(gameFxml.contains("onAction=\"#handleLeaveGame\""));
        assertTrue(gameFxml.contains("legend-speed"));

        String stylesheet;
        try (InputStream resource = resource("/css/application.css")) {
            stylesheet = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(stylesheet.contains(".track-cell"));
        assertTrue(stylesheet.contains(".finish-cell"));
        assertTrue(stylesheet.contains(".board-piece"));
        assertTrue(stylesheet.contains(".piece-valid"));
        assertTrue(stylesheet.contains(".special-shield"));
    }

    private static void assertWellFormed(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (InputStream resource = resource(path)) {
            assertNotNull(factory.newDocumentBuilder().parse(resource).getDocumentElement());
        }
    }

    private static void assertResourceContains(String path, String... expectedValues) throws Exception {
        String content;
        try (InputStream input = resource(path)) {
            content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String expected : expectedValues) {
            assertTrue(content.contains(expected), () -> path + " does not contain " + expected);
        }
    }

    private static InputStream resource(String path) {
        InputStream resource = JavaFxResourcesTest.class.getResourceAsStream(path);
        assertNotNull(resource, "Missing resource " + path);
        return resource;
    }
}
