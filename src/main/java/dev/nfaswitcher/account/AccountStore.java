package dev.nfaswitcher.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.nfaswitcher.NfaSwitcher;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class AccountStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type LIST_TYPE = new TypeToken<List<Account>>() {}.getType();

	private final Path file;
	private final List<Account> accounts = new ArrayList<>();

	public AccountStore() {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve("nfaswitcher");
		this.file = dir.resolve("accounts.json");
		try {
			Files.createDirectories(dir);
			Files.createDirectories(dir.resolve("import"));
		} catch (IOException e) {
			NfaSwitcher.LOGGER.error("Failed to create config dir", e);
		}
		load();
	}

	public Path importDir() {
		return file.getParent().resolve("import");
	}

	public synchronized List<Account> accounts() {
		return Collections.unmodifiableList(new ArrayList<>(accounts));
	}

	public synchronized void add(Account account) {
		for (int i = 0; i < accounts.size(); i++) {
			Account existing = accounts.get(i);
			if (sameIdentity(existing, account)) {
				account.id = existing.id;
				account.createdAt = existing.createdAt;
				accounts.set(i, account);
				save();
				return;
			}
		}
		accounts.add(account);
		save();
	}

	public synchronized void addAll(List<Account> list) {
		for (Account a : list) {
			add(a);
		}
	}

	public synchronized void remove(Account account) {
		accounts.removeIf(a -> a.id.equals(account.id));
		save();
	}

	public synchronized void save() {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(accounts), StandardCharsets.UTF_8);
		} catch (IOException e) {
			NfaSwitcher.LOGGER.error("Failed to save accounts", e);
		}
	}

	public synchronized void load() {
		accounts.clear();
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			String json = Files.readString(file, StandardCharsets.UTF_8);
			List<Account> loaded = GSON.fromJson(json, LIST_TYPE);
			if (loaded != null) {
				accounts.addAll(loaded);
			}
		} catch (Exception e) {
			NfaSwitcher.LOGGER.error("Failed to load accounts", e);
		}
	}

	public synchronized Account findByUuid(String uuid) {
		if (uuid == null) {
			return null;
		}
		String n = uuid.replace("-", "").toLowerCase();
		for (Account a : accounts) {
			if (a.uuid != null && a.uuid.replace("-", "").toLowerCase().equals(n)) {
				return a;
			}
		}
		return null;
	}

	private static boolean sameIdentity(Account a, Account b) {
		if (a.uuid != null && b.uuid != null && !a.uuid.isBlank() && !b.uuid.isBlank()) {
			return a.uuid.replace("-", "").equalsIgnoreCase(b.uuid.replace("-", ""));
		}
		return a.kind == b.kind && a.username.equalsIgnoreCase(b.username);
	}

	public synchronized int offlineCount() {
		int n = 0;
		for (Account a : accounts) {
			if (a.kind == AccountKind.OFFLINE) {
				n++;
			}
		}
		return n;
	}

	public synchronized void removeOffline() {
		Iterator<Account> it = accounts.iterator();
		while (it.hasNext()) {
			if (it.next().kind == AccountKind.OFFLINE) {
				it.remove();
			}
		}
		save();
	}
}
