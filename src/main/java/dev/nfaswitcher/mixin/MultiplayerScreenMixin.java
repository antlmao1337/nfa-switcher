package dev.nfaswitcher.mixin;

import dev.nfaswitcher.NfaSwitcher;
import dev.nfaswitcher.screen.AccountManagerScreen;
import dev.nfaswitcher.util.SessionChanger;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {
	protected MultiplayerScreenMixin(Text title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void nfa$button(CallbackInfo ci) {
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Alts"), b ->
				this.client.setScreen(new AccountManagerScreen(this))
		).dimensions(this.width - 90, 5, 80, 20).build());

		this.addDrawable((context, mouseX, mouseY, delta) -> {
			boolean swapped = NfaSwitcher.isSwapped();
			Text display = Text.literal(swapped ? "Alt: " : "Account: ")
					.formatted(swapped ? Formatting.GOLD : Formatting.GRAY)
					.append(Text.literal(SessionChanger.currentName()).formatted(Formatting.WHITE));
			context.drawTextWithShadow(this.textRenderer, display, 6, 8, 0xFFFFFF);
		});
	}
}
