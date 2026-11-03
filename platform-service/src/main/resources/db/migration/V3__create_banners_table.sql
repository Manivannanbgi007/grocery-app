-- Promotional banners on the home page, managed by admins.
CREATE TABLE banners (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    title       VARCHAR(100)  NOT NULL,
    subtitle    VARCHAR(200)  NULL,
    image_url   VARCHAR(500)  NOT NULL,
    link_url    VARCHAR(500)  NULL COMMENT 'Where a click leads, e.g. /categories/fruits',
    position    INT           NOT NULL COMMENT 'Order on the page, lowest first',
    starts_at   DATETIME(6)   NULL COMMENT 'Shown from (null = now)',
    ends_at     DATETIME(6)   NULL COMMENT 'Shown until (null = no end)',
    active      BOOLEAN       NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    version     BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_banners_active_position (active, position)
);
