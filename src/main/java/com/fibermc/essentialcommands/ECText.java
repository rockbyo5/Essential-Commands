package com.fibermc.essentialcommands;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.jpcode.eccore.util.TextUtil;
import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.client.font.TextVisitFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.*;
import net.minecraft.util.JsonHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import static com.fibermc.essentialcommands.EssentialCommands.*;

public abstract class ECText {

//    Map<String, String> textRegistry;
    private static final Gson GSON = new Gson();
    private static final Pattern TOKEN_PATTERN = Pattern.compile("%(\\d+\\$)?[\\d.]*[df]");
    public static final String DEFAULT_LANGUAGE = "en_us";

    private static volatile ECText instance = create(CONFIG.LANGUAGE);
    private static MinecraftServer _server;

    private ECText() {}

    static {
        BACKING_CONFIG.LANGUAGE.changeEvent.register((langId) -> {
            instance = create(langId);
        });
    }

    public static void init(MinecraftServer server) {
        _server = server;
    }

    public static MutableText literal(String str) {
        return Text.literal(str).setStyle(CONFIG.FORMATTING_DEFAULT);
    }

    public static MutableText accent(String str) {
        return Text.literal(str).setStyle(CONFIG.FORMATTING_ACCENT);
    }

    public static MutableText error(String str) {
        return Text.literal(str).setStyle(CONFIG.FORMATTING_ERROR);
    }

    private static ECText create(String langId) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        Objects.requireNonNull(builder);
        BiConsumer<String, String> biConsumer = builder::put;
//        String var2 = "/assets/minecraft/lang/en_us.json";
        final String resourceFString = "/assets/essential_commands/lang/%s.json";
        final String resourceLocation = String.format(resourceFString, langId);
        try {
            InputStream inputStream = ECText.class.getResourceAsStream(resourceLocation);
            if (inputStream == null) {
                LOGGER.info(String.format("No EC lang file for the language '%s' found. Defulting to 'en_us'.", langId));
                inputStream = ECText.class.getResourceAsStream(String.format(resourceFString, "en_us"));
            }

            try {
                load(inputStream, biConsumer);
            } catch (Throwable var7) {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (Throwable var6) {
                        var7.addSuppressed(var6);
                    }
                }

                throw var7;
            }

            if (inputStream != null) {
                inputStream.close();
            }
        } catch (JsonParseException | IOException var8) {
            LOGGER.error("Couldn't read strings from {}", resourceLocation, var8);
        }

        final Map<String, String> map = builder.build();
        return new ECText() {
            public String get(String key) {
                return map.getOrDefault(key, key);
            }

            // Literals
            public MutableText getTextLiteral(String key, TextFormatType textFormatType) {
                return Text.literal(get(key)).setStyle(textFormatType.getStyle());
            }

            public MutableText getText(String key) {
                return getTextLiteral(key, TextFormatType.Default);
            }

            public MutableText getText(String key, TextFormatType textFormatType) {
                return getTextLiteral(key, textFormatType);
            }

            // Interpolated
            public MutableText getText(String key, Text... args) {
                return getTextInternal(key, TextFormatType.Default, args);
            }

            public MutableText getText(String key, TextFormatType textFormatType,  Text... args) {
                return getTextInternal(key, textFormatType, args);
            }

            private Placeholders.PlaceholderGetter placeholderGetterForContext(TextFormatType textFormatType, List<MutableText> args) {
                return (placeholderId) ->
                    (ctx, abc) -> {
                        var idxAndFormattingCode = placeholderId.split(":");
                        if (idxAndFormattingCode.length < 1) {
                            throw new IllegalArgumentException("lang string placeholder did not contain an index");
                        }

                        var firstToken = idxAndFormattingCode[0];
                        var text = switch (firstToken) {
                            case "l" -> {
                                if (idxAndFormattingCode.length < 2) {
                                    throw new IllegalArgumentException("Specified lang interpolation prefix ('l'), but no lang key was provided. Expected the form: 'l:lang.key.here'. Received: " + placeholderId);
                                }
                                yield getTextLiteral(idxAndFormattingCode[1], textFormatType);
                            }

                            default -> args.get(Integer.parseInt(idxAndFormattingCode[0]));
                        };
                        return PlaceholderResult.value(text);
                    };
            }

            public MutableText getTextInternal(String key, TextFormatType textFormatType,  Text... args) {
                var argsList = Arrays.stream(args).map(Text::copy).toList();
                var placeholderGetter = placeholderGetterForContext(textFormatType, argsList);
                var retVal = Placeholders.parseText(
                    Text.literal(get(key)),
                    PlaceholderContext.of(_server),
                    Placeholders.PREDEFINED_PLACEHOLDER_PATTERN,
                    placeholderGetter);

                var retValSiblings = retVal.getSiblings();
                return retValSiblings.size() == 0
                    ? retVal.copy()
                    : TextUtil.concat(retVal.getSiblings().stream()
                        .map(txt -> argsList.contains(txt)
                            ? txt
                            : txt.copy().setStyle(textFormatType.getStyle()))
                        .toArray(Text[]::new));
            }

            // Other stuff
            public MutableText getText(String key, Object... args) {
                return ECText.literal(String.format(get(key), args));
            }

            public boolean hasTranslation(String key) {
                return map.containsKey(key);
            }

            public boolean isRightToLeft() {
                return false;
            }

            public OrderedText reorder(StringVisitable text) {
                return (visitor) -> {
                    return text.visit((style, string) -> {
                        return TextVisitFactory.visitFormatted(string, style, visitor) ? Optional.empty() : StringVisitable.TERMINATE_VISIT;
                    }, Style.EMPTY).isPresent();
                };
            }
        };
    }

    public static void load(InputStream inputStream, BiConsumer<String, String> entryConsumer) {
        JsonObject jsonObject = (JsonObject)GSON.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), JsonObject.class);
        Iterator var3 = jsonObject.entrySet().iterator();

        while(var3.hasNext()) {
            Map.Entry<String, JsonElement> entry = (Map.Entry)var3.next();
            String string = TOKEN_PATTERN.matcher(JsonHelper.asString((JsonElement)entry.getValue(), (String)entry.getKey())).replaceAll("%$1s");
            entryConsumer.accept((String)entry.getKey(), string);
        }

    }

    public static ECText getInstance() {
        return instance;
    }

//    public static String get(String key) {
//        Language
//    }
    public abstract String get(String key);

    public abstract MutableText getText(String key, Text... args);

    public abstract MutableText getText(String key, TextFormatType textFormatType,  Text... args);

    public abstract MutableText getText(String key, Object... args);

    public abstract MutableText getText(String key);

    public abstract boolean hasTranslation(String key);

    public abstract boolean isRightToLeft();

    public abstract OrderedText reorder(StringVisitable text);

    public List<OrderedText> reorder(List<StringVisitable> texts) {
        return (List<OrderedText>)texts.stream().map(this::reorder).collect(ImmutableList.toImmutableList());
    }

}
