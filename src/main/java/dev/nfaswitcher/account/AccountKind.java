package dev.nfaswitcher.account;

public enum AccountKind {
	MICROSOFT,
	COOKIE,
	REFRESH,
	SESSION,
	OFFLINE;

	public String badge() {
		return switch (this) {
			case MICROSOFT -> "MSA";
			case COOKIE -> "COOKIE";
			case REFRESH -> "REFRESH";
			case SESSION -> "TOKEN";
			case OFFLINE -> "OFFLINE";
		};
	}
}
