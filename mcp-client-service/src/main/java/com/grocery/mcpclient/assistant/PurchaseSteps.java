package com.grocery.mcpclient.assistant;

import com.grocery.mcpclient.tool.ChangeRequest;
import com.grocery.mcpclient.tool.ToolCallRecorder;
import com.grocery.mcpclient.tool.ToolCallRecorder.ToolCall;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * "I want to buy this "Harpic Toilet Cleaner" product. Can you order it for me?": the small model found the product,
 * but then only asked or announced ("Would you like me to add it to your cart and prepare the order?"), also after
 * being told to act. "Can you add one more item for Aashirvaad Whole Wheat Atta in my cart?": it wrote "Added ... your
 * total is ₹555.75" without calling the tool, twice. "Can you remove Harpic Toilet Cleaner from my cart?": it wrote that
 * the product was removed, without a tool. When the customer's words are that clear, ChatServiceImpl takes the step
 * itself; this class says which product.
 */
public final class PurchaseSteps {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** The tools whose results name products: as "data" (a list or one product), or as the cart's lines ("data.items") */
    private static final Set<String> PRODUCT_TOOLS = Set.of("searchProducts", "getProductDetails");
    private static final String CART_TOOL = "getMyCart";
    private static final String REMOVAL_TOOL = ChatActions.PREPARE_REMOVAL_TOOL;

    private PurchaseSteps() {
    }

    /**
     * The product to add: the one product found by a tool of this answer (a search, a product's details, or a line of
     * the cart for "one more ...") whose FULL name is in the customer's words ("Harpic Toilet Cleaner" in "i want to buy
     * this "Harpic Toilet Cleaner" product"). Null when there is none, or more than one: then nothing is added in code,
     * and the model asks the customer.
     *
     * @param customerWords the customer's message and the one before it
     */
    public static String productToAdd(String customerWords, ToolCallRecorder recorder) {
        List<String> saidWords = wordsOf(customerWords);
        Set<String> named = new LinkedHashSet<>();
        for (ToolCall call : recorder.calls()) {
            if (!"OK".equals(call.status())) {
                continue;
            }
            JsonNode products = PRODUCT_TOOLS.contains(call.toolName()) ? dataOf(call.result())
                    : CART_TOOL.equals(call.toolName()) ? dataOf(call.result()).path("items")
                    : JSON.missingNode();
            for (JsonNode product : products.isArray() ? products : List.of(products)) {
                String name = text(product, "name");
                if (name != null && !wordsOf(name).isEmpty() && namedIn(saidWords, wordsOf(name), false)) {
                    named.add(name);
                }
            }
        }
        return named.size() == 1 ? named.iterator().next() : null;
    }

    /**
     * The products to take out of the cart: every line of the cart (getMyCart of this answer) whose FULL name is in the
     * customer's message ("Harpic Toilet Cleaner" in "Can you remove Harpic Toilet Cleaner from my cart?"), also as a
     * plural ("Tomato" and "Potato" in "Remove the tomatoes and the potatoes"). Two rules keep the code from guessing:
     * - a product named after "keep", "except", "but not" ... stays ("Remove Tomato and Potato, but keep Onion");
     * - a short name that is part of a longer line's name is left out when the customer also said another word of that
     *   longer name: in "remove the tomato ketchup" the customer means "Kissan Fresh Tomato Ketchup", not "Tomato".
     * Empty when the message names no line in full: then the model's tool call (or its question) decides.
     *
     * @param message only the customer's message: a product named before ("add Harpic") is not the one to remove now
     */
    public static List<String> cartLinesToRemove(String message, ToolCallRecorder recorder) {
        List<String> saidWords = wordsOf(ChangeRequest.withoutKeptProducts(message));
        List<String> lines = cartLineNames(recorder);
        List<String> named = new ArrayList<>();
        for (String line : lines) {
            if (namedIn(saidWords, wordsOf(line), true) && !partOfALongerLineTheyNamed(line, lines, saidWords)) {
                named.add(line);
            }
        }
        return named;
    }

    /**
     * Whether a removal of this cart line was already prepared in this answer (an OK prepareMyCartRemoval result whose
     * line has this name), by the model or by the code
     */
    public static boolean removalPrepared(String product, ToolCallRecorder recorder) {
        return recorder.calls().stream()
                .filter(call -> REMOVAL_TOOL.equals(call.toolName()) && "OK".equals(call.status()))
                .flatMap(call -> namesOf(dataOf(call.result()).path("items")).stream())
                .anyMatch(name -> name.equalsIgnoreCase(product));
    }

    /**
     * The question for prepareMyCartRemoval's CHOOSE (several lines of the cart match, e.g. two pack sizes): "Which one
     * do you want to take out of your cart: Harpic Toilet Cleaner (500 ml) or Harpic Toilet Cleaner (1 l)?", from the
     * products the tool listed. Null when it listed fewer than two.
     */
    public static String whichOneToRemove(ToolCall choose) {
        List<String> options = new ArrayList<>();
        dataOf(choose.result()).path("options").forEach(option -> {
            String name = text(option, "name");
            String packSize = text(option, "packSize");
            if (name != null) {
                options.add(packSize == null ? name : name + " (" + packSize + ")");
            }
        });
        if (options.size() < 2) {
            return null;
        }
        return "Which one do you want to take out of your cart: "
                + String.join(", ", options.subList(0, options.size() - 1)) + " or " + options.getLast() + "?";
    }

    /** The names of the cart's lines (getMyCart of this answer), each once (two pack sizes of a product share a name) */
    private static List<String> cartLineNames(ToolCallRecorder recorder) {
        Set<String> names = new LinkedHashSet<>();
        recorder.calls().stream()
                .filter(call -> CART_TOOL.equals(call.toolName()) && "OK".equals(call.status()))
                .forEach(call -> names.addAll(namesOf(dataOf(call.result()).path("items"))));
        return List.copyOf(names);
    }

    /**
     * "remove the tomato ketchup" with Tomato and Kissan Fresh Tomato Ketchup in the cart: "tomato" is in the longer
     * name, and the customer also said "ketchup", another word of it. So they mean the longer one, not Tomato.
     */
    private static boolean partOfALongerLineTheyNamed(String line, List<String> lines, List<String> saidWords) {
        List<String> lineWords = wordsOf(line);
        for (String other : lines) {
            List<String> otherWords = wordsOf(other);
            boolean longerWithThisName = otherWords.size() > lineWords.size() && namedIn(otherWords, lineWords, false);
            if (longerWithThisName && otherWords.stream().anyMatch(word -> !lineWords.contains(word) && saidWords.contains(word))) {
                return true;
            }
        }
        return false;
    }

    /** The "name" of every product in a list of products (a cart's or a removal's "items") */
    private static List<String> namesOf(JsonNode products) {
        List<String> names = new ArrayList<>();
        products.forEach(product -> {
            String name = text(product, "name");
            if (name != null && !wordsOf(name).isEmpty()) {
                names.add(name);
            }
        });
        return names;
    }

    /**
     * Whether the name's words come one after another in the said words ("harpic toilet cleaner" in "remove harpic
     * toilet cleaner from my cart")
     */
    private static boolean namedIn(List<String> said, List<String> name, boolean pluralsCount) {
        for (int start = 0; start + name.size() <= said.size(); start++) {
            int matching = 0;
            while (matching < name.size() && sameWord(said.get(start + matching), name.get(matching), pluralsCount)) {
                matching++;
            }
            if (matching == name.size()) {
                return true;
            }
        }
        return false;
    }

    /** "tomato" is "tomato"; with plurals also "tomatoes" (and "onions" is "onion") */
    private static boolean sameWord(String said, String nameWord, boolean pluralsCount) {
        return said.equals(nameWord) || (pluralsCount && (said.equals(nameWord + "s") || said.equals(nameWord + "es")));
    }

    /** The words in lower case; quotes, commas and other characters only separate them: "Harpic  Toilet" -> [harpic, toilet] */
    private static List<String> wordsOf(String text) {
        String normalised = text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
        return normalised.isEmpty() ? List.of() : List.of(normalised.split(" "));
    }

    private static JsonNode dataOf(String result) {
        try {
            return JSON.readTree(result).path("data");
        } catch (JacksonException ex) {
            return JSON.missingNode();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() ? value.asString() : null;
    }
}
