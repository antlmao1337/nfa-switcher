package dev.nfaswitcher.auth;

public final class AuthException extends Exception {
	public final String stage;
	public final String code;
	public final int httpStatus;

	public AuthException(String message) {
		this("auth", "ERROR", 0, message);
	}

	public AuthException(String message, Throwable cause) {
		this("auth", "ERROR", 0, message);
		initCause(cause);
	}

	public AuthException(String stage, String code, int httpStatus, String message) {
		super(message);
		this.stage = stage == null ? "auth" : stage;
		this.code = (code == null || code.isBlank()) ? "ERROR" : code;
		this.httpStatus = httpStatus;
	}

	public String display() {
		StringBuilder sb = new StringBuilder();
		sb.append('[').append(code).append(']');
		if (httpStatus > 0) {
			sb.append(" HTTP ").append(httpStatus);
		}
		sb.append(" @ ").append(stage);
		sb.append(" â€” ").append(getMessage() == null ? "unknown failure" : getMessage());
		return sb.toString();
	}
}
