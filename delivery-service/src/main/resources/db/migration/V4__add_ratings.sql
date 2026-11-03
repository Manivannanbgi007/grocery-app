-- Phase 11: customers rate the delivery (1-5 stars); the partner's average is kept on the partner.
ALTER TABLE deliveries
    ADD COLUMN customer_rating  INT          NULL COMMENT '1 to 5 stars' AFTER partner_payout,
    ADD COLUMN customer_comment VARCHAR(500) NULL AFTER customer_rating,
    ADD COLUMN rated_at         DATETIME(6)  NULL AFTER customer_comment;

ALTER TABLE delivery_partners
    ADD COLUMN rating_count INT NOT NULL DEFAULT 0 COMMENT 'Number of ratings received' AFTER kyc_note,
    ADD COLUMN rating_total INT NOT NULL DEFAULT 0 COMMENT 'Sum of all stars (average = total / count)' AFTER rating_count;
