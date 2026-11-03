-- Weekly opening hours: one row per store and day of the week.
CREATE TABLE store_opening_hours (
    store_id     BIGINT       NOT NULL,
    day_of_week  VARCHAR(10)  NOT NULL COMMENT 'MONDAY ... SUNDAY',
    opens_at     TIME         NULL,
    closes_at    TIME         NULL,
    closed       BOOLEAN      NOT NULL DEFAULT FALSE COMMENT 'TRUE = closed all day',
    PRIMARY KEY (store_id, day_of_week),
    CONSTRAINT fk_store_opening_hours_store FOREIGN KEY (store_id) REFERENCES stores (id)
);
