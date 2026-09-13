package dev.nfaswitcher.screen;

import dev.nfaswitcher.NfaSwitcher;
import dev.nfaswitcher.auth.MicrosoftAuth;
import dev.nfaswitcher.util.FilePick;
import dev.nfaswitcher.util.LoginNotice;
import dev.nfaswitcher.util.SessionChanger;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class AddCookieScreen extends Screen {
	private final Screen parent;
	private TextFieldWidget field;
	private Text status = Text.literal("Paste Netscape cookies or a Cookie header").formatted(Formatting.GRAY);
	private boolean busy;

	public AddCookieScreen(Screen parent) {
		super(Text.literal("Cookie Alt"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = this.height / 2 - 40;
		field = new TextFieldWidget(this.textRenderer, cx - 160, y, 320, 20, Text.literal("Cookies"));
		field.setMaxLength(32767);
		field.setPlaceholder(Text.literal("MSPAuth=...; MSPProf=...  or netscape dump"));
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
			status = Text.literal("Nothing pasted").formatted(Formatting.RED);
			return;
		}
		busy = true;
		status = Text.literal("Logging in with cookies...").formatted(Formatting.YELLOW);
		MicrosoftAuth.runAsync(() -> MicrosoftAuth.loginCookies(raw), err -> this.client.execute(() -> {
			busy = false;
			LoginNotice.fail(err);
			status = Text.literal(err.display()).formatted(Formatting.RED);
		}), result -> this.client.execute(() -> {
			busy = false;
			NfaSwitcher.store.add(result.toAccount());
			SessionChanger.apply(result.toAccount());
			LoginNotice.ok(result.username);
			this.client.setScreen(new AccountManagerScreen(parent));
		}));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.literal("Cookie Alt"),
				this.width / 2, this.height / 2 - 70, LoginNotice.TEXT);
		field.render(context, mouseX, mouseY, delta);
		int y = this.height / 2 + 50;
		if (LoginNotice.visible() && !LoginNotice.success) {
			LoginNotice.draw(context, this.textRenderer, this.width, y);
		} else {
			context.drawCenteredTextWithShadow(this.textRenderer, status, this.width / 2, y, LoginNotice.TEXT);
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
