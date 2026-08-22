package io.github.dicur3x.lss.components;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class FfmpegReleaseProvider implements ComponentReleaseProvider {
    static final URI VERSION_URI = URI.create("https://www.gyan.dev/ffmpeg/builds/release-version");
    static final URI CHECKSUM_URI = URI.create(
            "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip.sha256");
    static final URI DOWNLOAD_URI = URI.create(
            "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip");

    private static final long METADATA_LIMIT = 4096;
    private final DownloadClient downloadClient;

    public FfmpegReleaseProvider(DownloadClient downloadClient) {
        this.downloadClient = java.util.Objects.requireNonNull(downloadClient, "downloadClient");
    }

    @Override
    public ComponentRelease latest(BooleanSupplier cancellationRequested) throws ComponentException {
        try {
            String version = firstToken(downloadClient.getText(
                    VERSION_URI, METADATA_LIMIT, cancellationRequested));
            String checksum = firstToken(downloadClient.getText(
                    CHECKSUM_URI, METADATA_LIMIT, cancellationRequested)).toLowerCase();
            if (!version.matches("[0-9]+(?:\\.[0-9]+){1,3}")) {
                throw new ComponentException("The FFmpeg release source returned an invalid version.");
            }
            if (!checksum.matches("[0-9a-f]{64}")) {
                throw new ComponentException("The FFmpeg release source returned an invalid SHA-256 checksum.");
            }
            return new ComponentRelease(
                    ManagedComponent.FFMPEG,
                    version,
                    DOWNLOAD_URI,
                    Optional.of(checksum),
                    URI.create("https://ffmpeg.org/download.html"),
                    URI.create("https://ffmpeg.org/releases/ffmpeg-" + version + ".tar.xz"),
                    "GPLv3 Windows essentials build from gyan.dev"
            );
        } catch (CancellationException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("FFmpeg update check was interrupted");
        } catch (IOException exception) {
            throw new ComponentException("Could not check the latest FFmpeg version: "
                    + exception.getMessage(), exception);
        }
    }

    private static String firstToken(String value) throws ComponentException {
        return value.lines().map(String::strip).filter(line -> !line.isEmpty())
                .findFirst().map(line -> line.split("\\s+", 2)[0])
                .orElseThrow(() -> new ComponentException("The release source returned an empty response."));
    }
}
