package net.onelitefeather.coloeus.problem;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Every {@link ProblemCode} must have a section in docs/problems.md. Gradle runs tests in the backend module, so the
 * document is one level up.
 */
class ProblemCodeDocumentationTest {

    private static final Path PROBLEMS_DOC = Path.of("..", "docs", "problems.md");

    @Test
    void everyProblemCodeIsDocumented() throws IOException {
        String document = Files.readString(PROBLEMS_DOC);

        List<String> undocumented = Arrays.stream(ProblemCode.values())
                .map(ProblemCode::name)
                .filter(name -> !Pattern.compile("(?m)^## " + Pattern.quote(name) + "$").matcher(document).find())
                .toList();

        assertTrue(undocumented.isEmpty(),
                "docs/problems.md must have a '## <CODE>' section for every ProblemCode, missing: " + undocumented);
    }
}
