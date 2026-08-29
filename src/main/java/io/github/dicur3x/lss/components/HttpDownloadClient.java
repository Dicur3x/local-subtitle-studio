package io.github.dicur3x.lss.components;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class HttpDownloadClient implements DownloadClient {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final long PROGRESS_STEP_BYTES = 1024 * 1024;
    private static final int MAXIMUM_ATTEMPTS = 3;

    private final HttpClient httpClient;

    public HttpDownloadClient() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    @Override
    public String getText(URI uri, long maximumBytes, BooleanSupplier cancellationRequested)
            throws IOException, InterruptedException {
        validateRequest(uri, maximumBytes, cancellationRequested);
        HttpResponse<InputStream> response = send(uri, cancellationRequested);
        ensureSuccessful(uri, response);
        ensureSecureResponse(response);
        try (InputStream input = response.body()) {
            byte[] bytes = readLimited(input, maximumBytes, cancellationRequested);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    @Override
    public DownloadResult download(
            URI uri,
            Path destination,
            long maximumBytes,
            String phase,
            OperationProgress progress,
            BooleanSupplier cancellationRequested
    ) throws IOException, InterruptedException {
        validateRequest(uri, maximumBytes, cancellationRequested);
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(progress, "progress");

        HttpResponse<InputStream> response = send(uri, cancellationRequested);
        ensureSuccessful(uri, response);
        ensureSecureResponse(response);
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        if (contentLength > maximumBytes) {
            response.body().close();
            throw new IOException("Download is larger than the allowed limit");
        }

        Files.createDirectories(destination.toAbsolutePath().normalize().getParent());
        MessageDigest digest = sha256();
        long downloaded = 0;
        long lastReported = 0;
        try (InputStream input = response.body();
             var output = Files.newOutputStream(destination,
                     StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                checkCancellation(cancellationRequested);
                downloaded += read;
                if (downloaded > maximumBytes) {
                    throw new IOException("Download exceeded the allowed limit");
                }
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                if (downloaded - lastReported >= PROGRESS_STEP_BYTES) {
                    progress.update(phase, downloaded, contentLength);
                    lastReported = downloaded;
                }
            }
        }
        progress.update(phase, downloaded, contentLength);
        return new DownloadResult(downloaded, HexFormat.of().formatHex(digest.digest()));
    }

    private HttpResponse<InputStream> send(
            URI uri,
            BooleanSupplier cancellationRequested
    ) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(30))
                .header("User-Agent", "LocalSubtitleStudio/0.1 (+https://github.com/Dicur3x/local-subtitle-studio)")
                .GET()
                .build();
        for (int attempt = 1; attempt <= MAXIMUM_ATTEMPTS; attempt++) {
            checkCancellation(cancellationRequested);
            HttpResponse<InputStream> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            if (!isTemporaryFailure(response.statusCode()) || attempt == MAXIMUM_ATTEMPTS) {
                return response;
            }
            response.body().close();
            Thread.sleep(250L * attempt);
        }
        throw new IllegalStateException("HTTP retry loop ended unexpectedly");
    }

    private static boolean isTemporaryFailure(int statusCode) {
        return statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private static void validateRequest(URI uri, long maximumBytes, BooleanSupplier cancellationRequested) {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Only HTTPS downloads are allowed");
        }
        if (maximumBytes <= 0) {
            throw new IllegalArgumentException("maximumBytes must be positive");
        }
        checkCancellation(cancellationRequested);
    }

    private static void ensureSuccessful(
            URI uri,
            HttpResponse<InputStream> response
    ) throws IOException {
        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            response.body().close();
            throw new IOException("Download failed with HTTP " + statusCode + " from " + uri.getHost());
        }
    }

    private static void ensureSecureResponse(HttpResponse<InputStream> response) throws IOException {
        if (!"https".equalsIgnoreCase(response.uri().getScheme())) {
            response.body().close();
            throw new IOException("Download was redirected to an insecure connection");
        }
    }

    private static byte[] readLimited(
            InputStream input,
            long maximumBytes,
            BooleanSupplier cancellationRequested
    ) throws IOException {
        var output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            checkCancellation(cancellationRequested);
            total += read;
            if (total > maximumBytes) {
                throw new IOException("Response exceeded the allowed limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static void checkCancellation(BooleanSupplier cancellationRequested) {
        if (Thread.currentThread().isInterrupted() || cancellationRequested.getAsBoolean()) {
            throw new CancellationException("Download was cancelled");
        }
    }
}
