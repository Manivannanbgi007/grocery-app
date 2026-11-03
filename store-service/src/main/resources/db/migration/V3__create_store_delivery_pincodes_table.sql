-- PIN codes each store delivers to.
CREATE TABLE store_delivery_pincodes (
    store_id  BIGINT      NOT NULL,
    pincode   VARCHAR(6)  NOT NULL,
    PRIMARY KEY (store_id, pincode),
    INDEX idx_store_delivery_pincodes_pincode (pincode),
    CONSTRAINT fk_store_delivery_pincodes_store FOREIGN KEY (store_id) REFERENCES stores (id)
);
