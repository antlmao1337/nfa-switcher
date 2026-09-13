package dev.nfaswitcher.screen;

import dev.nfaswitcher.NfaSwitcher;
import dev.nfaswitcher.auth.AuthException;
import dev.nfaswitcher.auth.AuthResult;
import dev.nfaswitcher.auth.MicrosoftAuth;
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

public class AddRefreshScreen extends Screen {
	private final Screen parent;
	private TextFieldWidget field;
	private Text status = Text.literal("Paste one refresh token, or many (one per line)").formatted(Formatting.GRAY);
	private boolean busy;

	public AddRefreshScreen(Screen parent) {
		super(Text.literal("Refresh Token"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = this.height / 2 - 50;
		field = new TextFieldWidget(this.textRenderer, cx - 160, y, 320, 20, Text.literal("Refresh token"));
		field.setMaxLength(32767);
		field.setPlaceholder(Text.literal("M.R3_BAY... / 0.AX... / eyJ..."));
		this.addSelectableChild(field);

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Paste"), b -> {
			field.setText(this.client.keyboard.getClipboard());
			status = Text.literal("Pasted clipboard").formatted(Formatting.GRAY);
		}).dimensions(cx - 160, y + 28, 100, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Load .txt"), b -> FilePick.pickTxt(path -> {
			try {
				field.setText(FilePick.read(path));
				status = Text.literal("Loaded " + path.getFileName()).formatted(Formatting.GRAY);
			} catch (Exception e) {
				status = Text.literal(e.getMessage()).formatted(Formatting.RED);
			}
		}, err -> status = Text.literal(err).formatted(Formatting.RED)))
				.dimensions(cx - 54, y + 28, 100, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Login"), b -> login())
				.dimensions(cx + 52, y + 28, 108, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> this.client.setScreen(parent))
				.dimensions(cx - 100, y + 56, 200, 20).build());
	}

	private void login() {
		if (busy) {
			return;
		}
		String raw = field.getText();
		if (raw == null || raw.isBlank()) {
			fail(new AuthException("refresh", "EMPTY", 0, "Nothing pasted."));
			return;
		}
		busy = true;
		status = Text.literal("Exchanging refresh token with Microsoft...").formatted(Formatting.YELLOW);
		Thread t = new Thread(() -> {
			try {
				String trimmed = raw.trim();
				boolean bulk = trimmed.contains("\n") || trimmed.contains("\r");
				if (!bulk) {
					AuthResult result = MicrosoftAuth.loginAny(trimmed);
					this.client.execute(() -> succeed(result, 1));
					return;
				}
				var entries = TxtImporter.parse(trimmed);
				if (entries.isEmpty()) {
					AuthResult result = MicrosoftAuth.loginAny(trimmed);
					this.client.execute(() -> succeed(result, 1));
					return;
				}
				int ok = 0;
				AuthException lastFail = null;
				AuthResult first = null;
				for (var e : entries) {
					try {
						AuthResult result = TxtImporter.login(e);
						NfaSwitcher.store.add(result.toAccount());
						if (first == null) {
							first = result;
						}
						ok++;
					} catch (AuthException ex) {
						lastFail = ex;
					} catch (Exception ex) {
						lastFail = new AuthException("refresh", "ERROR", 0, ex.getMessage());
					}
				}
				int fOk = ok;
				AuthResult fFirst = first;
				AuthException fFail = lastFail;
				this.client.execute(() -> {
					if (fOk == 0) {
						fail(fFail != null ? fFail : new AuthException("refresh", "NO_TOKENS", 0, "No tokens logged in."));
					} else {
						succeed(fFirst, fOk);
					}
				});
			} catch (AuthException e) {
				this.client.execute(() -> fail(e));
			} catch (Exception e) {
				this.client.execute(() -> fail(new AuthException("refresh", "CRASH", 0,
						e.getMessage() == null ? e.toString() : e.getMessage())));
			}
		}, "NfaRefreshLogin");
		t.setDaemon(true);
		t.start();
	}

	private void succeed(AuthResult result, int count) {
		busy = false;
		NfaSwitcher.store.add(result.toAccount());
		SessionChanger.apply(result.toAccount());
		LoginNotice.ok(result.username);
		if (count > 1) {
			LoginNotice.detail = "Logged in as " + result.username + "  (+" + (count - 1) + " more)";
		}
		this.client.setScreen(new AccountManagerScreen(parent));
	}

	private void fail(AuthException e) {
		busy = false;
		LoginNotice.fail(e);
		status = Text.literal(e.display()).formatted(Formatting.RED);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.literal("Refresh Token").formatted(Formatting.AQUA),
				this.width / 2, this.height / 2 - 80, 0xFFFFFF);
		field.render(context, mouseX, mouseY, delta);
		int bannerY = this.height / 2 + 40;
		if (LoginNotice.visible() && !LoginNotice.success) {
			LoginNotice.draw(context, this.textRenderer, this.width, bannerY);
		} else {
			context.drawCenteredTextWithShadow(this.textRenderer, status, this.width / 2, bannerY, LoginNotice.TEXT);
		}
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
		if (field.keyPressed(input)) {
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(net.minecraft.client.input.CharInput input) {
		if (field.charTyped(input)) {
			return true;
		}
		return super.charTyped(input);
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}
}
