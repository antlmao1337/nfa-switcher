# NFA Switcher

A client-side Fabric account switcher for Minecraft Java 1.21.11.

Add and swap Microsoft accounts, cookie alts, MSA refresh tokens, session JWTs, and unlimited offline / cracked names without restarting the client.

---

## Features

- **Microsoft login**
  - Device-code flow for adding a main Microsoft account
  - Browser opens automatically and the code is copied to the clipboard
  - Azure device-code first, Xbox device-code as fallback

- **Cookie alts**
  - Netscape `cookies.txt`
  - JSON cookie arrays
  - Raw `Cookie:` headers
  - One cookie per line (`MSPAuth` / `MSPProf` / `WLSSID`)
  - Load from paste or a `.txt` file

- **Refresh tokens**
  - Single token or many (one per line)
  - `email:refresh_token` lines
  - Tries Xbox, Prism, official launcher, and Bedrock client IDs
  - Clear `[error_code] HTTP xxx @ stage` banner on failure

- **Session tokens**
  - Minecraft access JWTs (`eyJ...`)
  - Profile lookup against official Mojang APIs only

- **Offline / cracked**
  - Custom username
  - Generate 1 / 10 / 50 / 100 / N accounts
  - Vanilla `OfflinePlayer:` UUID

- **Account manager**
  - Title screen + Multiplayer **Alts** button
  - Keybind (default `U`)
  - Search, paginate, delete, restore original session
  - Success banner shows the logged-in name
  - Failure banner shows the error code and reason

---

## Controls

| Action | Where |
|--------|--------|
| Open manager | Title screen / Multiplayer **Alts** button |
| Open manager | Keybind `U` (Controls → Misc) |
| Restore launcher account | **Restore** in the manager |

Switching an online account does not rewrite a world you are already in. Disconnect and reconnect after a swap.

---

## Installation

1. Install Fabric Loader for Minecraft 1.21.11
2. Install Fabric API
3. Place the mod jar in your `mods` folder

---

## Config & Data

- Saved accounts are stored in: `.minecraft/config/nfaswitcher/accounts.json`
- Optional bulk import drop folder: `.minecraft/config/nfaswitcher/import/`
- You can delete `accounts.json` to reset the list.

Tokens and cookies never leave the local config file. Network calls go only to Microsoft / Xbox / `api.minecraftservices.com`.

---

## Build

Requires JDK 21.

```bash
git clone https://github.com/antlmao1337/nfa-switcher.git
cd nfa-switcher
./gradlew build
```

Windows:

```bat
gradlew.bat build
```

Output jar: `build/libs/nfa-switcher-mc1.21.11-1.0.0.jar`

---

## Notes

- This is a client-side session tool. It does not bypass online-mode authentication on servers.
- Offline / cracked names only join offline-mode servers.
- Cookie dumps and refresh tokens expire. If Microsoft returns `invalid_grant` or `LOGIN_FORM`, the session is dead and needs a new dump.
- Effectiveness depends on the token / cookie still being valid with Microsoft.

---

## Credits

Developed as a focused client-side account switcher for Fabric 1.21.11.
For questions, problems, or suggestions, please use the repository's issue tracker.
