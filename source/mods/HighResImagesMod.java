package forge.util;

/** CdnUuidCache appends .jpg regardless of image variant. PNG needs .png. */
public final class HighResImagesMod {
    public static String cdnUrl(String url) {
        if (url != null && url.startsWith("https://cards.scryfall.io/png/"))
            return url.replaceAll("\\.jpg(?=\\?|$)", ".png");
        return url;
    }
}
