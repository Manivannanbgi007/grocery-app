-- An addition to the cart that waits for the customer's amount (PendingAdd): the assistant asked "How much Tomato
-- would you like to add?", and the customer's next message ("3 kg") adds exactly this product. Both NULL when no such
-- question is open. Kept with the conversation, so it belongs to its owner only.
ALTER TABLE conversations
    ADD COLUMN pending_product_id    BIGINT       NULL COMMENT 'The product prepareMyCartAdd asked the amount of',
    ADD COLUMN pending_product_name  VARCHAR(200) NULL COMMENT 'Its name in the catalog, for the answers';
