package dev.nfaswitcher;

import dev.nfaswitcher.account.AccountStore;
import net.minecraft.client.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NfaSwitcher {
	public static final String MOD_ID = "nfaswitcher";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static AccountStore store;
	public static Session originalSession;
	public static Session currentSession;

	private NfaSwitcher() {
	}

	public static boolean isSwapped() {
		return currentSession != null
				&& originalSession != null
				&& currentSession != originalSession;
	}
}
