package dev.nfaswitcher.util;

import dev.nfaswitcher.auth.AuthException;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class LoginNotice {
	public static final int TEXT = 0xFFFFFFFF;
	public static final int TEXT_DIM = 0xFFE5E5E5;

	public static volatile boolean success;
	public static volatile String title = "";
	public static volatile String detail = "";
	public static volatile String code = "";
	public static volatile String accountName = "";
	public static volatile long hideAt;

	private LoginNotice() {
	}

	public static void ok(String username) {
		success = true;
		accountName = username == null ? "" : username;
		code = "OK";
		title = "Logged in as " + accountName;
		detail = "Session switched. Reconnect to a server to apply it.";
		hideAt = System.currentTimeMillis() + 12_000L;
		SessionChanger.toast("Logged in as " + accountName);
	}

	public static void fail(AuthException e) {
		fail(e.code, e.display());
	}

	public static void fail(String errorCode, String why) {
		success = false;
		accountName = "";
		code = errorCode == null ? "ERROR" : errorCode;
		title = "Login failed  [" + code + "]";
		detail = why == null ? "Unknown error" : why;
		hideAt = System.currentTimeMillis() + 25_000L;
		String toast = title.length() > 60 ? title.substring(0, 57) + "..." : title;
		SessionChanger.toast(toast);
	}

	public static boolean visible() {
		return title != null && !title.isBlank() && System.currentTimeMillis() < hideAt;
	}

	public static int height(TextRenderer tr, int screenWidth) {
		if (!visible()) {
			return 0;
		}
		int boxW = Math.min(460, screenWidth - 24);
		return 26 + wrap(tr, detail, boxW - 20).size() * 11;
	}

	public static int draw(DrawContext context, TextRenderer tr, int screenWidth, int y) {
		if (!visible()) {
			return 0;
		}
		int boxW = Math.min(460, screenWidth - 24);
		int x = (screenWidth - boxW) / 2;
		List<String> lines = wrap(tr, detail, boxW - 20);
		int h = 26 + lines.size() * 11;
		int bg = success ? 0xF0123B24 : 0xF05C1212;
		int accent = success ? 0xFF4ADE80 : 0xFFF87171;
		context.fill(x, y, x + boxW, y + h, bg);
		context.fill(x, y, x + 3, y + h, accent);
		context.drawCenteredTextWithShadow(tr, Text.literal(title), screenWidth / 2, y + 5, TEXT);
		int ly = y + 18;
		for (String line : lines) {
			context.drawCenteredTextWithShadow(tr, Text.literal(line), screenWidth / 2, ly, TEXT_DIM);
			ly += 11;
		}
		return h;
	}

	private static List<String> wrap(TextRenderer tr, String text, int width) {
		List<String> out = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return out;
		}
		String[] words = text.replace('\n', ' ').split(" ");
		StringBuilder line = new StringBuilder();
		for (String word : words) {
			if (word.isEmpty()) {
				continue;
			}
			String trial = line.isEmpty() ? word : line + " " + word;
			if (tr.getWidth(trial) > width && !line.isEmpty()) {
				out.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(trial);
			}
		}
		if (!line.isEmpty()) {
			out.add(line.toString());
		}
		return out;
	}
}
