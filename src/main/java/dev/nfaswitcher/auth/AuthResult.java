package dev.nfaswitcher.auth;

import dev.nfaswitcher.account.Account;
import dev.nfaswitcher.account.AccountKind;

public final class AuthResult {
	public final String username;
	public final String uuid;
	public final String accessToken;
	public final String refreshToken;
	public final String cookies;
	public final String authClient;
	public final AccountKind kind;

	public AuthResult(String username, String uuid, String accessToken, String refreshToken,
					  String cookies, String authClient, AccountKind kind) {
		this.username = username;
		this.uuid = uuid;
		this.accessToken = accessToken == null ? "" : accessToken;
		this.refreshToken = refreshToken == null ? "" : refreshToken;
		this.cookies = cookies == null ? "" : cookies;
		this.authClient = authClient == null ? "xbox" : authClient;
		this.kind = kind;
	}

	public Account toAccount() {
		Account a = new Account();
		a.kind = kind;
		a.username = username;
		a.uuid = uuid;
		a.accessToken = accessToken;
		a.refreshToken = refreshToken;
		a.cookies = cookies;
		a.authClient = authClient;
		a.touch();
		return a;
	}
}
