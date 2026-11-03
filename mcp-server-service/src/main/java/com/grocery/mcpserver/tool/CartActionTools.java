package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.client.CustomerClient;
import com.grocery.mcpserver.client.ShopInfoClient;
import com.grocery.mcpserver.client.dto.CartData;
import com.grocery.mcpserver.client.dto.ProductData;
import com.grocery.mcpserver.tool.ProductChoice.Choice;
import com.grocery.mcpserver.tool.result.CartAddPreview;
import com.grocery.mcpserver.tool.result.CartChange;
import com.grocery.mcpserver.tool.result.CartQuantityPreview;
import com.grocery.mcpserver.tool.result.CartRemovalPreview;
import com.grocery.mcpserver.tool.result.CartSummary;
import com.grocery.mcpserver.tool.result.ProductSummary;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * The cart tools: each prepares a change of the logged-in customer's own cart that the customer confirms: adding a
 * product, how much of a product it has, taking a product out, emptying the cart. They only read; the customer's own
 * click on their card makes the change (the browser calls cart-service with their own login). So no tool of this
 * server changes data, and the model can never change a cart by itself.
 *
 * Whose cart: the customer's token (X-User-Token, see LoginToken) goes with every call, and cart-service finds the cart
 * by the user id in that token. So a tool can only ever read the cart of the customer who is chatting; no tool takes a
 * user id, a price or a total.
 *
 * What exactly: the model passes the customer's words only. ProductChoice decides which product and PackCount how many
 * packs; anything unclear comes back as a question for the customer (CHOOSE) instead of a guess. cart-service keeps all
 * the rules (stock, at most 20 per product, one store per cart, prices, totals) and checks them again on the click;
 * prepareMyCartAdd checks them before, so a card that could not work is REFUSED with the reason instead.
 *
 * mcp-client-service offers these tools only to logged-in customers, and runs prepareMyCartAdd only when the customer's
 * own message asks for a change (so a review or a product description cannot make the model offer an addition).
 */
@Component
@RequiredArgsConstructor
public class CartActionTools {

    private static final String NOT_LOGGED_IN =
            "The customer is not logged in. Ask them to log in on the website to change their cart.";
    private static final String PRODUCT_MISSING = "Give the product's name as the customer said it, e.g. \"tomatoes\" or \"amul butter\".";
    private static final String PRODUCT_PARAMETER = """
            The product as the customer named it, e.g. "tomatoes" or "amul butter"; with a pack size or a store only if \
            they said one ("amul butter 500 g"). Not the amount: that goes into quantity and unit.""";
    private static final String UNIT_PARAMETER = """
            What the number counts, as the customer said it: kg, g, l, ml, dozen, pieces (single things: "6 eggs", \
            "3 tomatoes") or packs (packs, packets, bottles). Leave it out only when they counted packs.""";

    private final CustomerClient customers;
    private final ShopInfoClient shopInfo;
    private final ProductChoice products;
    private final ToolRunner runner;

    /**
     * Adding a product needs the customer's click too: this tool only reads. It decides which product (ProductChoice) and
     * how many packs (PackCount), and checks what cart-service would refuse (the cart's store, the stock, at most 20
     * packs), so the card shows only an addition that can work. The card under the answer is built from its result by
     * mcp-client-service, and the browser adds the product with the customer's own login. The data is a CartAddPreview
     * (OK), ProductChoice's products to choose from (CHOOSE), or, without an amount, the product to ask the amount of
     * (CHOOSE, CartChange.amountFor): the shop never picks an amount for the customer.
     */
    @McpTool(name = "prepareMyCartAdd",
            description = """
                    Prepare adding a product to the logged-in customer's own cart ("add 2 kg tomatoes", "I want 3 \
                    packets of milk", "put amul butter in my cart", "buy Harpic"); call it once for each product. It adds \
                    NOTHING: the website shows the product, the amount and the price under your answer with a Confirm \
                    button; only the customer's click adds it. Pass the product and the amount as the customer said them \
                    (also with a spelling mistake: the tool finds the product): the tool works out the packs. Leave the \
                    amount out when they said none: the tool then asks how much. With CHOOSE, ask the customer the question.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<Object> prepareMyCartAdd(
            @McpToolParam(description = PRODUCT_PARAMETER) String product,
            @McpToolParam(description = "The number the customer said, e.g. 2 for \"2 kg\" or \"2 packets\", 1 for \"one\" "
                    + "or \"a\". Leave it out when they said no number: the tool then asks how much.", required = false) Double quantity,
            @McpToolParam(description = UNIT_PARAMETER, required = false) String unit,
            McpTransportContext context) {
        return runner.run("prepareMyCartAdd", () -> {
            String token = customerToken(context);
            String wanted = ToolInputs.clean(product);
            if (wanted == null || !ToolInputs.isUsableText(wanted)) {
                return ToolResult.invalidInput(PRODUCT_MISSING);
            }
            CartData cart = customers.cart(token);
            Choice<ProductData> choice = products.toAdd(wanted, cart);
            if (choice.instead() != null) {
                // NOT_FOUND, CHOOSE with the matching products, or REFUSED for a product of another store
                ToolResult<CartChange> instead = choice.instead();
                return new ToolResult<>(instead.status(), instead.message(), instead.data());
            }
            ProductData chosen = choice.chosen();
            String label = label(chosen.name(), chosen.unit(), chosen.unitQuantity());
            String storeName = StoreNames.of(shopInfo).get(chosen.storeId());
            int inCart = cart.packsOf(chosen.id());
            if (quantity == null) {
                // No amount: how much is the customer's decision. Asked only when at least one pack can be added (no
                // "how much?" for a product that is out of stock). The product goes with the question, so that the
                // customer's answer ("3 kg") adds exactly this product.
                ToolResult<Object> refusal = refusalOf(chosen, label, inCart, 1);
                return refusal != null ? refusal : ToolResult.choose("How much " + label + " does the customer want? Ask "
                        + "them, and prepare nothing until they answer.", CartChange.amountFor(ProductSummary.from(chosen, storeName)));
            }
            PackCount.Result count = PackCount.of(quantity, unit, chosen.name(), chosen.unit(), chosen.unitQuantity());
            if (!count.ok()) {
                return ToolResult.choose(count.question(), null);     // how much is the customer's decision
            }
            ToolResult<Object> refusal = refusalOf(chosen, label, inCart, count.packs());
            if (refusal != null) {
                return refusal;
            }
            int newQuantity = inCart + count.packs();

            String price = Formats.rupees(chosen.sellingPrice().multiply(BigDecimal.valueOf(count.packs())));
            CartSummary.Line line = new CartSummary.Line(chosen.id(), chosen.name(),
                    Formats.packSize(chosen.unit(), chosen.unitQuantity()), count.packs(), Formats.rupees(chosen.sellingPrice()), price);
            return ToolResult.ok("Adding prepared; NOTHING is added yet. The website shows it under your answer with a "
                    + "Confirm button: tell the customer to check it and press Confirm to add " + count.packs() + " x " + label
                    + " (" + price + ") to the cart."
                    + (inCart > 0 ? " The cart has " + inCart + " already, so that makes " + newQuantity + "." : ""),
                    new CartAddPreview(storeName, List.of(line), new CartAddPreview.Confirm(chosen.id(), count.packs(), newQuantity)));
        });
    }

    /**
     * What cart-service would refuse for adding these packs, before the click: more than 20 packs of one product, or more
     * than the stock. Null when it can work.
     */
    private static ToolResult<Object> refusalOf(ProductData product, String label, int inCart, int packs) {
        int newQuantity = inCart + packs;
        if (newQuantity > PackCount.MAX_PACKS) {
            return ToolResult.refused(inCart >= PackCount.MAX_PACKS
                    ? "The cart already has " + inCart + " x " + label + ", the most a cart can hold of one product."
                    : "The cart already has " + inCart + " x " + label + ". A cart holds at most " + PackCount.MAX_PACKS
                            + " packs of one product, so at most " + (PackCount.MAX_PACKS - inCart) + " more can be added.");
        }
        if (product.availableQuantity() < newQuantity) {
            return ToolResult.refused(notEnoughStock(product, label, inCart));
        }
        return null;
    }

    /** cart-service's own stock check, before the click: "Tomato (1 kg) is out of stock.", "Only 3 packs of ... are left." */
    private static String notEnoughStock(ProductData product, String label, int inCart) {
        if (product.availableQuantity() <= 0) {
            return label + " is out of stock.";
        }
        return "Only " + product.availableQuantity() + (product.availableQuantity() == 1 ? " pack" : " packs") + " of " + label
                + " are left" + (inCart > 0 ? ", and the cart has " + inCart + " already" : "") + ".";
    }

    /**
     * Changing how much of a product the cart holds needs the customer's click: this tool only reads. The card under the
     * answer is built from its result by mcp-client-service, and the browser sets the new quantity with the customer's
     * own login. The data is a CartQuantityPreview (OK), or ProductChoice's products to choose from (CHOOSE).
     */
    @McpTool(name = "prepareMyCartQuantity",
            description = """
                    Prepare changing how much of a product is in the logged-in customer's own cart to a NEW total \
                    ("make it 3 packets", "change the tomatoes to 2 kg", "only 1 butter"). It changes NOTHING: the \
                    website shows the change under your answer with a Confirm button; only the customer's click makes \
                    it. To add more on top, use prepareMyCartAdd; to take a product out completely, prepareMyCartRemoval. \
                    With CHOOSE, ask the customer the question.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<Object> prepareMyCartQuantity(
            @McpToolParam(description = PRODUCT_PARAMETER) String product,
            @McpToolParam(description = "The new total the customer said, e.g. 3 for \"make it 3 packets\", 2 for \"2 kg\"") Double quantity,
            @McpToolParam(description = UNIT_PARAMETER, required = false) String unit,
            McpTransportContext context) {
        return runner.run("prepareMyCartQuantity", () -> {
            String token = customerToken(context);
            String wanted = ToolInputs.clean(product);
            if (wanted == null || !ToolInputs.isUsableText(wanted)) {
                return ToolResult.invalidInput(PRODUCT_MISSING);
            }
            if (quantity != null && quantity == 0) {
                return ToolResult.invalidInput("To take a product out of the cart, use prepareMyCartRemoval.");
            }
            CartData cart = customers.cart(token);
            if (!cart.hasItems()) {
                return ToolResult.notFound("The cart is empty.");
            }
            Choice<CartData.Item> choice = products.inCart(wanted, cart);
            if (choice.instead() != null) {
                // NOT_FOUND "The cart has no product called ...", or CHOOSE with the matching lines
                ToolResult<CartChange> instead = choice.instead();
                return new ToolResult<>(instead.status(), instead.message(), instead.data());
            }
            CartData.Item line = choice.chosen();
            String label = label(line.productName(), line.unit(), line.unitQuantity());
            PackCount.Result count = PackCount.of(quantity, unit, line.productName(), line.unit(), line.unitQuantity());
            if (!count.ok()) {
                return ToolResult.choose(count.question(), null);       // how much is the customer's decision
            }
            if (count.packs() == line.quantity()) {
                return ToolResult.ok("The cart already has " + line.quantity() + " x " + label + "; nothing to change.", null);
            }
            return ToolResult.ok("Change prepared; NOTHING is changed yet. The website shows it under your answer with a "
                    + "Confirm button: tell the customer to check it and press Confirm to change " + label + " from "
                    + line.quantity() + " to " + count.packs() + ".",
                    new CartQuantityPreview(storeNameOf(cart), List.of(CartSummary.line(line)),
                            new CartQuantityPreview.Confirm(line.productId(), count.packs())));
        });
    }

    /**
     * Emptying the cart needs the customer's click: this tool only reads. Its data is the whole cart as getMyCart shows
     * it (CartSummary); mcp-client-service makes the card from it, and the browser empties the cart (DELETE
     * /api/v1/cart) with the customer's own login.
     */
    @McpTool(name = "prepareMyCartClear",
            description = """
                    Prepare emptying the logged-in customer's own cart ("empty my cart", "remove everything from my \
                    cart"). It removes NOTHING: it returns the whole cart, and the website shows it under your answer \
                    with a Confirm button; only the customer's click empties the cart.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<CartSummary> prepareMyCartClear(McpTransportContext context) {
        return runner.run("prepareMyCartClear", () -> {
            CartData cart = customers.cart(customerToken(context));
            if (!cart.hasItems()) {
                return ToolResult.notFound("The cart is empty already, so there is nothing to remove.");
            }
            int count = cart.items().size();
            return ToolResult.ok("Emptying prepared; NOTHING is removed yet. The website shows the cart under your answer "
                    + "with a Confirm button: tell the customer to check it and press Confirm to empty the cart ("
                    + count + (count == 1 ? " product" : " products") + ").", CartSummary.from(cart, storeNameOf(cart)));
        });
    }

    /**
     * Taking a product out of the cart needs the customer's click: this tool only reads. The card under the answer is
     * built from its result by mcp-client-service, and the browser removes the product with the customer's own login.
     * The data is a CartRemovalPreview (OK), or ProductChoice's products to choose from (CHOOSE), so it is typed Object.
     */
    @McpTool(name = "prepareMyCartRemoval",
            description = """
                    Prepare taking a product out of the logged-in customer's own cart ("remove the onions", "delete the \
                    butter from my cart"). It removes NOTHING: it returns the cart's line, and the website shows it under \
                    your answer with a Confirm button; only the customer's click removes it. With CHOOSE, ask the \
                    customer which product they mean.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<Object> prepareMyCartRemoval(
            @McpToolParam(description = "The product as the customer named it, e.g. \"onions\" or \"amul butter\"; with a "
                    + "pack size only if they said one (\"amul butter 500 g\")") String product,
            McpTransportContext context) {
        return runner.run("prepareMyCartRemoval", () -> {
            String token = customerToken(context);
            String wanted = ToolInputs.clean(product);
            if (wanted == null || !ToolInputs.isUsableText(wanted)) {
                return ToolResult.invalidInput(PRODUCT_MISSING);
            }
            CartData cart = customers.cart(token);
            if (!cart.hasItems()) {
                return ToolResult.notFound("The cart is empty.");
            }
            Choice<CartData.Item> choice = products.inCart(wanted, cart);
            if (choice.instead() != null) {
                // NOT_FOUND "The cart has no product called ...", or CHOOSE with the matching lines
                ToolResult<CartChange> instead = choice.instead();
                return new ToolResult<>(instead.status(), instead.message(), instead.data());
            }
            CartData.Item line = choice.chosen();
            String label = label(line.productName(), line.unit(), line.unitQuantity());
            return ToolResult.ok("Removal prepared; NOTHING is removed yet. The website shows it under your answer with a "
                    + "Confirm button: tell the customer to check it and press Confirm to take " + label + " out of the cart.",
                    new CartRemovalPreview(storeNameOf(cart), List.of(CartSummary.line(line)),
                            new CartRemovalPreview.Confirm(line.productId())));
        });
    }

    private static String customerToken(McpTransportContext context) {
        return LoginToken.of(context, NOT_LOGGED_IN);
    }

    /** "Tomato (1 kg)" */
    private static String label(String name, String unit, BigDecimal unitQuantity) {
        String packSize = Formats.packSize(unit, unitQuantity);
        return packSize == null ? name : name + " (" + packSize + ")";
    }

    private String storeNameOf(CartData cart) {
        return cart.storeId() == null ? null : StoreNames.of(shopInfo).get(cart.storeId());
    }
}
