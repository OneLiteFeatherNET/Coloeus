package net.onelitefeather.coloeus.problem;

import io.micronaut.serde.annotation.Serdeable;

/**
 * RFC 9457 problem document with the stable {@code code} extension.
 */
@Serdeable
public record ProblemDetails(String type, String title, int status, String detail, String code) {

    public static ProblemDetails of(ProblemException exception) {
        return new ProblemDetails(
                "urn:coloeus:problem:" + exception.code().name(),
                exception.title(),
                exception.status().getCode(),
                exception.detail(),
                exception.code().name());
    }
}
