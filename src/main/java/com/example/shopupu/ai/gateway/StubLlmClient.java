package com.example.shopupu.ai.gateway;

import com.example.shopupu.ai.model.ChatMessage;
import com.example.shopupu.ai.model.OutfitPlan;
import com.example.shopupu.ai.model.ParsedProductQuery;
import com.example.shopupu.ai.model.ReviewSummary;
import com.example.shopupu.catalog.entity.Gender;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic canned output (default, mirrors LoggingNotificationService):
 * dev and tests see the features working end-to-end without any API key.
 */
@Component
@ConditionalOnProperty(name = "ai.llm-provider", havingValue = "stub", matchIfMissing = true)
public class StubLlmClient implements LlmClient {

    private static final int TLDR_SNIPPET_LENGTH = 160;

    private static final Pattern MAX_PRICE = Pattern.compile(
            "(?:до|under|below|max|не дороже)\\s*(\\d{2,5})", Pattern.CASE_INSENSITIVE);

    /**
     * Garment types shoppers ask for that the catalog does not carry — the bot
     * must say so instead of quietly recommending the nearest unrelated item.
     * Every stem is word-start anchored (UNICODE_CHARACTER_CLASS makes \b work
     * for Cyrillic): "сорочка" must not trigger "очк", "sweatier" not "tie".
     */
    private static final Pattern UNAVAILABLE_GARMENTS = Pattern.compile(
            "\\b(?:галстук\\w*|костюм\\w*|рем[не]\\w*|туфл\\w*|каблук\\w*|сумк\\w*|шорт\\w*"
                    + "|купальник\\w*|носк\\w*|бель[её]|перчатк\\w*|кепк\\w*|шляп\\w*|очк\\w*"
                    + "|ties?|necktie\\w*|suits?|belts?|heels|bags?|handbags?|purses?|shorts"
                    + "|swimsuits?|bikinis?|socks|underwear|gloves|caps?|hats?|sunglasses)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

    @Override
    public Optional<ReviewSummary> summarizeReviews(String productTitle, List<String> reviewLines) {
        if (reviewLines == null || reviewLines.isEmpty()) {
            return Optional.empty();
        }
        String first = reviewLines.get(0);
        String tldr = "Customers left " + reviewLines.size() + " reviews of " + productTitle + ". "
                + first.substring(0, Math.min(first.length(), TLDR_SNIPPET_LENGTH));
        return Optional.of(new ReviewSummary(
                tldr, List.of("mentioned by buyers"), List.of(), ReviewSummary.Sentiment.MIXED));
    }

    @Override
    public Optional<ParsedProductQuery> parseCatalogQuery(String query) {
        // no extraction: the whole query flows into keyword search unchanged
        return Optional.of(new ParsedProductQuery(query, null, null, null, null, null));
    }

    @Override
    public Optional<OutfitPlan> planOutfit(List<ChatMessage> conversation, String catalogContext) {
        if (conversation == null || conversation.isEmpty()) {
            return Optional.empty();
        }
        String lastUserMessage = conversation.reversed().stream()
                .filter(turn -> "user".equals(turn.role()))
                .map(ChatMessage::content)
                .findFirst()
                .orElse("");
        return Optional.of(keywordPlan(lastUserMessage));
    }

    /**
     * Offline keyword planner. Also serves as the degradation path in
     * StylistService when a live provider errors out — recommendations must
     * survive any LLM outage.
     */
    public static OutfitPlan keywordPlan(String message) {
        OutfitPlan plan = basePlan(message);
        List<String> unavailable = detectUnavailable(message);
        if (unavailable.isEmpty()) {
            return plan;
        }
        String honesty = "We don't carry " + String.join(", ", unavailable)
                + " — here is the closest the catalog can do. ";
        return new OutfitPlan(honesty + plan.reply(), plan.slots(), unavailable);
    }

    private static OutfitPlan basePlan(String message) {
        String m = message == null ? "" : message.toLowerCase(Locale.ROOT);
        Gender gender = detectGender(m);
        BigDecimal maxPrice = detectMaxPrice(m);

        if (matches(m, "делов", "офици", "офис", "собесед", "костюм", "formal", "business", "office", "interview", "smart")) {
            return plan("A tailored blazer over a crisp shirt with pleated trousers reads sharp without trying too hard.",
                    slot("Blazer", "tailored wool blazer", gender, maxPrice),
                    slot("Trousers", "pleated formal trousers", gender, maxPrice),
                    slot("Shirt", "crisp oxford shirt", gender, maxPrice));
        }
        if (matches(m, "дожд", "rain", "wet")) {
            return plan("Something that shrugs off rain: a waterproof shell or a light trench over everyday basics.",
                    slot("Rain jacket", "waterproof hooded rain jacket", gender, maxPrice),
                    slot("Trench coat", "lightweight trench coat", gender, maxPrice),
                    slot("Jeans", "straight leg jeans", gender, maxPrice));
        }
        if (matches(m, "холод", "зим", "мороз", "тепл", "cold", "winter", "warm")) {
            return plan("Layer up: fine merino, a beanie and a scarf keep it warm without bulk.",
                    slot("Sweater", "merino wool crewneck sweater", gender, maxPrice),
                    slot("Beanie", "rib-knit wool beanie", gender, maxPrice),
                    slot("Scarf", "soft cashmere scarf", gender, maxPrice));
        }
        if (matches(m, "спорт", "зал", "трениров", "бег", "gym", "sport", "workout", "run")) {
            return plan("Easy pieces that move with you: a soft hoodie, a breathable tank and clean sneakers.",
                    slot("Hoodie", "soft cotton hoodie", gender, maxPrice),
                    slot("Tank", "ribbed cotton tank", gender, maxPrice),
                    slot("Sneakers", "minimal white leather sneakers", gender, maxPrice));
        }
        if (matches(m, "свидан", "вечерин", "плать", "date", "party", "dress")) {
            return plan("A wrap dress or a satin skirt works for an evening out; white sneakers keep it relaxed.",
                    slot("Dress", "wrap jersey midi dress", gender, maxPrice),
                    slot("Skirt", "satin midi skirt", gender, maxPrice),
                    slot("Sneakers", "minimal white leather sneakers", gender, maxPrice));
        }
        if (matches(m, "жар", "лето", "летн", "пляж", "summer", "beach", "hot")) {
            return plan("Light and breathable: washed linen and a ribbed tank handle the heat.",
                    slot("Shirt", "washed linen short sleeve shirt", gender, maxPrice),
                    slot("Tank", "ribbed cotton tank", gender, maxPrice),
                    slot("Bottoms", "relaxed cargo pants", gender, maxPrice));
        }
        return plan("A solid everyday base: an oxford shirt, straight jeans and minimal sneakers go anywhere.",
                slot("Shirt", "oversized oxford shirt", gender, maxPrice),
                slot("Jeans", "straight leg blue jeans", gender, maxPrice),
                slot("Sneakers", "minimal white leather sneakers", gender, maxPrice));
    }

    /**
     * Word-start stem matching. Plain contains() is a trap here: "нужен"
     * contains "жен", "training" contains "rain", "сказал" contains "зал".
     */
    private static boolean matches(String message, String... stems) {
        for (String stem : stems) {
            Pattern pattern = STEM_PATTERNS.computeIfAbsent(stem, s -> Pattern.compile(
                    "\\b" + Pattern.quote(s),
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS));
            if (pattern.matcher(message).find()) {
                return true;
            }
        }
        return false;
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Pattern> STEM_PATTERNS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Echoes the shopper's own words for garments the shop does not carry. */
    private static List<String> detectUnavailable(String message) {
        if (message == null || message.isBlank()) {
            return List.of();
        }
        List<String> found = new java.util.ArrayList<>();
        Matcher matcher = UNAVAILABLE_GARMENTS.matcher(message);
        while (matcher.find()) {
            String word = matcher.group();
            if (found.stream().noneMatch(existing -> existing.equalsIgnoreCase(word))) {
                found.add(word);
            }
        }
        return List.copyOf(found);
    }

    private static Gender detectGender(String message) {
        if (matches(message, "муж", "men", "him", "husband", "парн")) {
            return Gender.MEN;
        }
        if (matches(message, "жен", "women", "her", "wife", "девуш")) {
            return Gender.WOMEN;
        }
        return null;
    }

    private static BigDecimal detectMaxPrice(String message) {
        Matcher matcher = MAX_PRICE.matcher(message);
        return matcher.find() ? new BigDecimal(matcher.group(1)) : null;
    }

    private static OutfitPlan plan(String reply, OutfitPlan.OutfitSlot... slots) {
        return new OutfitPlan(reply, List.of(slots), List.of());
    }

    private static OutfitPlan.OutfitSlot slot(String label, String query, Gender gender, BigDecimal maxPrice) {
        return new OutfitPlan.OutfitSlot(label, query, gender, maxPrice);
    }
}
