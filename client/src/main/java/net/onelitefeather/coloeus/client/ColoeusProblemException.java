package net.onelitefeather.coloeus.client;

/**
 * The server refused the call with an RFC 9457 problem document. Branch on {@link #code()}, never on the detail.
 */
public class ColoeusProblemException extends ColoeusException {

    private final String code;
    private final int status;
    private final String detail;

    public ColoeusProblemException(String code, int status, String detail) {
        super(code + " (HTTP " + status + "): " + detail);
        this.code = code;
        this.status = status;
        this.detail = detail;
    }

    /** @return the stable problem code, for example {@code CURRENCY_EXISTS} */
    public String code() {
        return code;
    }

    /** @return the HTTP status of the answer */
    public int status() {
        return status;
    }

    /** @return the explanation of this answer; not machine-readable */
    public String detail() {
        return detail;
    }
}
