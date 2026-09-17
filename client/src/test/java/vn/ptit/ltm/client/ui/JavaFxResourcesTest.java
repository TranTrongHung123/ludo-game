package vn.ptit.ltm.client.ui;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

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

        try (InputStream stylesheet = resource("/css/application.css")) {
            assertTrue(stylesheet.readAllBytes().length > 0);
        }
    }

    private static void assertWellFormed(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (InputStream resource = resource(path)) {
            assertNotNull(factory.newDocumentBuilder().parse(resource).getDocumentElement());
        }
    }

    private static InputStream resource(String path) {
        InputStream resource = JavaFxResourcesTest.class.getResourceAsStream(path);
        assertNotNull(resource, "Missing resource " + path);
        return resource;
    }
}
