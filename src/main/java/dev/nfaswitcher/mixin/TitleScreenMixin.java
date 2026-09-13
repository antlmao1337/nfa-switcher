package dev.nfaswitcher.mixin;

import dev.nfaswitcher.screen.AccountManagerScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
	protected TitleScreenMixin(Text title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void nfa$button(CallbackInfo ci) {
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Alts"), b ->
				this.client.setScreen(new AccountManagerScreen(this))
		).dimensions(5, 5, 50, 20).build());
	}
}
