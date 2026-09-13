package dev.nfaswitcher.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class Uuids {
	private Uuids() {
	}

	public static String dash(String uuid) {
		if (uuid == null) {
			return "";
		}
		String n = uuid.replace("-", "");
		if (n.length() != 32) {
			return uuid;
		}
		return n.substring(0, 8) + "-" + n.substring(8, 12) + "-" + n.substring(12, 16)
				+ "-" + n.substring(16, 20) + "-" + n.substring(20);
	}

	public static UUID parse(String uuid) {
		return UUID.fromString(dash(uuid));
	}

	public static UUID offline(String name) {
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
	}

	public static String offlineString(String name) {
		return offline(name).toString();
	}
}
