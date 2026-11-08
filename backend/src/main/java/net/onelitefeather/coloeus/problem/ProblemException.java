package net.onelitefeather.coloeus.problem;

import io.micronaut.http.HttpStatus;

/**
 * A request the service rejects on purpose. Rendered as RFC 9457 Problem Details by {@link ProblemExceptionHandler}.
 */
public class ProblemException extends RuntimeException {

    private final ProblemCode code;
    private final HttpStatus status;
    private final String title;
    private final String detail;

    public ProblemException(ProblemCode code, HttpStatus status, String title, String detail) {
        super(code.name() + ": " + detail);
        this.code = code;
        this.status = status;
        this.title = title;
        this.detail = detail;
    }

    /** Problem with the status and title that belong to the code. */
    public static ProblemException of(ProblemCode code, String detail) {
        return new ProblemException(code, code.status(), code.title(), detail);
    }

    public ProblemCode code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String detail() {
        return detail;
    }
}
