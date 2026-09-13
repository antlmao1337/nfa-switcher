package dev.nfaswitcher.screen;

import dev.nfaswitcher.NfaSwitcher;
import dev.nfaswitcher.account.Account;
import dev.nfaswitcher.auth.AuthException;
import dev.nfaswitcher.auth.AuthResult;
import dev.nfaswitcher.auth.MicrosoftAuth;
import dev.nfaswitcher.util.LoginNotice;
import dev.nfaswitcher.util.SessionChanger;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class AccountManagerScreen extends Screen {
	private static final int PER_PAGE = 6;

	private final Screen parent;
	private Text status = Text.empty();
	private TextFieldWidget search;
	private String query = "";
	private int page;
	private int titleY;
	private int statusY;
	private int rowStart;

	public AccountManagerScreen(Screen parent) {
		super(Text.literal("NFA Switcher"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int banner = LoginNotice.height(this.textRenderer, this.width);
		int top = 18;
		this.titleY = top;
		this.statusY = top + 14;
		int searchY = top + 28 + (banner > 0 ? banner + 6 : 0);

		search = new TextFieldWidget(this.textRenderer, cx - 150, searchY, 220, 18, Text.literal("Search"));
		search.setMaxLength(64);
		search.setText(query);
		search.setChangedListener(s -> {
			query = s;
			page = 0;
			this.clearAndInit();
		});
		this.addSelectableChild(search);

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Restore"), b -> {
			SessionChanger.restore();
			status = ok("Restored original");
		}).dimensions(cx + 76, searchY - 1, 74, 20).build());

		List<Account> all = filtered();
		int maxPage = Math.max(0, (all.size() - 1) / PER_PAGE);
		page = Math.min(page, maxPage);
		int start = page * PER_PAGE;
		int end = Math.min(all.size(), start + PER_PAGE);
		this.rowStart = searchY + 26;

		for (int i = start; i < end; i++) {
			Account acc = all.get(i);
			int y = rowStart + (i - start) * 22;
			this.addDrawableChild(ButtonWidget.builder(row(acc), b -> switchTo(acc))
					.dimensions(cx - 150, y, 232, 20).build());
			this.addDrawableChild(ButtonWidget.builder(Text.literal("X").formatted(Formatting.RED), b -> {
				NfaSwitcher.store.remove(acc);
				this.clearAndInit();
			}).dimensions(cx + 86, y, 64, 20).build());
		}

		int navY = rowStart + Math.max(1, end - start) * 22 + 6;

		ButtonWidget prev = ButtonWidget.builder(Text.literal("<"), b -> {
			page--;
			this.clearAndInit();
		}).dimensions(cx - 150, navY, 30, 20).build();
		prev.active = page > 0;
		this.addDrawableChild(prev);

		ButtonWidget next = ButtonWidget.builder(Text.literal(">"), b -> {
			page++;
			this.clearAndInit();
		}).dimensions(cx - 116, navY, 30, 20).build();
		next.active = page < maxPage;
		this.addDrawableChild(next);

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Save Current"), b -> {
			NfaSwitcher.store.add(SessionChanger.currentAsAccount());
			status = ok("Saved " + SessionChanger.currentName());
			this.clearAndInit();
		}).dimensions(cx - 80, navY, 110, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Refresh All MSA"), b -> refreshVisible(all))
				.dimensions(cx + 36, navY, 114, 20).build());

		int by = navY + 24;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Microsoft"), b ->
				this.client.setScreen(new MicrosoftLoginScreen(this))
		).dimensions(cx - 150, by, 96, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Cookie Alt"), b ->
				this.client.setScreen(new AddCookieScreen(this))
		).dimensions(cx - 50, by, 96, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Refresh Token"), b ->
				this.client.setScreen(new AddRefreshScreen(this))
		).dimensions(cx + 50, by, 100, 20).build());

		by += 24;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Offline / Cracked"), b ->
				this.client.setScreen(new OfflineScreen(this))
		).dimensions(cx - 150, by, 148, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Import .txt"), b ->
				this.client.setScreen(new ImportTxtScreen(this))
		).dimensions(cx + 2, by, 148, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b ->
				this.client.setScreen(parent)
		).dimensions(cx - 100, by + 28, 200, 20).build());
	}

	private List<Account> filtered() {
		List<Account> list = new ArrayList<>();
		String q = query.toLowerCase(Locale.ROOT).trim();
		for (Account a : NfaSwitcher.store.accounts()) {
			if (q.isEmpty()
					|| a.displayName().toLowerCase(Locale.ROOT).contains(q)
					|| a.kind.badge().toLowerCase(Locale.ROOT).contains(q)
					|| (a.note != null && a.note.toLowerCase(Locale.ROOT).contains(q))) {
				list.add(a);
			}
		}
		list.sort(Comparator.comparingLong((Account a) -> a.lastUsed).reversed());
		return list;
	}

	private Text row(Account acc) {
		boolean active = acc.username != null && acc.username.equalsIgnoreCase(SessionChanger.currentName());
		Formatting nameCol = active ? Formatting.GREEN : Formatting.WHITE;
		return Text.literal(acc.displayName()).formatted(nameCol)
				.append(Text.literal("  [" + acc.kind.badge() + "]").formatted(Formatting.GRAY));
	}

	private void switchTo(Account acc) {
		if (acc.kind == dev.nfaswitcher.account.AccountKind.OFFLINE) {
			SessionChanger.apply(acc);
			status = ok("Offline: " + acc.username);
			return;
		}
		status = Text.literal("Logging in " + acc.username + "...").formatted(Formatting.YELLOW);
		MicrosoftAuth.runAsync(() -> MicrosoftAuth.relogin(acc), err -> this.client.execute(() -> {
			LoginNotice.fail(err);
			status = bad(err.display());
			this.clearAndInit();
		}), result -> this.client.execute(() -> applyResult(acc, result)));
	}

	private void applyResult(Account acc, AuthResult result) {
		acc.username = result.username;
		acc.uuid = result.uuid;
		acc.accessToken = result.accessToken;
		if (result.refreshToken != null && !result.refreshToken.isBlank()) {
			acc.refreshToken = result.refreshToken;
		}
		if (result.cookies != null && !result.cookies.isBlank()) {
			acc.cookies = result.cookies;
		}
		acc.authClient = result.authClient;
		acc.touch();
		NfaSwitcher.store.save();
		SessionChanger.apply(acc);
		LoginNotice.ok(acc.username);
		status = ok("Logged in as " + acc.username);
		this.clearAndInit();
	}

	private void refreshVisible(List<Account> all) {
		status = Text.literal("Refreshing...").formatted(Formatting.YELLOW);
		Thread t = new Thread(() -> {
			int ok = 0;
			int fail = 0;
			for (Account a : all) {
				if (!a.isOnline()) {
					continue;
				}
				try {
					AuthResult r = MicrosoftAuth.relogin(a);
					a.username = r.username;
					a.uuid = r.uuid;
					a.accessToken = r.accessToken;
					if (r.refreshToken != null && !r.refreshToken.isBlank()) {
						a.refreshToken = r.refreshToken;
					}
					ok++;
				} catch (Exception e) {
					fail++;
				}
			}
			NfaSwitcher.store.save();
			int fOk = ok;
			int fFail = fail;
			this.client.execute(() -> {
				status = Text.literal("Refreshed " + fOk + ", failed " + fFail)
						.formatted(fFail == 0 ? Formatting.GREEN : Formatting.YELLOW);
				this.clearAndInit();
			});
		}, "NfaRefreshAll");
		t.setDaemon(true);
		t.start();
	}

	private static Text ok(String m) {
		return Text.literal(m).formatted(Formatting.GREEN);
	}

	private static Text bad(String m) {
		return Text.literal(m).formatted(Formatting.RED);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.literal("NFA Switcher"),
				this.width / 2, titleY, LoginNotice.TEXT);
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.literal("Current: " + SessionChanger.currentName()),
				this.width / 2, statusY, 0xFFBBBBBB);
		LoginNotice.draw(context, this.textRenderer, this.width, statusY + 12);
		if (search != null) {
			search.render(context, mouseX, mouseY, delta);
		}
		if (filtered().isEmpty()) {
			context.drawCenteredTextWithShadow(this.textRenderer,
					Text.literal("No accounts yet. Add Microsoft / cookie / refresh / offline."),
					this.width / 2, rowStart + 8, 0xFF888888);
		}
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
		if (search != null && search.keyPressed(input)) {
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(net.minecraft.client.input.CharInput input) {
		if (search != null && search.charTyped(input)) {
			return true;
		}
		return super.charTyped(input);
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}
}
