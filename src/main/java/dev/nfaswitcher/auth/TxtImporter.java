package dev.nfaswitcher.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TxtImporter {
	private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
	private static final Pattern REFRESH = Pattern.compile("(?:M\\.[A-Za-z0-9._\\-]{40,}|[A-Za-z0-9._\\-]{80,})");

	private TxtImporter() {
	}

	public enum Kind {
		COOKIE, REFRESH, SESSION
	}

	public static final class Entry {
		public final Kind kind;
		public final String payload;
		public final String hint;

		public Entry(Kind kind, String payload, String hint) {
			this.kind = kind;
			this.payload = payload;
			this.hint = hint;
		}
	}

	public static List<Entry> parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return List.of();
		}
		String text = raw.trim();
		List<Entry> out = new ArrayList<>();

		if (CookieParser.looksNetscape(text) || text.trim().startsWith("[") || text.trim().startsWith("{")) {
			if (!CookieParser.parse(text).isEmpty()) {
				out.add(new Entry(Kind.COOKIE, text, "cookie dump"));
				pullRefreshAndJwt(text, out);
				return dedupe(out);
			}
		}

		if (looksLikeCookieHeader(text) && text.contains("=") && !text.contains("\n")) {
			out.add(new Entry(Kind.COOKIE, text, "cookie header"));
			return out;
		}

		String[] blocks = text.split("(?m)^\\s*-{3,}\\s*$");
		if (blocks.length == 1) {
			blocks = text.split("\\R\\s*\\R");
		}
		for (String block : blocks) {
			parseBlock(block.trim(), out);
		}
		if (out.isEmpty()) {
			parseLines(text, out);
		}
		return dedupe(out);
	}

	private static void parseBlock(String block, List<Entry> out) {
		if (block.isEmpty()) {
			return;
		}
		if (CookieParser.looksNetscape(block) || looksLikeCookieHeader(block)) {
			if (!CookieParser.parse(block).isEmpty()) {
				out.add(new Entry(Kind.COOKIE, block, "cookie block"));
				return;
			}
		}
		parseLines(block, out);
	}

	private static void parseLines(String text, List<Entry> out) {
		for (String line : text.split("\\R")) {
			String trimmed = stripPrefix(line.trim());
			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				continue;
			}
			String lower = trimmed.toLowerCase(Locale.ROOT);

			if (lower.startsWith("cookie:") || lower.contains("mspauth=") || lower.contains("wlssid=")) {
				out.add(new Entry(Kind.COOKIE, trimmed, "cookie line"));
				continue;
			}

			Matcher jwt = JWT.matcher(trimmed);
			if (jwt.find()) {
				out.add(new Entry(Kind.SESSION, jwt.group(), "session token"));
				continue;
			}

			String refresh = extractRefresh(trimmed);
			if (refresh != null) {
				out.add(new Entry(Kind.REFRESH, refresh, hintFromLine(trimmed)));
			}
		}
	}

	private static void pullRefreshAndJwt(String text, List<Entry> out) {
		Matcher jwt = JWT.matcher(text);
		while (jwt.find()) {
			out.add(new Entry(Kind.SESSION, jwt.group(), "embedded session"));
		}
		for (String line : text.split("\\R")) {
			String refresh = extractRefresh(stripPrefix(line));
			if (refresh != null && refresh.startsWith("M.")) {
				out.add(new Entry(Kind.REFRESH, refresh, "embedded refresh"));
			}
		}
	}

	private static String extractRefresh(String line) {
		if (line == null || line.isBlank()) {
			return null;
		}
		String trimmed = line.trim();
		String lower = trimmed.toLowerCase(Locale.ROOT);
		if (lower.startsWith("refresh_token:") || lower.startsWith("refreshtoken:")) {
			return trimmed.substring(trimmed.indexOf(':') + 1).trim();
		}
		String[] cols = trimmed.split("[:;|,\\t]");
		for (int i = cols.length - 1; i >= 0; i--) {
			String part = cols[i].trim();
			if (isRefresh(part)) {
				return part;
			}
		}
		if (isRefresh(trimmed)) {
			return trimmed;
		}
		Matcher m = REFRESH.matcher(trimmed);
		if (m.find()) {
			String g = m.group();
			if (isRefresh(g)) {
				return g;
			}
		}
		return null;
	}

	private static boolean isRefresh(String s) {
		if (s == null) {
			return false;
		}
		if (s.startsWith("eyJ")) {
			return false;
		}
		if (s.startsWith("M.") && s.length() > 30) {
			return true;
		}
		if (s.startsWith("0.") && s.length() > 40) {
			return true;
		}
		return s.length() >= 70 && s.matches("[A-Za-z0-9._\\-+=/*]+");
	}

	private static boolean looksLikeCookieHeader(String text) {
		String l = text.toLowerCase(Locale.ROOT);
		return l.contains("mspauth=") || l.contains("mspprof=") || l.contains("wlssid=")
				|| l.contains("__host-msaauth") || l.contains("cookie:");
	}

	private static String stripPrefix(String line) {
		String s = line.trim();
		if (s.regionMatches(true, 0, "refresh_token", 0, 13)
				|| s.regionMatches(true, 0, "mctoken", 0, 7)
				|| s.regionMatches(true, 0, "access_token", 0, 12)
				|| s.regionMatches(true, 0, "session", 0, 7)
				|| s.regionMatches(true, 0, "token", 0, 5)) {
			int c = s.indexOf(':');
			if (c > 0 && c < 20) {
				return s.substring(c + 1).trim();
			}
			int eq = s.indexOf('=');
			if (eq > 0 && eq < 20) {
				return s.substring(eq + 1).trim();
			}
		}
		return s;
	}

	private static String hintFromLine(String line) {
		int colon = line.indexOf(':');
		if (colon > 0 && colon < 80 && line.substring(0, colon).contains("@")) {
			return line.substring(0, colon);
		}
		return "refresh token";
	}

	private static List<Entry> dedupe(List<Entry> in) {
		List<Entry> out = new ArrayList<>();
		for (Entry e : in) {
			boolean seen = false;
			for (Entry o : out) {
				if (o.kind == e.kind && o.payload.equals(e.payload)) {
					seen = true;
					break;
				}
			}
			if (!seen) {
				out.add(e);
			}
		}
		return out;
	}

	public static AuthResult login(Entry entry) throws AuthException {
		return switch (entry.kind) {
			case COOKIE -> MicrosoftAuth.loginCookies(entry.payload);
			case REFRESH -> MicrosoftAuth.loginRefresh(entry.payload);
			case SESSION -> MicrosoftAuth.loginSessionToken(entry.payload);
		};
	}
}
