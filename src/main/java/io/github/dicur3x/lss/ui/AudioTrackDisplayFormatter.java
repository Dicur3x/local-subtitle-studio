package io.github.dicur3x.lss.ui;

import io.github.dicur3x.lss.media.model.AudioTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static io.github.dicur3x.lss.ui.I18n.tr;

public final class AudioTrackDisplayFormatter {
    public String format(AudioTrack track) {
        List<String> parts = new ArrayList<>();
        parts.add(track.streamIndex() + ". " + trackName(track));
        parts.add(codecName(track.codec()));
        parts.add(channelName(track));
        track.bitrate().ifPresent(value -> parts.add(Math.round(value / 1_000d) + " " + tr("audio.kbps")));
        track.sampleRate().ifPresent(value -> parts.add(formatSampleRate(value)));
        return String.join("  ·  ", parts.stream().filter(part -> !part.isBlank()).toList());
    }

    private static String trackName(AudioTrack track) {
        String language = languageName(track.language());
        if (track.title().isBlank()) {
            return language;
        }
        if (language.equals(tr("audio.unknownLanguage"))) {
            return track.title();
        }
        if (track.title().toLowerCase(Locale.ROOT).contains(language.toLowerCase(Locale.ROOT))) {
            return track.title();
        }
        return language + " — " + track.title();
    }

    private static String languageName(String code) {
        if (code.isBlank() || code.equalsIgnoreCase("und")) {
            return tr("audio.unknownLanguage");
        }
        String normalized = code.replace('_', '-');
        String displayName = Locale.forLanguageTag(normalized).getDisplayLanguage(I18n.locale());
        if (displayName.isBlank()) {
            return code.toUpperCase(Locale.ROOT);
        }
        return Character.toUpperCase(displayName.charAt(0)) + displayName.substring(1);
    }

    private static String codecName(String codec) {
        if (codec.isBlank()) {
            return tr("audio.unknownCodec");
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
                case "mono" -> tr("audio.mono");
                case "stereo" -> tr("audio.stereo");
                default -> track.channelLayout();
            };
        }
        return switch (track.channels()) {
            case 1 -> tr("audio.mono");
            case 2 -> tr("audio.stereo");
            case 6 -> "5.1";
            case 8 -> "7.1";
            case 0 -> tr("audio.unknownChannels");
            default -> tr("audio.channelCount", track.channels());
        };
    }

    private static String formatSampleRate(int sampleRate) {
        if (sampleRate % 1_000 == 0) {
            return (sampleRate / 1_000) + " " + tr("audio.khz");
        }
        return String.format(Locale.ROOT, "%.1f %s", sampleRate / 1_000d, tr("audio.khz"));
    }
}
