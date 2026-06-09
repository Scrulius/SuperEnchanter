/* SuperEnchanter - Author: Scrulius (GitHub) */
package dev.scrulius.superenchanter.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Static utility class for MiniMessage operations.
 * <p>
 * Provides convenient methods for deserializing MiniMessage strings,
 * parsing with placeholders, and stripping tags.
 * </p>
 */
public final class MiniMessageUtil {

    /** Shared MiniMessage instance — thread-safe and reusable. */
    private static final MiniMessage mm = MiniMessage.miniMessage();

    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private MiniMessageUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Converts legacy color codes (§ and &) to their MiniMessage equivalents safely.
     */
    public static @NotNull String replaceLegacy(@NotNull String text) {
        if (!text.contains("§") && !text.contains("&")) {
            return text;
        }

        // Deserialize using Adventure's legacy serializers
        Component legacyComp = LEGACY_SECTION.deserialize(
                LEGACY_AMPERSAND.serialize(
                        LEGACY_AMPERSAND.deserialize(text)
                ).replace("&", "§")
        );

        // Serialize back to MiniMessage format (this escapes < and >)
        String mmText = mm.serialize(legacyComp);

        // Unescape \< and \> to allow MiniMessage tags to function together with legacy
        return mmText.replace("\\<", "<").replace("\\>", ">");
    }

    /**
     * Deserializes a MiniMessage string into an Adventure {@link Component}.
     *
     * @param miniMessage the MiniMessage-formatted string
     * @return the parsed {@link Component}
     */
    public static @NotNull Component parse(@NotNull String miniMessage) {
        return mm.deserialize(replaceLegacy(miniMessage));
    }

    /**
     * Deserializes a MiniMessage string with the given tag resolvers.
     *
     * @param miniMessage the MiniMessage-formatted string
     * @param resolvers   additional tag resolvers for placeholders
     * @return the parsed {@link Component}
     */
    public static @NotNull Component parse(@NotNull String miniMessage, @NotNull TagResolver... resolvers) {
        return mm.deserialize(replaceLegacy(miniMessage), resolvers);
    }

    /**
     * Deserializes a MiniMessage string, replacing each map entry as a
     * raw string substitution. Supports both {@code {key}} and {@code <key>}
     * placeholder styles in the input string.
     *
     * @param miniMessage  the MiniMessage-formatted string
     * @param placeholders map of placeholder keys to their replacement values
     * @return the parsed {@link Component}
     */
    public static @NotNull Component parse(@NotNull String miniMessage, @NotNull Map<String, String> placeholders) {
        String processed = miniMessage;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            // Replace {key} style placeholders via raw string substitution
            processed = processed.replace(entry.getKey(), entry.getValue());
        }
        return mm.deserialize(replaceLegacy(processed));
    }

    /**
     * Strips all MiniMessage tags from the input string, returning plain text.
     *
     * @param input the MiniMessage-formatted string
     * @return the string with all tags removed
     */
    public static @NotNull String stripTags(@NotNull String input) {
        return mm.stripTags(input);
    }
}
