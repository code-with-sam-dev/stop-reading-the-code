package dev.example.payments.acceptance;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A plain HTTP client, so these tests know nothing about how the service is built. */
final class Http {

    record Response(int status, String body) {
        long id() {
            Matcher m = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(body);
            if (!m.find()) throw new AssertionError("no id in " + body);
            return Long.parseLong(m.group(1));
        }
    }

    private final HttpClient client = HttpClient.newHttpClient();
    private final String base;

    Http(int port) {
        this.base = "http://localhost:" + port;
    }

    Response get(String path, String header, long who) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(base + path)).header(header, String.valueOf(who)).GET());
    }

    Response post(String path, String header, long who) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(base + path)).header(header, String.valueOf(who))
                .POST(HttpRequest.BodyPublishers.noBody()));
    }

    private Response send(HttpRequest.Builder request) throws Exception {
        HttpResponse<String> r = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(r.statusCode(), r.body());
    }
}
