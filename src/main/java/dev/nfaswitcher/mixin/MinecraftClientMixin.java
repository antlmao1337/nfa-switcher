package dev.nfaswitcher.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import dev.nfaswitcher.NfaSwitcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Shadow
	@Final
	private Session session;

	@Shadow
	@Final
	public File runDirectory;

	@Unique
	private UUID nfa$lastUuid;

	@Unique
	private String nfa$lastToken;

	@Unique
	private ProfileKeys nfa$keys;

	@Inject(method = "getSession", at = @At("HEAD"), cancellable = true)
	private void nfa$getSession(CallbackInfoReturnable<Session> cir) {
		if (NfaSwitcher.originalSession == null) {
			NfaSwitcher.originalSession = this.session;
			if (NfaSwitcher.currentSession == null) {
				NfaSwitcher.currentSession = this.session;
			}
		}
		if (NfaSwitcher.currentSession != null && NfaSwitcher.currentSession != this.session) {
			cir.setReturnValue(NfaSwitcher.currentSession);
		}
	}

	@Inject(method = "getProfileKeys", at = @At("HEAD"), cancellable = true)
	private void nfa$getProfileKeys(CallbackInfoReturnable<ProfileKeys> cir) {
		if (NfaSwitcher.currentSession == null || NfaSwitcher.currentSession == NfaSwitcher.originalSession) {
			return;
		}
		Session s = NfaSwitcher.currentSession;
		UUID uuid = s.getUuidOrNull();
		String token = s.getAccessToken();
		boolean stale = nfa$lastUuid == null || !nfa$lastUuid.equals(uuid)
				|| nfa$lastToken == null || !nfa$lastToken.equals(token);
		if (stale) {
			nfa$lastUuid = uuid;
			nfa$lastToken = token;
			try {
				Path path = runDirectory.toPath().resolve("profilekeys");
				nfa$keys = ProfileKeys.create(UserApiService.OFFLINE, s, path);
			} catch (Exception e) {
				nfa$keys = null;
			}
		}
		if (nfa$keys != null) {
			cir.setReturnValue(nfa$keys);
		}
	}
}
