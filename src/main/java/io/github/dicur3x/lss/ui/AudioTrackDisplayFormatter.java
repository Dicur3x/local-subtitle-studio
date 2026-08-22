package io.github.dicur3x.lss.ui;

import io.github.dicur3x.lss.media.model.AudioTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AudioTrackDisplayFormatter {
    public String format(AudioTrack track) {
        List<String> parts = new ArrayList<>();
        parts.add(track.streamIndex() + ". " + trackName(track));
        parts.add(codecName(track.codec()));
        parts.add(channelName(track));
        track.bitrate().ifPresent(value -> parts.add(Math.round(value / 1_000d) + " kbps"));
        track.sampleRate().ifPresent(value -> parts.add(formatSampleRate(value)));
        return String.join("  ·  ", parts.stream().filter(part -> !part.isBlank()).toList());
    }

    private static String trackName(AudioTrack track) {
        String language = languageName(track.language());
        if (track.title().isBlank()) {
            return language;
        }
        if (language.equals("Unknown language")) {
            return track.title();
        }
        if (track.title().toLowerCase(Locale.ROOT).contains(language.toLowerCase(Locale.ROOT))) {
            return track.title();
        }
        return language + " — " + track.title();
    }

    private static String languageName(String code) {
        if (code.isBlank() || code.equalsIgnoreCase("und")) {
            return "Unknown language";
        }
        String normalized = code.replace('_', '-');
        String displayName = Locale.forLanguageTag(normalized).getDisplayLanguage(Locale.ENGLISH);
        if (displayName.isBlank()) {
            return code.toUpperCase(Locale.ROOT);
        }
        return Character.toUpperCase(displayName.charAt(0)) + displayName.substring(1);
    }

    private static String codecName(String codec) {
        if (codec.isBlank()) {
            return "Unknown codec";
        }
        return switch (codec.toLowerCase(Locale.ROOT)) {
            case "eac3" -> "E-AC3";
            case "ac3" -> "AC3";
            case "aac" -> "AAC";
            case "dts" -> "DTS";
            case "truehd" -> "TrueHD";
            case "opus" -> "Opus";
            default -> codec.toUpperCase(Locale.ROOT);
        };
    }

    private static String channelName(AudioTrack track) {
        if (!track.channelLayout().isBlank()) {
            return switch (track.channelLayout().toLowerCase(Locale.ROOT)) {
                case "mono" -> "Mono";
                case "stereo" -> "Stereo";
                default -> track.channelLayout();
            };
        }
        return switch (track.channels()) {
            case 1 -> "Mono";
            case 2 -> "Stereo";
            case 6 -> "5.1";
            case 8 -> "7.1";
            case 0 -> "Unknown channels";
            default -> track.channels() + " channels";
        };
    }

    private static String formatSampleRate(int sampleRate) {
        if (sampleRate % 1_000 == 0) {
            return (sampleRate / 1_000) + " kHz";
        }
        return String.format(Locale.ROOT, "%.1f kHz", sampleRate / 1_000d);
    }
}
