package net.onelitefeather.coloeus.auth;

import io.micronaut.security.utils.SecurityService;
import jakarta.inject.Singleton;
import net.onelitefeather.coloeus.problem.ProblemCode;
import net.onelitefeather.coloeus.problem.ProblemException;

/**
 * Answers who calls the current request, from the verified token of the request.
 */
@Singleton
public class CurrentCaller {

    private final SecurityService security;

    public CurrentCaller(SecurityService security) {
        this.security = security;
    }

    /**
     * @throws ProblemException {@link ProblemCode#UNAUTHORIZED} when the request carries no verified token
     */
    public Caller get() {
        return security.getAuthentication()
                .map(Caller::from)
                .orElseThrow(() -> ProblemException.of(ProblemCode.UNAUTHORIZED, "no verified token on the request"));
    }
}
