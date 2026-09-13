package dev.nfaswitcher.screen;

import dev.nfaswitcher.NfaSwitcher;
import dev.nfaswitcher.auth.AuthException;
import dev.nfaswitcher.auth.TxtImporter;
import dev.nfaswitcher.util.FilePick;
import dev.nfaswitcher.util.LoginNotice;
import dev.nfaswitcher.util.SessionChanger;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class ImportTxtScreen extends Screen {
	private final Screen parent;
	private TextFieldWidget field;
	private TextFieldWidget pathField;
	private Text status = Text.literal("Cookies, refresh tokens, session JWTs â€” one file or paste").formatted(Formatting.GRAY);
	private boolean busy;

	public ImportTxtScreen(Screen parent) {
		super(Text.literal("Import TXT"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = this.height / 2 - 50;

		field = new TextFieldWidget(this.textRenderer, cx - 160, y, 320, 20, Text.literal("Dump"));
		field.setMaxLength(32767);
		field.setPlaceholder(Text.literal("paste dump here"));
		this.addSelectableChild(field);

		pathField = new TextFieldWidget(this.textRenderer, cx - 160, y + 28, 210, 20, Text.literal("Path"));
		pathField.setMaxLength(512);
		pathField.setPlaceholder(Text.literal("C:\\\\alts\\\\cookies.txt"));
		this.addSelectableChild(pathField);

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Browse"), b -> FilePick.pickTxt(path -> {
			pathField.setText(path.toString());
			try {
				field.setText(FilePick.read(path));
				status = Text.literal("Loaded " + path.getFileName()).formatted(Formatting.GRAY);
			} catch (Exception e) {
				status = Text.literal(e.getMessage()).formatted(Formatting.RED);
			}
		}, err -> status = Text.literal(err).formatted(Formatting.RED)))
				.dimensions(cx + 56, y + 28, 104, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Paste"), b ->
				field.setText(this.client.keyboard.getClipboard())
		).dimensions(cx - 160, y + 54, 100, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Import Folder"), b -> importFolder())
				.dimensions(cx - 54, y + 54, 104, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Import"), b -> importNow())
				.dimensions(cx + 56, y + 54, 104, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> this.client.setScreen(parent))
				.dimensions(cx - 100, y + 82, 200, 20).build());
	}

	private void importFolder() {
		Path dir = NfaSwitcher.store.importDir();
		try {
			Files.createDirectories(dir);
			List<String> blobs = new ArrayList<>();
			try (Stream<Path> stream = Files.list(dir)) {
				stream.filter(p -> {
					String n = p.getFileName().toString().toLowerCase();
					return n.endsWith(".txt") || n.endsWith(".json") || n.endsWith(".cookies");
				}).forEach(p -> {
					try {
						blobs.add(FilePick.read(p));
					} catch (Exception ignored) {
					}
				});
			}
			if (blobs.isEmpty()) {
				status = Text.literal("Drop .txt files into " + dir).formatted(Formatting.YELLOW);
				return;
			}
			field.setText(String.join("\n---\n", blobs));
			status = Text.literal("Loaded " + blobs.size() + " file(s) from import folder").formatted(Formatting.GRAY);
		} catch (Exception e) {
			status = Text.literal(e.getMessage()).formatted(Formatting.RED);
		}
	}

	private void importNow() {
		if (busy) {
			return;
		}
		String raw = field.getText();
		if ((raw == null || raw.isBlank()) && pathField.getText() != null && !pathField.getText().isBlank()) {
			try {
				raw = FilePick.read(Path.of(pathField.getText().trim()));
			} catch (Exception e) {
				status = Text.literal(e.getMessage()).formatted(Formatting.RED);
				return;
			}
		}
		if (raw == null || raw.isBlank()) {
			status = Text.literal("Nothing to import").formatted(Formatting.RED);
			return;
		}
		busy = true;
		status = Text.literal("Importing...").formatted(Formatting.YELLOW);
		String payload = raw;
		Thread t = new Thread(() -> {
			List<TxtImporter.Entry> entries = TxtImporter.parse(payload);
			int ok = 0;
			int fail = 0;
			String firstName = null;
			dev.nfaswitcher.account.Account firstAcc = null;
			AuthException lastFail = null;
			if (entries.isEmpty()) {
				lastFail = new AuthException("import", "PARSE", 0, "No cookies, refresh tokens, or session JWTs found in that file.");
			}
			for (TxtImporter.Entry e : entries) {
				try {
					var result = TxtImporter.login(e);
					var acc = result.toAccount();
					NfaSwitcher.store.add(acc);
					ok++;
					if (firstAcc == null) {
						firstAcc = acc;
						firstName = result.username;
					}
				} catch (AuthException ex) {
					fail++;
					lastFail = ex;
				} catch (Exception ex) {
					fail++;
					lastFail = new AuthException("import", "ERROR", 0, ex.getMessage());
				}
			}
			int fOk = ok;
			int fFail = fail;
			String fName = firstName;
			var apply = firstAcc;
			AuthException fLast = lastFail;
			this.client.execute(() -> {
				busy = false;
				if (fOk == 0) {
					AuthException err = fLast != null ? fLast
							: new AuthException("import", "FAILED", 0, "Import failed (" + fFail + ").");
					LoginNotice.fail(err);
					status = Text.literal(err.display()).formatted(Formatting.RED);
				} else {
					if (apply != null) {
						SessionChanger.apply(apply);
					}
					LoginNotice.ok(fName == null ? "imported" : fName);
					if (fFail > 0) {
						LoginNotice.detail = "Logged in as " + fName + "  (" + fOk + " ok, " + fFail + " failed)";
					}
					this.client.setScreen(new AccountManagerScreen(parent));
				}
			});
		}, "NfaImport");
		t.setDaemon(true);
		t.start();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.literal("Import .txt").formatted(Formatting.GOLD),
				this.width / 2, this.height / 2 - 74, 0xFFFFFF);
		context.drawCenteredTextWithShadow(this.textRenderer, status, this.width / 2, this.height / 2 + 58, 0xFFFFFF);
		field.render(context, mouseX, mouseY, delta);
		pathField.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
		if (field.keyPressed(input) || pathField.keyPressed(input)) {
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(net.minecraft.client.input.CharInput input) {
		if (field.charTyped(input) || pathField.charTyped(input)) {
			return true;
		}
		return super.charTyped(input);
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}
}
