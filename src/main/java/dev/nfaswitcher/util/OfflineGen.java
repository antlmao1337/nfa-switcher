package dev.nfaswitcher.util;

import dev.nfaswitcher.NfaSwitcher;
import dev.nfaswitcher.account.Account;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class OfflineGen {
	private static final SecureRandom RNG = new SecureRandom();
	private static final char[] ALPH = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

	private OfflineGen() {
	}

	public static List<Account> generate(String prefix, int count) {
		String base = sanitize(prefix);
		List<Account> out = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			String name = uniqueName(base);
			out.add(Account.offline(name, Uuids.offlineString(name)));
		}
		return out;
	}

	private static String uniqueName(String base) {
		for (int attempt = 0; attempt < 64; attempt++) {
			String suffix = random(Math.max(3, 16 - base.length()));
			String name = (base + suffix);
			if (name.length() > 16) {
				name = name.substring(0, 16);
			}
			if (name.length() < 3) {
				name = (name + random(3)).substring(0, Math.max(3, name.length()));
			}
			if (!exists(name)) {
				return name;
			}
		}
		return (base + Integer.toHexString(RNG.nextInt(0xFFFFF))).substring(0, Math.min(16, base.length() + 5));
	}

	private static boolean exists(String name) {
		if (NfaSwitcher.store == null) {
			return false;
		}
		for (Account a : NfaSwitcher.store.accounts()) {
			if (a.username.equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

	private static String sanitize(String prefix) {
		if (prefix == null || prefix.isBlank()) {
			return "Player";
		}
		StringBuilder sb = new StringBuilder();
		for (char c : prefix.trim().toCharArray()) {
			if (Character.isLetterOrDigit(c) || c == '_') {
				sb.append(c);
			}
		}
		String s = sb.toString();
		if (s.isEmpty()) {
			return "Player";
		}
		if (s.length() > 13) {
			s = s.substring(0, 13);
		}
		return s;
	}

	private static String random(int n) {
		int len = Math.max(1, Math.min(8, n));
		char[] buf = new char[len];
		for (int i = 0; i < len; i++) {
			buf[i] = ALPH[RNG.nextInt(ALPH.length)];
		}
		return new String(buf);
	}

	public static String randomName() {
		return uniqueName("Player").toLowerCase(Locale.ROOT);
	}
}
