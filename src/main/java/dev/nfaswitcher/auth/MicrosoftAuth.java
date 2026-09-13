package dev.nfaswitcher.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nfaswitcher.account.Account;
import dev.nfaswitcher.account.AccountKind;
import dev.nfaswitcher.util.Http;
import dev.nfaswitcher.util.Uuids;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import dev.nfaswitcher.NfaSwitcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MicrosoftAuth {
	public static final String XBOX_CLIENT_ID = "00000000402b5328";
	public static final String AZURE_CLIENT_ID = "c36a9fb6-4f2a-41ff-90bd-ae7cc92031db";
	public static final String XBOX_REDIRECT = "https://login.live.com/oauth20_desktop.srf";
	public static final String XBOX_SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";
	public static final String AZURE_SCOPE = "XboxLive.signin offline_access";

	private static final String AUTHORIZE = "https://login.live.com/oauth20_authorize.srf"
			+ "?client_id=" + XBOX_CLIENT_ID
			+ "&response_type=token"
			+ "&redirect_uri=" + Http.enc(XBOX_REDIRECT)
			+ "&scope=" + Http.enc(XBOX_SCOPE)
			+ "&display=touch&locale=en&prompt=none";

	private static final String AUTHORIZE_CODE = "https://login.live.com/oauth20_authorize.srf"
			+ "?client_id=" + XBOX_CLIENT_ID
			+ "&response_type=code"
			+ "&redirect_uri=" + Http.enc(XBOX_REDIRECT)
			+ "&scope=" + Http.enc(XBOX_SCOPE)
			+ "&display=touch&locale=en&prompt=none";

	private static final String AUTHORIZE_CODE_PROMPT = "https://login.live.com/oauth20_authorize.srf"
			+ "?client_id=" + XBOX_CLIENT_ID
			+ "&response_type=code"
			+ "&redirect_uri=" + Http.enc(XBOX_REDIRECT)
			+ "&scope=" + Http.enc(XBOX_SCOPE)
			+ "&display=touch&locale=en";

	private static final String SISU = "https://sisu.xboxlive.com/connect/XboxLive/?state=login"
			+ "&cobrandId=8058f65d-ce06-4c30-9559-473c9275a65d&tid=896928775"
			+ "&ru=" + Http.enc("https://www.minecraft.net/en-us/login")
			+ "&aid=1142970254";

	private static final Pattern META_REFRESH = Pattern.compile(
			"http-equiv=[\"']refresh[\"'][^>]*content=[\"'][^\"']*url=([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
	private static final Pattern ERROR_PARAM = Pattern.compile("[?&#]error=([^&\"'#\\s]+)");

	private static final Pattern ACCESS = Pattern.compile("access_token=([^&\"'#\\s]+)");
	private static final Pattern REFRESH = Pattern.compile("refresh_token=([^&\"'#\\s]+)");
	private static final Pattern CODE = Pattern.compile("[?&#]code=([^&\"'#\\s]+)");
	private static final Pattern JS_URL = Pattern.compile("window\\.location\\s*=\\s*['\"]([^'\"]+)['\"]");
	private static final Pattern FORM_ACTION = Pattern.compile("<form[^>]+action=['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);

	private MicrosoftAuth() {
	}

	public static String sanitizeToken(String raw) {
		if (raw == null) {
			return "";
		}
		String t = raw.trim();
		if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
			t = t.substring(1, t.length() - 1).trim();
		}
		t = t.replace("\r", "").replace("\n", "").trim();
		if (t.regionMatches(true, 0, "Bearer ", 0, 7)) {
			t = t.substring(7).trim();
		}
		String lower = t.toLowerCase();
		for (String prefix : List.of("refresh_token:", "refreshtoken:", "refresh:", "token:", "rt:")) {
			if (lower.startsWith(prefix)) {
				t = t.substring(prefix.length()).trim();
				lower = t.toLowerCase();
			}
		}
		if (t.contains("%") && t.length() > 20) {
			try {
				t = java.net.URLDecoder.decode(t, java.nio.charset.StandardCharsets.UTF_8);
			} catch (Exception ignored) {
			}
		}
		return t.trim();
	}

	public static AuthResult loginAny(String raw) throws AuthException {
		String token = sanitizeToken(raw);
		if (token.isBlank()) {
			throw new AuthException("refresh", "EMPTY", 0, "No token pasted.");
		}
		if (token.startsWith("eyJ")) {
			try {
				return loginSessionToken(token);
			} catch (AuthException ignored) {
			}
		}
		return loginRefresh(token);
	}

	public static AuthResult loginRefresh(String refreshToken) throws AuthException {
		String token = sanitizeToken(refreshToken);
		if (token.isBlank()) {
			throw new AuthException("refresh", "EMPTY", 0, "No refresh token pasted.");
		}
		if (token.startsWith("eyJ")) {
			return loginSessionToken(token);
		}

		List<String> errors = new ArrayList<>();
		List<OAuthAttempt> attempts = oauthAttempts(token);
		MsaToken msa = null;
		for (OAuthAttempt attempt : attempts) {
			try {
				msa = refreshMsa(attempt, token);
				NfaSwitcher.LOGGER.info("Refresh token accepted by {}", attempt.name);
				break;
			} catch (AuthException e) {
				NfaSwitcher.LOGGER.info("Refresh attempt {} failed: {}", attempt.name, e.display());
				errors.add(attempt.name + " " + e.display());
			}
		}
		if (msa == null) {
			throw new AuthException("refresh", "INVALID_GRANT", 400,
					"Refresh token rejected by every Microsoft client. Last errors: " + String.join(" | ", errors));
		}
		return complete(msa, "", AccountKind.REFRESH);
	}

	public static AuthResult loginCookies(String raw) throws AuthException {
		List<CookieParser.Cookie> cookies = CookieParser.parse(raw);
		if (cookies.isEmpty()) {
			throw new AuthException("cookie", "PARSE", 0, "No cookies found. Paste a Netscape dump or a Cookie header (MSPAuth / MSPProf).");
		}
		MsaToken msa = msaFromCookies(cookies, raw);
		return complete(msa, CookieParser.toHeader(cookies), AccountKind.COOKIE);
	}

	public static AuthResult loginSessionToken(String token) throws AuthException {
		token = stripBearer(token);
		JsonObject profile = fetchProfile(token);
		return new AuthResult(
				Http.str(profile, "name"),
				Uuids.dash(Http.str(profile, "id")),
				token,
				"",
				"",
				"session",
				AccountKind.SESSION
		);
	}

	public static AuthResult relogin(Account account) throws AuthException {
		if (account.kind == AccountKind.OFFLINE) {
			return new AuthResult(account.username, account.uuid, "", "", "", "offline", AccountKind.OFFLINE);
		}
		if (account.refreshToken != null && !account.refreshToken.isBlank()) {
			try {
				return loginRefresh(account.refreshToken);
			} catch (AuthException ignored) {
			}
		}
		if (account.cookies != null && !account.cookies.isBlank()) {
			return loginCookies(account.cookies);
		}
		if (account.accessToken != null && !account.accessToken.isBlank()) {
			return loginSessionToken(account.accessToken);
		}
		throw new AuthException("Account has nothing to log in with");
	}

	public static DeviceCode startDeviceCode() throws AuthException {
		try {
			return startAzureDevice();
		} catch (AuthException azure) {
			try {
				return startXboxDevice();
			} catch (AuthException xbox) {
				throw new AuthException("microsoft", "DEVICE_CODE", 0,
						"Could not start Microsoft login. Azure: " + azure.getMessage() + " | Xbox: " + xbox.getMessage());
			}
		}
	}

	public static AuthResult pollDevice(DeviceCode code) throws AuthException {
		if ("azure".equals(code.client)) {
			return pollAzure(code);
		}
		return pollXbox(code);
	}

	private static DeviceCode startXboxDevice() throws AuthException {
		try {
			Map<String, String> fields = new LinkedHashMap<>();
			fields.put("client_id", XBOX_CLIENT_ID);
			fields.put("scope", XBOX_SCOPE);
			fields.put("response_type", "device_code");
			fields.put("redirect_uri", XBOX_REDIRECT);
			HttpResponse<String> resp = Http.postForm("https://login.live.com/oauth20_connect.srf", fields);
			JsonObject json = Http.json(resp);
			if (Http.str(json, "device_code") == null) {
				throw new AuthException("Xbox device code failed: " + resp.body());
			}
			return DeviceCode.from(json, "xbox");
		} catch (AuthException e) {
			throw e;
		} catch (Exception e) {
			throw new AuthException("Xbox device code request failed", e);
		}
	}

	private static DeviceCode startAzureDevice() throws AuthException {
		try {
			HttpResponse<String> resp = Http.postForm(
					"https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode",
					Map.of("client_id", AZURE_CLIENT_ID, "scope", AZURE_SCOPE)
			);
			JsonObject json = Http.json(resp);
			if (Http.str(json, "device_code") == null) {
				throw new AuthException("Azure device code failed: " + resp.body());
			}
			return DeviceCode.from(json, "azure");
		} catch (AuthException e) {
			throw e;
		} catch (Exception e) {
			throw new AuthException("Azure device code request failed", e);
		}
	}

	private static AuthResult pollXbox(DeviceCode code) throws AuthException {
		try {
			Map<String, String> fields = new LinkedHashMap<>();
			fields.put("client_id", XBOX_CLIENT_ID);
			fields.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
			fields.put("device_code", code.deviceCode);
			HttpResponse<String> resp = Http.postForm("https://login.live.com/oauth20_token.srf", fields);
			AuthResult result = handlePoll(resp, "xbox");
			if (result != null || isPending(resp)) {
				return result;
			}
			fields.put("grant_type", "device_code");
			resp = Http.postForm("https://login.live.com/oauth20_token.srf", fields);
			return handlePoll(resp, "xbox");
		} catch (AuthException e) {
			throw e;
		} catch (Exception e) {
			throw new AuthException("Device poll failed", e);
		}
	}

	private static boolean isPending(HttpResponse<String> resp) {
		String err = Http.str(Http.json(resp), "error");
		return "authorization_pending".equals(err) || "slow_down".equals(err);
	}

	private static AuthResult pollAzure(DeviceCode code) throws AuthException {
		try {
			HttpResponse<String> resp = Http.postForm(
					"https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
					Map.of(
							"client_id", AZURE_CLIENT_ID,
							"grant_type", "urn:ietf:params:oauth:grant-type:device_code",
							"device_code", code.deviceCode
					)
			);
			return handlePoll(resp, "azure");
		} catch (AuthException e) {
			throw e;
		} catch (Exception e) {
			throw new AuthException("Device poll failed", e);
		}
	}

	private static AuthResult handlePoll(HttpResponse<String> resp, String client) throws AuthException {
		JsonObject json = Http.json(resp);
		String err = Http.str(json, "error");
		if (err != null) {
			if ("authorization_pending".equals(err) || "slow_down".equals(err)) {
				return null;
			}
			if ("expired_token".equals(err) || "code_expired".equals(err)) {
				throw new AuthException("Device code expired. Start again.");
			}
			throw new AuthException("Device login failed: " + err);
		}
		String access = Http.str(json, "access_token");
		if (access == null) {
			return null;
		}
		MsaToken msa = new MsaToken(access, Http.str(json, "refresh_token"), client);
		return complete(msa, "", AccountKind.MICROSOFT);
	}

	private static List<OAuthAttempt> oauthAttempts(String token) {
		List<OAuthAttempt> list = new ArrayList<>();
		boolean azureStyle = token.startsWith("0.") || token.length() > 200;
		OAuthAttempt xboxNoScope = new OAuthAttempt("xbox-live", XBOX_CLIENT_ID,
				"https://login.live.com/oauth20_token.srf", XBOX_REDIRECT, null, "xbox");
		OAuthAttempt xboxScope = new OAuthAttempt("xbox-live-scope", XBOX_CLIENT_ID,
				"https://login.live.com/oauth20_token.srf", XBOX_REDIRECT, XBOX_SCOPE, "xbox");
		OAuthAttempt xboxMin = new OAuthAttempt("xbox-live-min", XBOX_CLIENT_ID,
				"https://login.live.com/oauth20_token.srf", null, null, "xbox");
		OAuthAttempt prismConsumers = new OAuthAttempt("prism-consumers", AZURE_CLIENT_ID,
				"https://login.microsoftonline.com/consumers/oauth2/v2.0/token", null, AZURE_SCOPE, "azure");
		OAuthAttempt prismCommon = new OAuthAttempt("prism-common", AZURE_CLIENT_ID,
				"https://login.microsoftonline.com/common/oauth2/v2.0/token", null, AZURE_SCOPE, "azure");
		OAuthAttempt launcher = new OAuthAttempt("mc-launcher", "1f907974-e22b-4810-a9de-d9647380c97e",
				"https://login.microsoftonline.com/consumers/oauth2/v2.0/token", null, AZURE_SCOPE, "azure");
		OAuthAttempt bedrock = new OAuthAttempt("bedrock-android", "0000000048183522",
				"https://login.live.com/oauth20_token.srf", XBOX_REDIRECT, XBOX_SCOPE, "xbox");
		if (azureStyle) {
			list.add(prismConsumers);
			list.add(prismCommon);
			list.add(launcher);
			list.add(xboxNoScope);
			list.add(xboxMin);
			list.add(xboxScope);
			list.add(bedrock);
		} else {
			list.add(xboxNoScope);
			list.add(xboxMin);
			list.add(xboxScope);
			list.add(prismConsumers);
			list.add(prismCommon);
			list.add(launcher);
			list.add(bedrock);
		}
		return list;
	}

	private static MsaToken refreshMsa(OAuthAttempt attempt, String refreshToken) throws AuthException {
		try {
			Map<String, String> fields = new LinkedHashMap<>();
			fields.put("client_id", attempt.clientId);
			fields.put("grant_type", "refresh_token");
			fields.put("refresh_token", refreshToken);
			if (attempt.redirect != null) {
				fields.put("redirect_uri", attempt.redirect);
			}
			if (attempt.scope != null) {
				fields.put("scope", attempt.scope);
			}
			HttpResponse<String> resp = Http.postForm(attempt.url, fields);
			JsonObject json = Http.json(resp);
			String access = Http.str(json, "access_token");
			if (access == null) {
				throw oauthError("refresh", attempt.name, resp, json);
			}
			String next = Http.str(json, "refresh_token");
			return new MsaToken(access, next == null ? refreshToken : next, attempt.client);
		} catch (AuthException e) {
			throw e;
		} catch (Exception e) {
			throw new AuthException("refresh", "HTTP", 0, attempt.name + " request failed: " + e.getMessage());
		}
	}

	private static MsaToken msaFromCookies(List<CookieParser.Cookie> cookies, String raw) throws AuthException {
		CookieManager manager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		seedCookies(manager, cookies);
		HttpClient client = Http.withCookieHandler(manager);
		String header = CookieParser.toHeader(cookies);

		AuthException last = null;
		for (String start : List.of(AUTHORIZE_CODE, AUTHORIZE_CODE_PROMPT, AUTHORIZE, SISU)) {
			try {
				MsaToken token = followOAuth(client, start, header);
				if (token != null) {
					if (token.refresh == null || token.refresh.isBlank()) {
						String embedded = firstRefreshIn(raw);
						if (embedded != null) {
							token = new MsaToken(token.access, embedded, token.client);
						}
					}
					return token;
				}
			} catch (AuthException e) {
				last = e;
			}
		}
		if (last != null) {
			throw last;
		}
		throw new AuthException("cookie", "NO_TOKEN", 0,
				"Cookies did not produce an MSA token. Need live login.live.com cookies (MSPAuth / MSPProf / WLSSID). They may be expired.");
	}

	private static MsaToken followOAuth(HttpClient client, String start, String cookieHeader) throws AuthException {
		try {
			String url = start;
			String header = cookieHeader;
			for (int i = 0; i < 18; i++) {
				MsaToken fromUrl = extractToken(url);
				if (fromUrl != null) {
					return fromUrl;
				}
				String oauthErr = extractGroup(ERROR_PARAM, url);
				if (oauthErr != null) {
					throw new AuthException("cookie", oauthErr, 0, "Microsoft OAuth error: " + oauthErr);
				}
				HttpResponse<String> resp = Http.get(client, url, header);
				header = mergeCookieHeader(header, resp);
				String loc = Http.location(resp);
				String body = resp.body() == null ? "" : resp.body();
				MsaToken extracted = extractToken(loc);
				if (extracted == null) {
					extracted = extractToken(body);
				}
				if (extracted != null) {
					return extracted;
				}
				String code = extractGroup(CODE, loc);
				if (code == null) {
					code = extractGroup(CODE, body);
				}
				if (code != null) {
					return exchangeCode(code);
				}
				oauthErr = extractGroup(ERROR_PARAM, loc == null ? "" : loc);
				if (oauthErr != null) {
					throw new AuthException("cookie", oauthErr, resp.statusCode(), "Microsoft OAuth error: " + oauthErr);
				}
				if (body.contains("name=\"PPFT\"") || body.contains("id=\"i0116\"")) {
					throw new AuthException("cookie", "LOGIN_FORM", resp.statusCode(),
							"Microsoft showed the login form. These cookies are expired or missing MSPAuth.");
				}
				if (loc == null || loc.isBlank()) {
					Matcher js = JS_URL.matcher(body);
					if (js.find()) {
						loc = js.group(1);
					} else {
						Matcher meta = META_REFRESH.matcher(body);
						if (meta.find()) {
							loc = meta.group(1).replace("&amp;", "&");
						} else {
							Matcher form = FORM_ACTION.matcher(body);
							if (form.find()) {
								loc = form.group(1).replace("&amp;", "&");
							}
						}
					}
				}
				if (loc == null || loc.isBlank()) {
					break;
				}
				url = Http.resolve(url, loc);
			}
			return null;
		} catch (AuthException e) {
			throw e;
		} catch (Exception e) {
			throw new AuthException("cookie", "FOLLOW", 0, "Cookie OAuth follow failed: " + e.getMessage());
		}
	}

	private static MsaToken exchangeCode(String code) throws AuthException {
		try {
			HttpResponse<String> resp = Http.postForm("https://login.live.com/oauth20_token.srf", Map.of(
					"client_id", XBOX_CLIENT_ID,
					"code", code,
					"grant_type", "authorization_code",
					"redirect_uri", XBOX_REDIRECT,
					"scope", XBOX_SCOPE
			));
			JsonObject json = Http.json(resp);
			String access = Http.str(json, "access_token");
			if (access == null) {
				throw oauthError("code", "xbox", resp, json);
			}
			return new MsaToken(access, Http.str(json, "refresh_token"), "xbox");
		} catch (AuthException e) {
			throw e;
		} catch (Exception e) {
			throw new AuthException("Authorization code exchange failed", e);
		}
	}

	private static MsaToken extractToken(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String decoded = text.replace("%3D", "=").replace("%26", "&");
		try {
			decoded = java.net.URLDecoder.decode(decoded, java.nio.charset.StandardCharsets.UTF_8);
		} catch (Exception ignored) {
		}
		String access = extractGroup(ACCESS, decoded);
		if (access == null) {
			return null;
		}
		return new MsaToken(access, extractGroup(REFRESH, decoded), "xbox");
	}

	private static String extractGroup(Pattern p, String text) {
		if (text == null) {
			return null;
		}
		Matcher m = p.matcher(text);
		return m.find() ? m.group(1) : null;
	}

	private static void seedCookies(CookieManager manager, List<CookieParser.Cookie> cookies) {
		String[] hosts = {
				"login.live.com", "login.microsoftonline.com", "account.live.com",
				"www.live.com", "sisu.xboxlive.com", "xboxlive.com"
		};
		for (CookieParser.Cookie c : cookies) {
			for (String host : hosts) {
				try {
					HttpCookie hc = new HttpCookie(c.name, c.value);
					hc.setPath(c.path == null || c.path.isBlank() ? "/" : c.path);
					if (!c.name.startsWith("__Host-")) {
						hc.setDomain(host);
					}
					hc.setSecure(true);
					hc.setVersion(0);
					manager.getCookieStore().add(URI.create("https://" + host + "/"), hc);
				} catch (Exception ignored) {
				}
			}
		}
	}

	private static String mergeCookieHeader(String existing, HttpResponse<?> resp) {
		String header = existing == null ? "" : existing;
		for (String set : resp.headers().allValues("Set-Cookie")) {
			int sc = set.indexOf(';');
			String nv = sc < 0 ? set : set.substring(0, sc);
			int eq = nv.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			String name = nv.substring(0, eq).trim();
			String value = nv.substring(eq + 1).trim();
			header = upsertCookie(header, name, value);
		}
		return header;
	}

	private static String upsertCookie(String header, String name, String value) {
		List<String> parts = new ArrayList<>();
		boolean replaced = false;
		if (header != null && !header.isBlank()) {
			for (String piece : header.split(";")) {
				String p = piece.trim();
				int eq = p.indexOf('=');
				if (eq <= 0) {
					continue;
				}
				String n = p.substring(0, eq).trim();
				if (n.equalsIgnoreCase(name)) {
					parts.add(name + "=" + value);
					replaced = true;
				} else {
					parts.add(p);
				}
			}
		}
		if (!replaced) {
			parts.add(name + "=" + value);
		}
		return String.join("; ", parts);
	}

	private static AuthResult complete(MsaToken msa, String cookies, AccountKind kind) throws AuthException {
		try {
			XboxPair xbl = xboxLive(msa.access);
			XboxPair xsts = xsts(xbl.token);
			String mcToken = minecraftToken(xsts.hash, xsts.token);
			JsonObject profile = fetchProfile(mcToken);
			String name = Http.str(profile, "name");
			String id = Http.str(profile, "id");
			if (name == null || id == null) {
				throw new AuthException("Logged into Microsoft, but this account has no Minecraft Java profile.");
			}
			return new AuthResult(name, Uuids.dash(id), mcToken, msa.refresh, cookies, msa.client, kind);
		} catch (AuthException e) {
			throw e;
		} catch (Exception e) {
			throw new AuthException("Xbox/Minecraft token exchange failed: " + e.getMessage(), e);
		}
	}

	private static XboxPair xboxLive(String msaAccess) throws Exception {
		List<String> tickets = new ArrayList<>();
		if (msaAccess.startsWith("d=") || msaAccess.startsWith("t=")) {
			tickets.add(msaAccess);
		} else {
			tickets.add("d=" + msaAccess);
			tickets.add(msaAccess);
			tickets.add("t=" + msaAccess);
		}
		AuthException last = null;
		for (String ticket : tickets) {
			try {
				JsonObject props = new JsonObject();
				props.addProperty("AuthMethod", "RPS");
				props.addProperty("SiteName", "user.auth.xboxlive.com");
				props.addProperty("RpsTicket", ticket);
				JsonObject body = new JsonObject();
				body.add("Properties", props);
				body.addProperty("RelyingParty", "http://auth.xboxlive.com");
				body.addProperty("TokenType", "JWT");
				HttpResponse<String> resp = Http.postJson("https://user.auth.xboxlive.com/user/authenticate", body.toString());
				return parseXbox(resp, "XBL");
			} catch (AuthException e) {
				last = e;
			}
		}
		throw last == null ? new AuthException("XBL", "XBL_FAIL", 0, "Xbox Live login failed") : last;
	}

	private static XboxPair xsts(String xblToken) throws Exception {
		JsonArray tokens = new JsonArray();
		tokens.add(xblToken);
		JsonObject props = new JsonObject();
		props.addProperty("SandboxId", "RETAIL");
		props.add("UserTokens", tokens);
		JsonObject body = new JsonObject();
		body.add("Properties", props);
		body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
		body.addProperty("TokenType", "JWT");
		HttpResponse<String> resp = Http.postJson("https://xsts.auth.xboxlive.com/xsts/authorize", body.toString());
		return parseXbox(resp, "XSTS");
	}

	private static XboxPair parseXbox(HttpResponse<String> resp, String stage) throws AuthException {
		JsonObject json = Http.json(resp);
		String token = Http.str(json, "Token");
		if (token == null) {
			String xerr = json.has("XErr") ? json.get("XErr").toString().replace(".0", "") : null;
			String code = xerr == null ? ("HTTP_" + resp.statusCode()) : xerr;
			String why = xerr == null
					? clip(resp.body(), 240)
					: xboxError(xerr);
			throw new AuthException(stage, code, resp.statusCode(), why);
		}
		try {
			String uhs = json.getAsJsonObject("DisplayClaims")
					.getAsJsonArray("xui")
					.get(0).getAsJsonObject()
					.get("uhs").getAsString();
			return new XboxPair(token, uhs);
		} catch (Exception e) {
			throw new AuthException(stage, "NO_UHS", resp.statusCode(), "Xbox response missing user hash");
		}
	}

	private static String minecraftToken(String uhs, String xsts) throws Exception {
		JsonObject body = new JsonObject();
		body.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xsts);
		body.addProperty("ensureLegacyEnabled", true);
		HttpResponse<String> resp = Http.postJson("https://api.minecraftservices.com/authentication/login_with_xbox", body.toString());
		JsonObject json = Http.json(resp);
		String token = Http.str(json, "access_token");
		if (token == null) {
			throw new AuthException("minecraft", "MC_TOKEN", resp.statusCode(),
					"login_with_xbox failed: " + clip(resp.body(), 240));
		}
		return token;
	}

	private static JsonObject fetchProfile(String mcToken) throws AuthException {
		try {
			HttpResponse<String> resp = Http.getAuth("https://api.minecraftservices.com/minecraft/profile", stripBearer(mcToken));
			JsonObject json = Http.json(resp);
			if (Http.str(json, "id") == null || Http.str(json, "name") == null) {
				String err = Http.str(json, "error") == null ? "NO_PROFILE" : Http.str(json, "error");
				String msg = Http.str(json, "errorMessage");
				if (msg == null) {
					msg = clip(resp.body(), 240);
				}
				if (resp.statusCode() == 404) {
					msg = "This Microsoft account has no Minecraft Java profile (not purchased / demo).";
				}
				throw new AuthException("profile", err, resp.statusCode(), msg);
			}
			return json;
		} catch (AuthException e) {
			throw e;
		} catch (Exception e) {
			throw new AuthException("profile", "HTTP", 0, "Profile lookup failed: " + e.getMessage());
		}
	}

	public static String stripBearer(String token) {
		if (token == null) {
			return "";
		}
		String t = token.trim();
		if (t.regionMatches(true, 0, "Bearer ", 0, 7)) {
			t = t.substring(7).trim();
		}
		int colon = t.lastIndexOf(':');
		if (colon > 20 && t.substring(colon + 1).replace("-", "").length() == 32) {
			t = t.substring(0, colon);
		}
		return t;
	}

	private static String firstRefreshIn(String raw) {
		if (raw == null) {
			return null;
		}
		Matcher m = REFRESH.matcher(raw);
		if (m.find()) {
			return m.group(1);
		}
		return null;
	}

	private static AuthException oauthError(String stage, String attempt, HttpResponse<String> resp, JsonObject json) {
		String err = Http.str(json, "error");
		String desc = Http.str(json, "error_description");
		if (desc != null) {
			desc = desc.replaceAll("\\r?\\n", " ").trim();
		}
		if (err == null) {
			err = resp.statusCode() >= 400 ? "HTTP_" + resp.statusCode() : "NO_ACCESS_TOKEN";
		}
		String why = (desc == null || desc.isBlank()) ? clip(resp.body(), 240) : desc;
		return new AuthException(stage + "/" + attempt, err, resp.statusCode(), why);
	}

	private static String clip(String s, int max) {
		if (s == null || s.isBlank()) {
			return "(empty response)";
		}
		String t = s.replaceAll("\\s+", " ").trim();
		return t.length() <= max ? t : t.substring(0, max) + "...";
	}

	private static String xboxError(String xerr) {
		if (xerr == null) {
			return "unknown";
		}
		return switch (xerr.replace(".0", "")) {
			case "2148916233" -> "No Xbox account on this Microsoft account";
			case "2148916235" -> "Xbox Live is banned in this country";
			case "2148916236", "2148916237" -> "Adult verification needed";
			case "2148916238" -> "Account is a child / needs family add";
			default -> "Xbox rejected the token";
		};
	}

	public static void runAsync(ThrowingTask task, Consumer<AuthException> onError, Consumer<AuthResult> onOk) {
		Thread t = new Thread(() -> {
			try {
				onOk.accept(task.run());
			} catch (AuthException e) {
				onError.accept(e);
			} catch (Exception e) {
				onError.accept(new AuthException("auth", "CRASH", 0, e.getMessage() == null ? e.toString() : e.getMessage()));
			}
		}, "NfaAuth");
		t.setDaemon(true);
		t.start();
	}

	@FunctionalInterface
	public interface ThrowingTask {
		AuthResult run() throws Exception;
	}

	public static final class DeviceCode {
		public final String userCode;
		public final String deviceCode;
		public final String verificationUri;
		public final String message;
		public final int interval;
		public final String client;

		public DeviceCode(String userCode, String deviceCode, String verificationUri, String message, int interval, String client) {
			this.userCode = userCode;
			this.deviceCode = deviceCode;
			this.verificationUri = verificationUri;
			this.message = message;
			this.interval = Math.max(3, interval);
			this.client = client;
		}

		static DeviceCode from(JsonObject json, String client) {
			String uri = Http.str(json, "verification_uri");
			if (uri == null) {
				uri = Http.str(json, "verification_url");
			}
			if (uri == null) {
				uri = "https://www.microsoft.com/link";
			}
			int interval = 5;
			try {
				if (json.has("interval")) {
					interval = json.get("interval").getAsInt();
				}
			} catch (Exception ignored) {
			}
			String msg = Http.str(json, "message");
			return new DeviceCode(
					Http.str(json, "user_code"),
					Http.str(json, "device_code"),
					uri,
					msg == null ? "Open " + uri + " and enter " + Http.str(json, "user_code") : msg,
					interval,
					client
			);
		}
	}

	private record MsaToken(String access, String refresh, String client) {
	}

	private record XboxPair(String token, String hash) {
	}

	private record OAuthAttempt(String name, String clientId, String url, String redirect, String scope, String client) {
	}
}
