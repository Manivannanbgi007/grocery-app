package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.client.PartnerClient;
import com.grocery.mcpserver.client.dto.DeliveryData;
import com.grocery.mcpserver.client.dto.PageData;
import com.grocery.mcpserver.client.dto.PartnerData;
import com.grocery.mcpserver.tool.result.PartnerDelivery;
import com.grocery.mcpserver.tool.result.PartnerEarnings;
import com.grocery.mcpserver.tool.result.PartnerStatus;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * The delivery partner tools: the logged-in partner's own status, current deliveries and earnings (read-only).
 *
 * The partner's token comes with the MCP request (see LoginToken) and goes to the gateway with every call, so a tool
 * can only ever see the data of the partner who is chatting.
 *
 * The names start with "getPartner": mcp-client-service offers these tools only to logged-in delivery partners.
 */
@Component
@RequiredArgsConstructor
public class PartnerTools {

    static final int CURRENT_DELIVERIES = 5;
    static final String NOT_LOGGED_IN =
            "The delivery partner is not logged in. Ask them to log in to the delivery panel again.";
    static final String NO_PROFILE =
            "The partner's profile is still being set up. Ask them to open the delivery panel again in a moment.";

    private final PartnerClient partners;
    private final ToolRunner runner;
    private final Clock clock;

    @McpTool(name = "getPartnerStatus",
            description = """
                    The logged-in delivery partner's own status: online or offline, the KYC (approved, waiting for \
                    review, rejected and why), zone, vehicle and rating. Use it for "am I online?", "why do I get no \
                    deliveries?", "is my KYC approved?".""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<PartnerStatus> getPartnerStatus(McpTransportContext context) {
        return runner.run("getPartnerStatus", () -> {
            String token = LoginToken.of(context, NOT_LOGGED_IN);
            Optional<PartnerData> profile = partners.profile(token);
            if (profile.isEmpty()) {
                return ToolResult.notFound(NO_PROFILE);
            }
            PartnerStatus status = PartnerStatus.from(profile.get(), partners.availability(token));
            String message = status.online()
                    ? "The partner is online and gets new deliveries."
                    : "The partner is offline: no new deliveries come to them until they go online in the delivery panel.";
            if (!"APPROVED".equals(profile.get().kycStatus())) {
                message += " Deliveries start only once the KYC is approved.";
            }
            return ToolResult.ok(message, status);
        });
    }

    @McpTool(name = "getPartnerDeliveries",
            description = """
                    The logged-in delivery partner's own current deliveries (not finished yet, at most 5): order \
                    number, step, store and its address, customer name and address, cash to collect and the payout. \
                    Use it for "what are my deliveries?", "where do I pick up?", "how much cash do I collect?".""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<List<PartnerDelivery>> getPartnerDeliveries(McpTransportContext context) {
        return runner.run("getPartnerDeliveries", () -> {
            PageData<DeliveryData> page = partners.activeDeliveries(LoginToken.of(context, NOT_LOGGED_IN), CURRENT_DELIVERIES);
            if (page.content().isEmpty()) {
                return ToolResult.ok("The partner has no current deliveries.", null);
            }
            List<PartnerDelivery> deliveries = page.content().stream().map(PartnerDelivery::from).toList();
            String message = page.totalElements() > deliveries.size()
                    ? "The partner has " + page.totalElements() + " current deliveries; these are the " + deliveries.size() + " newest."
                    : "The partner has " + deliveries.size() + (deliveries.size() == 1 ? " current delivery." : " current deliveries.");
            return ToolResult.ok(message, deliveries);
        });
    }

    @McpTool(name = "getPartnerEarnings",
            description = """
                    The logged-in delivery partner's own deliveries and earnings of the last few days, in total and per \
                    day. Use it for "how much did I earn this week?", "how many deliveries did I do today?".""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<PartnerEarnings> getPartnerEarnings(
            @McpToolParam(description = "How many days up to today: 1 = today, 7 = the last 7 days (the default), "
                    + "30 = the last 30 days. At most 90.", required = false) Integer days,
            McpTransportContext context) {
        return runner.run("getPartnerEarnings", () -> {
            String token = LoginToken.of(context, NOT_LOGGED_IN);
            if (!DateRange.isValid(days)) {
                return ToolResult.invalidInput(DateRange.DAYS_RULE);
            }
            DateRange range = DateRange.lastDays(days, clock);
            PartnerEarnings earnings = PartnerEarnings.from(range.text(), partners.earnings(token, range.from(), range.to()));
            return ToolResult.ok("The partner's earnings of " + range.text() + ".", earnings);
        });
    }
}
