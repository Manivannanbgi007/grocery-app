package com.grocery.mcpclient.assistant;

import com.grocery.mcpclient.context.Audience;

import java.util.List;

/**
 * The result of {@link AnswerValidator#check}.
 *
 * @param problems what is wrong with the answer; empty when the answer may be shown
 */
public record AnswerCheck(List<String> problems) {

    public boolean passed() {
        return problems.isEmpty();
    }

    /**
     * The note for the second attempt: tells the model what was wrong with its first answer. In a panel the model is
     * pointed to its own tools (a small model answered a follow-up such as "which products are low?" from its earlier
     * answer instead of calling one); a customer's follow-up about the cart ("how much do I pay now?") to getMyCart, and
     * a change of the cart to the tools that make it. (Naming only getMyCart sent "remove Harpic from my cart" to a
     * second attempt that read the cart and again said it was removed.)
     */
    public String correction(Audience audience) {
        String callTheTools = audience.isPanel()
                ? ". Call the right tool for this question now (your tools are named in your rules; facts from earlier "
                + "answers are out of date) "
                : audience == Audience.CUSTOMER
                ? ". Call the tools now (for a product mentioned earlier: getProductDetails with its name; for the cart "
                + "and the amount to pay: getMyCart; to add to the cart, change an amount, take a product out or empty the "
                + "cart: prepareMyCartAdd, prepareMyCartQuantity, prepareMyCartRemoval or prepareMyCartClear) "
                : ". Call the tools now (for a product mentioned earlier: getProductDetails with its name) ";
        return "Your first answer to this message was rejected because: " + String.join("; ", problems)
                + callTheTools
                + "and answer only with facts from their results. Never calculate amounts yourself: "
                + "quote the prices exactly as the tools return them. "
                + "If the tools do not have the answer, say that you could not find it.";
    }
}
