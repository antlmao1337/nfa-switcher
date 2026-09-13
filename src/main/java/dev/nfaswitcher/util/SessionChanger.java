package dev.nfaswitcher.util;

import dev.nfaswitcher.NfaSwitcher;
import dev.nfaswitcher.account.Account;
import dev.nfaswitcher.account.AccountKind;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;

import java.util.Optional;
import java.util.UUID;

public final class SessionChanger {
	private SessionChanger() {
	}

	public static Session create(String username, String uuid, String token) {
		UUID id = uuid == null || uuid.isBlank()
				? Uuids.offline(username)
				: Uuids.parse(uuid);
		return new Session(username, id, token == null ? "" : token, Optional.empty(), Optional.empty());
	}

	public static void apply(Account account) {
		apply(account.username, account.uuid, account.accessToken);
		account.touch();
		if (NfaSwitcher.store != null) {
			NfaSwitcher.store.save();
		}
	}

	public static void apply(String username, String uuid, String token) {
		NfaSwitcher.currentSession = create(username, uuid, token);
		toast("Switched to " + username);
	}

	public static void applyOffline(String username) {
		String uuid = Uuids.offlineString(username);
		NfaSwitcher.currentSession = create(username, uuid, "");
		toast("Offline: " + username);
	}

	public static void restore() {
		if (NfaSwitcher.originalSession != null) {
			NfaSwitcher.currentSession = NfaSwitcher.originalSession;
			toast("Restored original session");
		}
	}

	public static String currentName() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.getSession() == null) {
			return "?";
		}
		return mc.getSession().getUsername();
	}

	public static void toast(String message) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null) {
			return;
		}
		mc.execute(() -> mc.getToastManager().add(new SystemToast(
				SystemToast.Type.PERIODIC_NOTIFICATION,
				Text.literal("NFA Switcher"),
				Text.literal(message)
		)));
	}

	public static Account currentAsAccount() {
		MinecraftClient mc = MinecraftClient.getInstance();
		Session s = mc.getSession();
		Account a = new Account();
		a.kind = s.getAccessToken() == null || s.getAccessToken().isBlank()
				? AccountKind.OFFLINE
				: AccountKind.SESSION;
		a.username = s.getUsername();
		UUID id = s.getUuidOrNull();
		a.uuid = id == null ? "" : id.toString();
		a.accessToken = s.getAccessToken();
		a.touch();
		return a;
	}
}
