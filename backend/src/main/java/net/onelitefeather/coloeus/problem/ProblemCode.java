package net.onelitefeather.coloeus.problem;

import io.micronaut.http.HttpStatus;

/**
 * Stable machine-readable error codes. Every constant is documented in docs/problems.md (checked by a test).
 */
public enum ProblemCode {

    REQUEST_INVALID(HttpStatus.BAD_REQUEST, "Request is invalid"),
    AMOUNT_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "Amount is invalid"),
    SCALE_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Amount has too many fraction digits"),
    CURRENCY_UNKNOWN(HttpStatus.UNPROCESSABLE_ENTITY, "Currency is unknown"),
    CURRENCY_NOT_FOUND(HttpStatus.NOT_FOUND, "Currency not found"),
    CURRENCY_EXISTS(HttpStatus.CONFLICT, "Currency already exists"),
    CURRENCY_SCALE_IMMUTABLE(HttpStatus.CONFLICT, "Fraction digits of a currency cannot change"),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Account not found"),
    ACCOUNT_FORBIDDEN(HttpStatus.FORBIDDEN, "Account may not be used in this request"),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds"),
    UNBALANCED(HttpStatus.UNPROCESSABLE_ENTITY, "Postings do not sum to zero"),
    METADATA_TOO_LARGE(HttpStatus.UNPROCESSABLE_ENTITY, "Too many metadata entries"),
    IDEMPOTENCY_KEY_MISSING(HttpStatus.BAD_REQUEST, "Idempotency-Key header is missing"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "Idempotency key was used with another request"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    FORBIDDEN_ROLE(HttpStatus.FORBIDDEN, "Required role is missing"),
    FORBIDDEN_GRANT(HttpStatus.FORBIDDEN, "Client has no grant for this permission"),
    CLIENT_NOT_REGISTERED(HttpStatus.FORBIDDEN, "Client is not registered"),
    ACTOR_MISMATCH(HttpStatus.FORBIDDEN, "Actor is not the debited player"),
    CLIENT_EXISTS(HttpStatus.CONFLICT, "Client already exists"),
    CLIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Client not found"),
    PLAYER_UNKNOWN(HttpStatus.NOT_FOUND, "Player name is unknown"),
    NAME_LOOKUP_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Name lookup is unavailable"),
    NOTHING_TO_RESET(HttpStatus.CONFLICT, "Nothing to reset"),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

    private final HttpStatus status;
    private final String title;

    ProblemCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
