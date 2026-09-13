package dev.nfaswitcher;

import dev.nfaswitcher.account.AccountStore;
import dev.nfaswitcher.screen.AccountManagerScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class NfaSwitcherClient implements ClientModInitializer {
	private KeyBinding openKey;

	@Override
	public void onInitializeClient() {
		NfaSwitcher.store = new AccountStore();
		NfaSwitcher.LOGGER.info("NFA Switcher loaded. {} saved account(s).", NfaSwitcher.store.accounts().size());

		openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.nfaswitcher.open",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_U,
				KeyBinding.Category.MISC
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.wasPressed()) {
				client.setScreen(new AccountManagerScreen(client.currentScreen));
			}
		});

		HudRenderCallback.EVENT.register((context, tickCounter) -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.world == null || mc.currentScreen != null || !NfaSwitcher.isSwapped()) {
				return;
			}
			Text label = Text.literal("Alt: ")
					.formatted(Formatting.GOLD)
					.append(Text.literal(mc.getSession().getUsername()).formatted(Formatting.WHITE));
			context.drawTextWithShadow(mc.textRenderer, label, 4, 4, 0xFFFFFF);
		});
	}
}
