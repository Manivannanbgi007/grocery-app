-- =====================================================================
-- LOCAL ONLY sample banners (loaded by the "local" profile).
-- Repeatable migration (R__): Flyway runs it again whenever this file changes. INSERT IGNORE skips existing rows.
-- =====================================================================

INSERT IGNORE INTO banners (id, title, subtitle, image_url, link_url, position, starts_at, ends_at, active, created_at, updated_at) VALUES
 (1, 'Fresh vegetables, delivered today', 'Order before 6 pm', 'http://localhost:8080/media/banners/vegetables.jpg', '/categories/1', 1, NULL, NULL, TRUE, NOW(6), NOW(6)),
 (2, 'WELCOME50: 50 off your first order', 'On orders of 299 or more', 'http://localhost:8080/media/banners/welcome50.jpg', '/offers', 2, NULL, NULL, TRUE, NOW(6), NOW(6)),
 (3, 'Diwali sale', 'Ended last year', 'http://localhost:8080/media/banners/diwali.jpg', '/offers', 3, '2025-10-15 00:00:00', '2025-11-05 00:00:00', TRUE, NOW(6), NOW(6));
