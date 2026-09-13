package dev.nfaswitcher.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;

public final class Http {
	public static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

	public static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(20))
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	private Http() {
	}

	public static HttpClient withCookieHandler(java.net.CookieHandler handler) {
		return HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(20))
				.followRedirects(HttpClient.Redirect.NEVER)
				.cookieHandler(handler)
				.build();
	}

	public static String form(Map<String, String> fields) {
		StringJoiner joiner = new StringJoiner("&");
		for (Map.Entry<String, String> e : fields.entrySet()) {
			joiner.add(enc(e.getKey()) + "=" + enc(e.getValue() == null ? "" : e.getValue()));
		}
		return joiner.toString();
	}

	public static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	public static HttpResponse<String> get(HttpClient client, String url, String cookieHeader) throws IOException, InterruptedException {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(30))
				.header("User-Agent", UA)
				.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
				.GET();
		if (cookieHeader != null && !cookieHeader.isBlank()) {
			b.header("Cookie", cookieHeader);
		}
		return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
	}

	public static HttpResponse<String> postForm(String url, Map<String, String> fields) throws IOException, InterruptedException {
		return post(CLIENT, url, "application/x-www-form-urlencoded", form(fields), null, false);
	}

	public static HttpResponse<String> postJson(String url, String json) throws IOException, InterruptedException {
		boolean xbox = url.contains("xboxlive.com");
		return post(CLIENT, url, "application/json", json, null, xbox);
	}

	public static HttpResponse<String> postJsonAuth(String url, String json, String bearer) throws IOException, InterruptedException {
		boolean xbox = url.contains("xboxlive.com");
		return post(CLIENT, url, "application/json", json, bearer, xbox);
	}

	public static HttpResponse<String> getAuth(String url, String bearer) throws IOException, InterruptedException {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(30))
				.header("User-Agent", UA)
				.header("Accept", "application/json")
				.GET();
		if (bearer != null) {
			b.header("Authorization", "Bearer " + bearer);
		}
		return CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
	}

	public static HttpResponse<String> post(HttpClient client, String url, String contentType, String body, String bearer)
			throws IOException, InterruptedException {
		return post(client, url, contentType, body, bearer, false);
	}

	public static HttpResponse<String> post(HttpClient client, String url, String contentType, String body, String bearer, boolean xbox)
			throws IOException, InterruptedException {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(30))
				.header("User-Agent", UA)
				.header("Content-Type", contentType)
				.header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
		if (bearer != null) {
			b.header("Authorization", "Bearer " + bearer);
		}
		if (xbox) {
			b.header("x-xbl-contract-version", "1");
		}
		return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
	}

	public static JsonObject json(HttpResponse<String> response) {
		return json(response.body());
	}

	public static JsonObject json(String body) {
		if (body == null || body.isBlank()) {
			return new JsonObject();
		}
		try {
			JsonElement el = JsonParser.parseString(body);
			return el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
		} catch (Exception e) {
			return new JsonObject();
		}
	}

	public static String str(JsonObject obj, String key) {
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
			return null;
		}
		return obj.get(key).getAsString();
	}

	public static String location(HttpResponse<?> response) {
		return response.headers().firstValue("Location").orElse(null);
	}

	public static String resolve(String base, String loc) {
		if (loc == null) {
			return null;
		}
		return URI.create(base).resolve(loc).toString();
	}
}
