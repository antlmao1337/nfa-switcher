package dev.nfaswitcher.screen;

import dev.nfaswitcher.NfaSwitcher;
import dev.nfaswitcher.auth.AuthException;
import dev.nfaswitcher.auth.AuthResult;
import dev.nfaswitcher.auth.MicrosoftAuth;
import dev.nfaswitcher.util.LoginNotice;
import dev.nfaswitcher.util.SessionChanger;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

public class MicrosoftLoginScreen extends Screen {
	private final Screen parent;
	private volatile MicrosoftAuth.DeviceCode code;
	private volatile Text status = Text.literal("Starting Microsoft device login...").formatted(Formatting.YELLOW);
	private volatile boolean done;
	private Thread poller;

	public MicrosoftLoginScreen(Screen parent) {
		super(Text.literal("Microsoft Login"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = this.height / 2 + 20;

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Open Browser"), b -> {
			if (code != null) {
				Util.getOperatingSystem().open(code.verificationUri);
			}
		}).dimensions(cx - 155, y, 100, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Copy Code"), b -> {
			if (code != null) {
				this.client.keyboard.setClipboard(code.userCode);
				status = Text.literal("Copied " + code.userCode).formatted(Formatting.GREEN);
			}
		}).dimensions(cx - 50, y, 100, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> {
			done = true;
			this.client.setScreen(parent);
		}).dimensions(cx + 55, y, 100, 20).build());

		if (code == null && poller == null) {
			start();
		}
	}

	private void start() {
		poller = new Thread(() -> {
			try {
				MicrosoftAuth.DeviceCode dc = MicrosoftAuth.startDeviceCode();
				this.code = dc;
				this.status = Text.literal(dc.message).formatted(Formatting.WHITE);
				this.client.execute(() -> {
					if (dc.userCode != null) {
						this.client.keyboard.setClipboard(dc.userCode);
					}
					if (dc.verificationUri != null) {
						Util.getOperatingSystem().open(dc.verificationUri);
					}
					this.status = Text.literal("Code copied. Browser opened â€” paste the code and sign in.")
							.formatted(Formatting.YELLOW);
				});
				while (!done) {
					AuthResult result = MicrosoftAuth.pollDevice(dc);
					if (result != null) {
						done = true;
						this.client.execute(() -> {
							NfaSwitcher.store.add(result.toAccount());
							SessionChanger.apply(result.toAccount());
							LoginNotice.ok(result.username);
							this.client.setScreen(new AccountManagerScreen(parent));
						});
						return;
					}
					Thread.sleep(dc.interval * 1000L);
				}
			} catch (InterruptedException ignored) {
			} catch (AuthException e) {
				LoginNotice.fail(e);
				this.status = Text.literal(e.display()).formatted(Formatting.RED);
			} catch (Exception e) {
				AuthException wrap = new AuthException("microsoft", "ERROR", 0,
						e.getMessage() == null ? e.toString() : e.getMessage());
				LoginNotice.fail(wrap);
				this.status = Text.literal(wrap.display()).formatted(Formatting.RED);
			}
		}, "NfaDeviceCode");
		poller.setDaemon(true);
		poller.start();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.literal("Add Microsoft Account").formatted(Formatting.AQUA),
				this.width / 2, this.height / 2 - 60, 0xFFFFFF);
		if (code != null) {
			context.drawCenteredTextWithShadow(this.textRenderer,
					Text.literal(code.verificationUri).formatted(Formatting.YELLOW),
					this.width / 2, this.height / 2 - 36, 0xFFFFFF);
			context.drawCenteredTextWithShadow(this.textRenderer,
					Text.literal(code.userCode).formatted(Formatting.GOLD, Formatting.BOLD),
					this.width / 2, this.height / 2 - 20, 0xFFFFFF);
		}
		context.drawCenteredTextWithShadow(this.textRenderer, status, this.width / 2, this.height / 2 + 2, 0xFFFFFF);
	}

	@Override
	public void close() {
		done = true;
		this.client.setScreen(parent);
	}
}
