package com.spendlens.ai;

import com.spendlens.statement.Category;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic fallback used when Claude is not configured or a request to it fails.
 * Order: income by sign → merchant keywords → MCC code ranges → OTHER.
 */
@Component
public class RuleBasedCategorizer {

    private static final Map<Category, List<String>> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put(Category.SUBSCRIPTIONS, List.of("netflix", "spotify", "youtube", "apple.com", "icloud",
                "google one", "chatgpt", "openai", "claude", "anthropic", "github", "megogo", "sweet.tv", "patreon"));
        KEYWORDS.put(Category.TRANSPORT, List.of("uber", "bolt", "uklon", "taxi", "таксі", "metro", "метро",
                "wog", "okko", "socar", "upg", "parking", "паркування"));
        KEYWORDS.put(Category.GROCERIES, List.of("silpo", "сільпо", "atb", "атб", "novus", "fora", "фора",
                "metro cash", "auchan", "ашан", "varus", "варус", "eko market"));
        KEYWORDS.put(Category.RESTAURANTS, List.of("coffee", "кава", "cafe", "кафе", "restaurant", "ресторан",
                "mcdonald", "kfc", "puzata", "пузата", "sushi", "pizza", "піца", "glovo", "bolt food"));
        KEYWORDS.put(Category.HEALTH, List.of("apteka", "аптека", "pharm", "clinic", "клініка", "dental",
                "стоматолог", "sport life", "gym"));
        KEYWORDS.put(Category.UTILITIES, List.of("kyivstar", "київстар", "vodafone", "lifecell", "комунал",
                "yasno", "ясно", "нафтогаз", "водоканал", "internet", "інтернет"));
        KEYWORDS.put(Category.ENTERTAINMENT, List.of("cinema", "кіно", "multiplex", "planeta kino", "steam",
                "playstation", "concert", "концерт", "karabas", "театр"));
        KEYWORDS.put(Category.TRAVEL, List.of("booking", "airbnb", "hotel", "готель", "ukrzaliznytsia",
                "укрзалізниця", "airline", "ryanair", "wizz"));
        KEYWORDS.put(Category.TRANSFERS, List.of("переказ", "transfer", "на картку", "to card", "поповнення"));
        KEYWORDS.put(Category.CASH, List.of("atm", "банкомат", "cash withdrawal", "видача готівки"));
        KEYWORDS.put(Category.SHOPPING, List.of("rozetka", "розетка", "prom.ua", "epicentr", "епіцентр",
                "comfy", "foxtrot", "zara", "h&m", "amazon", "aliexpress", "nova poshta", "нова пошта"));
    }

    public List<CategoryAssignment> categorize(List<TransactionInput> inputs) {
        return inputs.stream().map(t -> new CategoryAssignment(t.index(), categorize(t))).toList();
    }

    public Category categorize(TransactionInput t) {
        if (t.amount() != null && t.amount().signum() > 0) {
            return Category.INCOME;
        }
        String text = t.description() == null ? "" : t.description().toLowerCase(Locale.ROOT);
        for (var entry : KEYWORDS.entrySet()) {
            if (entry.getValue().stream().anyMatch(k -> containsWord(text, k))) {
                return entry.getKey();
            }
        }
        return t.mcc() == null ? Category.OTHER : byMcc(t.mcc());
    }

    /**
     * True if {@code keyword} occurs in {@code text} starting at a word boundary. Plain substring
     * matching is wrong here: "приватбанк" contains "атб", which is also a supermarket chain.
     */
    static boolean containsWord(String text, String keyword) {
        for (int i = text.indexOf(keyword); i >= 0; i = text.indexOf(keyword, i + 1)) {
            if (i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1))) {
                return true;
            }
        }
        return false;
    }

    /** Maps ISO 18245 merchant category codes to our categories. */
    static Category byMcc(int mcc) {
        return switch (mcc) {
            case 5411, 5422, 5441, 5451, 5462, 5499 -> Category.GROCERIES;
            case 5811, 5812, 5813, 5814 -> Category.RESTAURANTS;
            case 4111, 4121, 4131, 4784, 4789, 5172, 5541, 5542, 7523 -> Category.TRANSPORT;
            case 4899, 5815, 5816, 5817, 5818, 5968 -> Category.SUBSCRIPTIONS;
            case 5912, 5975, 5976, 8011, 8021, 8031, 8042, 8043, 8049, 8050, 8062, 8071, 8099, 7997 -> Category.HEALTH;
            case 4812, 4814, 4816, 4900 -> Category.UTILITIES;
            case 7832, 7841, 7922, 7929, 7932, 7933, 7991, 7994, 7996, 7998, 7999 -> Category.ENTERTAINMENT;
            case 4411, 4511, 4582, 4722, 7011, 7012, 7512 -> Category.TRAVEL;
            case 6010, 6011 -> Category.CASH;
            case 4829, 6012, 6050, 6051, 6211, 6536, 6537, 6538, 6540 -> Category.TRANSFERS;
            default -> {
                if (mcc >= 3000 && mcc <= 3999) yield Category.TRAVEL;      // airlines, car rental, hotels
                if (mcc >= 5200 && mcc <= 5999) yield Category.SHOPPING;    // retail
                yield Category.OTHER;
            }
        };
    }
}
