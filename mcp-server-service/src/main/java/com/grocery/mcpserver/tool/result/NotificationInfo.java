package com.grocery.mcpserver.tool.result;

import com.grocery.mcpserver.client.dto.NotificationData;
import com.grocery.mcpserver.tool.Formats;

/**
 * One notification in the list of getMyNotifications.
 *
 * @param unread   true when the user has not read it yet
 * @param received when it arrived, in India time: "28 Oct 2026, 5:30 pm"
 */
public record NotificationInfo(String title, String message, boolean unread, String received) {

    /** Notification texts are short; a long one is cut, so a few of them never fill the model's small context. */
    static final int MAX_MESSAGE_LENGTH = 300;

    /** Amounts in the texts ("Please pay 138.00 in cash") become ₹ amounts ("₹138"), see Formats.withRupees. */
    public static NotificationInfo from(NotificationData notification) {
        String message = Formats.withRupees(notification.message());
        if (message != null && message.length() > MAX_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";
        }
        return new NotificationInfo(Formats.withRupees(notification.title()), message, !notification.read(),
                Formats.dateTime(notification.createdAt()));
    }
}
