package dev.nfaswitcher.screen;

import dev.nfaswitcher.NfaSwitcher;
import dev.nfaswitcher.account.Account;
import dev.nfaswitcher.util.OfflineGen;
import dev.nfaswitcher.util.SessionChanger;
import dev.nfaswitcher.util.Uuids;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class OfflineScreen extends Screen {
	private final Screen parent;
	private TextFieldWidget nameField;
	private TextFieldWidget prefixField;
	private TextFieldWidget countField;
	private Text status = Text.literal("Offline accounts work on cracked / offline-mode servers").formatted(Formatting.GRAY);

	public OfflineScreen(Screen parent) {
		super(Text.literal("Offline Accounts"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = this.height / 2 - 70;

		nameField = new TextFieldWidget(this.textRenderer, cx - 120, y, 240, 20, Text.literal("Username"));
		nameField.setMaxLength(16);
		nameField.setPlaceholder(Text.literal("custom cracked name"));
		this.addSelectableChild(nameField);

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Add + Switch"), b -> addCustom())
				.dimensions(cx - 120, y + 24, 240, 20).build());

		prefixField = new TextFieldWidget(this.textRenderer, cx - 120, y + 56, 150, 20, Text.literal("Prefix"));
		prefixField.setMaxLength(13);
		prefixField.setText("Player");
		this.addSelectableChild(prefixField);

		countField = new TextFieldWidget(this.textRenderer, cx + 36, y + 56, 84, 20, Text.literal("Count"));
		countField.setMaxLength(4);
		countField.setText("10");
		this.addSelectableChild(countField);

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Generate 1"), b -> gen(1))
				.dimensions(cx - 120, y + 82, 76, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Generate 10"), b -> gen(10))
				.dimensions(cx - 40, y + 82, 80, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Generate 50"), b -> gen(50))
				.dimensions(cx + 44, y + 82, 76, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Generate N"), b -> gen(parseCount()))
				.dimensions(cx - 120, y + 106, 118, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Generate 100"), b -> gen(100))
				.dimensions(cx + 2, y + 106, 118, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Wipe Offline"), b -> {
			NfaSwitcher.store.removeOffline();
			status = Text.literal("Removed all offline accounts").formatted(Formatting.YELLOW);
		}).dimensions(cx - 120, y + 130, 240, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> this.client.setScreen(parent))
				.dimensions(cx - 100, y + 158, 200, 20).build());
	}

	private int parseCount() {
		try {
			return Math.max(1, Math.min(500, Integer.parseInt(countField.getText().trim())));
		} catch (Exception e) {
			return 10;
		}
	}

	private void addCustom() {
		String name = nameField.getText() == null ? "" : nameField.getText().trim();
		if (name.length() < 3) {
			status = Text.literal("Name must be at least 3 characters").formatted(Formatting.RED);
			return;
		}
		if (name.length() > 16) {
			name = name.substring(0, 16);
		}
		Account a = Account.offline(name, Uuids.offlineString(name));
		NfaSwitcher.store.add(a);
		SessionChanger.apply(a);
		status = Text.literal("Now playing as " + name).formatted(Formatting.GREEN);
	}

	private void gen(int n) {
		List<Account> made = OfflineGen.generate(prefixField.getText(), n);
		NfaSwitcher.store.addAll(made);
		if (!made.isEmpty()) {
			SessionChanger.apply(made.getFirst());
		}
		status = Text.literal("Generated " + made.size() + " offline accounts").formatted(Formatting.GREEN);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.literal("Offline / Cracked").formatted(Formatting.LIGHT_PURPLE),
				this.width / 2, this.height / 2 - 92, 0xFFFFFF);
		context.drawCenteredTextWithShadow(this.textRenderer, status, this.width / 2, this.height / 2 + 112, 0xFFFFFF);
		nameField.render(context, mouseX, mouseY, delta);
		prefixField.render(context, mouseX, mouseY, delta);
		countField.render(context, mouseX, mouseY, delta);
		context.drawTextWithShadow(this.textRenderer, Text.literal("prefix").formatted(Formatting.DARK_GRAY),
				this.width / 2 - 120, this.height / 2 - 26, 0xFFFFFF);
		context.drawTextWithShadow(this.textRenderer, Text.literal("count").formatted(Formatting.DARK_GRAY),
				this.width / 2 + 36, this.height / 2 - 26, 0xFFFFFF);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
		if (nameField.keyPressed(input) || prefixField.keyPressed(input) || countField.keyPressed(input)) {
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(net.minecraft.client.input.CharInput input) {
		if (nameField.charTyped(input) || prefixField.charTyped(input) || countField.charTyped(input)) {
			return true;
		}
		return super.charTyped(input);
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}
}
