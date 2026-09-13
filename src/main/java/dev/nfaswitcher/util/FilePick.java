package dev.nfaswitcher.util;

import net.minecraft.client.MinecraftClient;

import java.awt.FileDialog;
import java.awt.Frame;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class FilePick {
	private FilePick() {
	}

	public static void pickTxt(Consumer<Path> onPicked, Consumer<String> onError) {
		Thread t = new Thread(() -> {
			try {
				System.setProperty("java.awt.headless", "false");
				FileDialog dialog = new FileDialog((Frame) null, "Select .txt / cookie dump", FileDialog.LOAD);
				dialog.setFile("*.txt");
				dialog.setVisible(true);
				if (dialog.getFile() == null) {
					return;
				}
				Path path = Path.of(dialog.getDirectory(), dialog.getFile());
				MinecraftClient.getInstance().execute(() -> onPicked.accept(path));
			} catch (Throwable e) {
				MinecraftClient.getInstance().execute(() ->
						onError.accept(e.getMessage() == null ? e.toString() : e.getMessage()));
			}
		}, "NfaFilePick");
		t.setDaemon(true);
		t.start();
	}

	public static String read(Path path) throws Exception {
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
