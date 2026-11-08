package net.onelitefeather.coloeus.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Skips test classes tagged {@code integration} when no Docker daemon is reachable (for example on the macOS and
 * Windows CI runners), instead of failing them while their Postgres container starts. Registered for every test
 * through JUnit's extension auto-detection, so it is evaluated before the Micronaut extension builds a context.
 */
public final class DockerAvailableCondition implements ExecutionCondition {

    private static final String INTEGRATION = "integration";

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (!context.getTags().contains(INTEGRATION)) {
            return ConditionEvaluationResult.enabled("not an integration test");
        }
        if (DockerClientFactory.instance().isDockerAvailable()) {
            return ConditionEvaluationResult.enabled("Docker is available");
        }
        return ConditionEvaluationResult.disabled("Docker is not available for the integration test containers");
    }
}
