package dev.nfaswitcher.account;

import java.util.UUID;

public final class Account {
	public String id = UUID.randomUUID().toString();
	public AccountKind kind = AccountKind.OFFLINE;
	public String username = "Unknown";
	public String uuid = "";
	public String accessToken = "";
	public String refreshToken = "";
	public String cookies = "";
	public String authClient = "xbox";
	public long lastUsed;
	public long createdAt = System.currentTimeMillis();
	public String note = "";

	public Account() {
	}

	public static Account offline(String username, String uuid) {
		Account a = new Account();
		a.kind = AccountKind.OFFLINE;
		a.username = username;
		a.uuid = uuid;
		a.accessToken = "";
		return a;
	}

	public String displayName() {
		return username == null || username.isBlank() ? "Unknown" : username;
	}

	public void touch() {
		this.lastUsed = System.currentTimeMillis();
	}

	public boolean isOnline() {
		return kind != AccountKind.OFFLINE;
	}
}
