package dev.nfaswitcher.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CookieParser {
	private CookieParser() {
	}

	public static List<Cookie> parse(String raw) {
		if (raw == null) {
			return List.of();
		}
		String text = raw.trim();
		if (text.isEmpty()) {
			return List.of();
		}
		if (text.startsWith("[") || text.startsWith("{")) {
			List<Cookie> json = parseJson(text);
			if (!json.isEmpty()) {
				return json;
			}
		}
		if (looksNetscape(text)) {
			return parseNetscape(text);
		}
		return parseHeader(text);
	}

	public static boolean looksNetscape(String text) {
		String t = text.trim();
		if (t.contains("# Netscape") || t.contains("# HTTP Cookie File")) {
			return true;
		}
		for (String line : t.split("\\R")) {
			if (line.startsWith("#") || line.isBlank()) {
				continue;
			}
			String[] parts = line.split("\\t");
			if (parts.length >= 7 && parts[0].contains(".")) {
				return true;
			}
			break;
		}
		return false;
	}

	public static List<Cookie> parseNetscape(String text) {
		List<Cookie> out = new ArrayList<>();
		for (String line : text.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				if (trimmed.startsWith("#HttpOnly_")) {
					trimmed = trimmed.substring("#HttpOnly_".length());
				} else {
					continue;
				}
			}
			String[] parts = trimmed.split("\\t");
			if (parts.length < 7) {
				parts = trimmed.split("\\s+");
			}
			if (parts.length < 7) {
				continue;
			}
			out.add(new Cookie(
					parts[5],
					joinFrom(parts, 6),
					parts[0],
					parts[2],
					"TRUE".equalsIgnoreCase(parts[3])
			));
		}
		return out;
	}

	public static List<Cookie> parseHeader(String text) {
		String header = text;
		int idx = header.toLowerCase(Locale.ROOT).indexOf("cookie:");
		if (idx >= 0) {
			header = header.substring(idx + "cookie:".length());
		}
		List<Cookie> out = new ArrayList<>();
		for (String piece : header.split("[\\n\\r;]+")) {
			String p = piece.trim();
			if (p.isEmpty() || p.startsWith("#")) {
				continue;
			}
			int eq = p.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			String name = p.substring(0, eq).trim();
			String value = p.substring(eq + 1).trim();
			if (name.isEmpty() || value.isEmpty()) {
				continue;
			}
			if (name.equalsIgnoreCase("expires") || name.equalsIgnoreCase("path")
					|| name.equalsIgnoreCase("domain") || name.equalsIgnoreCase("max-age")
					|| name.equalsIgnoreCase("samesite") || name.equalsIgnoreCase("httponly")
					|| name.equalsIgnoreCase("secure")) {
				continue;
			}
			out.add(new Cookie(name, value, ".login.live.com", "/", true));
		}
		return out;
	}

	private static List<Cookie> parseJson(String text) {
		try {
			JsonElement el = JsonParser.parseString(text);
			List<Cookie> out = new ArrayList<>();
			if (el.isJsonArray()) {
				for (JsonElement item : el.getAsJsonArray()) {
					Cookie c = fromJsonObj(item);
					if (c != null) {
						out.add(c);
					}
				}
			} else if (el.isJsonObject()) {
				JsonObject obj = el.getAsJsonObject();
				if (obj.has("cookies") && obj.get("cookies").isJsonArray()) {
					JsonArray arr = obj.getAsJsonArray("cookies");
					for (JsonElement item : arr) {
						Cookie c = fromJsonObj(item);
						if (c != null) {
							out.add(c);
						}
					}
				} else {
					Cookie c = fromJsonObj(obj);
					if (c != null) {
						out.add(c);
					}
				}
			}
			return out;
		} catch (Exception e) {
			return List.of();
		}
	}

	private static Cookie fromJsonObj(JsonElement el) {
		if (el == null || !el.isJsonObject()) {
			return null;
		}
		JsonObject o = el.getAsJsonObject();
		String name = first(o, "name", "Name");
		String value = first(o, "value", "Value");
		if (name == null || value == null) {
			return null;
		}
		String domain = first(o, "domain", "Domain");
		String path = first(o, "path", "Path");
		boolean secure = o.has("secure") && o.get("secure").getAsBoolean();
		return new Cookie(name, value, domain == null ? ".login.live.com" : domain, path == null ? "/" : path, secure);
	}

	private static String first(JsonObject o, String... keys) {
		for (String k : keys) {
			if (o.has(k) && !o.get(k).isJsonNull()) {
				return o.get(k).getAsString();
			}
		}
		return null;
	}

	public static String toHeader(List<Cookie> cookies) {
		Map<String, String> map = new LinkedHashMap<>();
		for (Cookie c : cookies) {
			map.put(c.name, c.value);
		}
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> e : map.entrySet()) {
			if (!sb.isEmpty()) {
				sb.append("; ");
			}
			sb.append(e.getKey()).append('=').append(e.getValue());
		}
		return sb.toString();
	}

	private static String joinFrom(String[] parts, int start) {
		StringBuilder sb = new StringBuilder();
		for (int i = start; i < parts.length; i++) {
			if (i > start) {
				sb.append('\t');
			}
			sb.append(parts[i]);
		}
		return sb.toString();
	}

	public static final class Cookie {
		public final String name;
		public final String value;
		public final String domain;
		public final String path;
		public final boolean secure;

		public Cookie(String name, String value, String domain, String path, boolean secure) {
			this.name = name;
			this.value = value;
			this.domain = domain;
			this.path = path;
			this.secure = secure;
		}
	}
}
