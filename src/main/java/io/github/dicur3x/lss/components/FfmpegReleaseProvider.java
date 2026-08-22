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
    private static final long CHANGELOG_LIMIT = 3L * 1024 * 1024;
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
            URI changelogUri = URI.create(
                    "https://raw.githubusercontent.com/FFmpeg/FFmpeg/n" + version + "/Changelog");
            String releaseNotes = loadReleaseNotes(version, changelogUri, cancellationRequested);
            return new ComponentRelease(
                    ManagedComponent.FFMPEG,
                    version,
                    DOWNLOAD_URI,
                    Optional.of(checksum),
                    URI.create("https://github.com/FFmpeg/FFmpeg/blob/n" + version + "/Changelog"),
                    releaseNotes,
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

    private String loadReleaseNotes(
            String version,
            URI changelogUri,
            BooleanSupplier cancellationRequested
    ) throws InterruptedException {
        try {
            String changelog = downloadClient.getText(
                    changelogUri, CHANGELOG_LIMIT, cancellationRequested);
            String section = versionSection(changelog, version);
            if (!section.isBlank()) {
                return section;
            }
        } catch (IOException ignored) {
            // Version and package checks remain useful when the optional changelog host is unavailable.
        }
        return "FFmpeg " + version + " is a stable point release. FFmpeg states that point releases "
                + "contain important bug fixes rather than new features. Detailed release notes are temporarily "
                + "unavailable; use the official-source button to view the upstream changelog.";
    }

    static String versionSection(String changelog, String version) {
        String normalized = changelog == null ? "" : changelog.replace("\r\n", "\n");
        String heading = "version " + version + ":";
        int start = normalized.indexOf(heading);
        if (start < 0) {
            return "";
        }
        int searchFrom = start + heading.length();
        java.util.regex.Matcher next = java.util.regex.Pattern.compile("(?m)^version [^:\\r\\n]+:")
                .matcher(normalized);
        int end = normalized.length();
        if (next.find(searchFrom)) {
            end = next.start();
        }
        return normalized.substring(start, end).strip();
    }

    private static String firstToken(String value) throws ComponentException {
        return value.lines().map(String::strip).filter(line -> !line.isEmpty())
                .findFirst().map(line -> line.split("\\s+", 2)[0])
                .orElseThrow(() -> new ComponentException("The release source returned an empty response."));
    }
}
