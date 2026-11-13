package ph.uc.cpe314.dinesmart.service;

import java.util.Objects;

/** Explicit success, empty, and failure result for operations exposed to controllers. */
public record ApiResult<T>(Status status, T data, String message) {
    public enum Status {
        OK,
        EMPTY,
        FAILED
    }

    public ApiResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(data, "data");
        message = message == null ? "" : message;
    }

    public boolean isSuccessful() {
        return status == Status.OK || status == Status.EMPTY;
    }
}
