-- =====================================================================
-- LOCAL ONLY stock of the Fresh Grocery demo products 101-143 and 201-250 (loaded by the "local" profile).
--
-- GENERATED from fresh-grocery/src/mocks/data/products.ts by "npm run seed:sql" - do not edit by hand.
-- Repeatable migration (R__): runs again whenever this file changes. INSERT IGNORE skips existing rows.
--
-- SQL inserts publish no events. After the first start, call
-- POST /internal/inventory/stock-snapshot so product-read-service learns the stock.
-- =====================================================================

INSERT IGNORE INTO stock_items (product_id, store_id, quantity_on_hand, reserved_quantity, low_stock_threshold,
                                created_at, updated_at, version) VALUES
 (101, 1, 40, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Cauliflower
 (102, 1, 30, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Green Capsicum
 (103, 2, 15, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Organic Broccoli
 (104, 2, 25, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Organic Cucumber
 (105, 1, 60, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Lemon
 (106, 1, 18, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Green Kiwi
 (107, 1, 45, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Nagpur Orange
 (108, 2, 4, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Strawberry   LOW
 (109, 2, 22, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Green Grapes
 (110, 2, 10, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Watermelon
 (111, 1, 40, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Amul Masti Dahi
 (112, 1, 20, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Amul Cheese Slices
 (113, 2, 12, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Mother Dairy Paneer
 (114, 2, 25, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Akshayakalpa Organic Milk
 (115, 1, 36, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Coca-Cola Soft Drink
 (116, 1, 50, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Britannia Good Day Cashew Cookies
 (117, 2, 30, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Paper Boat Aamras
 (118, 2, 25, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Haldiram's Moong Dal
 (119, 1, 20, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Harpic Toilet Cleaner
 (120, 2, 40, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Scotch-Brite Scrub Pad
 (121, 1, 25, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Britannia Brown Bread
 (122, 1, 18, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Kellogg's Corn Flakes
 (123, 2, 22, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Saffola Masala Oats
 (124, 2, 3, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Kissan Mixed Fruit Jam   LOW
 (125, 1, 14, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Dabur Honey
 (126, 1, 30, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Tata Tea Gold
 (127, 1, 15, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Nescafe Classic Coffee
 (128, 2, 20, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Organic India Tulsi Green Tea
 (129, 2, 0, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Bru Instant Coffee   OUT OF STOCK
 (130, 1, 10, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Chicken Breast Boneless
 (131, 1, 6, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Rohu Fish Curry Cut
 (132, 2, 5, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Prawns Medium Cleaned   LOW
 (133, 1, 20, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- India Gate Basmati Rice
 (134, 1, 35, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Aashirvaad Whole Wheat Atta
 (135, 2, 18, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Organic Toor Dal
 (136, 2, 26, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Tata Sampann Moong Dal
 (137, 1, 16, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- McCain French Fries
 (138, 1, 9, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Amul Vanilla Ice Cream
 (139, 2, 24, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Safal Frozen Green Peas
 (140, 1, 40, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Dove Cream Beauty Bar
 (141, 1, 50, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Colgate Strong Teeth Toothpaste
 (142, 2, 28, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Himalaya Neem Face Wash
 (143, 2, 2, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Biotique Green Apple Shampoo   LOW
 (201, 1, 40, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- MDH Garam Masala
 (202, 1, 55, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Everest Turmeric Powder
 (203, 2, 20, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Catch Black Pepper Powder
 (204, 2, 18, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Organic Cumin Seeds
 (205, 1, 3, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Everest Kitchen King Masala   LOW
 (206, 1, 30, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Fortune Sunflower Oil
 (207, 1, 14, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Amul Pure Ghee
 (208, 2, 22, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Saffola Gold Oil
 (209, 2, 10, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Cold Pressed Groundnut Oil
 (210, 1, 25, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Fortune Kachi Ghani Mustard Oil
 (211, 1, 26, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Happilo California Almonds
 (212, 1, 4, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Happilo Whole Cashews   LOW
 (213, 2, 30, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Seedless Black Raisins
 (214, 2, 12, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Nutraj Walnut Kernels
 (215, 1, 28, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Lion Seedless Dates
 (216, 1, 35, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Kissan Fresh Tomato Ketchup
 (217, 1, 20, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Veeba Eggless Mayonnaise
 (218, 2, 15, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Mother's Recipe Mango Pickle
 (219, 2, 0, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Ching's Secret Schezwan Chutney   OUT OF STOCK
 (220, 1, 18, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Maggi Hot & Sweet Tomato Chilli Sauce
 (221, 1, 60, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Maggi 2-Minute Masala Noodles
 (222, 1, 40, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Sunfeast Yippee Magic Masala Noodles
 (223, 2, 18, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Del Monte Penne Pasta
 (224, 2, 22, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- MTR Rava Idli Mix
 (225, 1, 30, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Ching's Hakka Noodles
 (226, 1, 32, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Cadbury Dairy Milk Silk
 (227, 1, 45, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Nestle KitKat
 (228, 2, 20, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Haldiram's Soan Papdi
 (229, 2, 14, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Bikanervala Rasgulla
 (230, 1, 5, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Ferrero Rocher   LOW
 (231, 1, 12, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Pampers Baby Dry Pants Medium
 (232, 1, 30, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Himalaya Gentle Baby Wipes
 (233, 2, 16, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Nestle Cerelac Wheat Apple
 (234, 2, 22, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Johnson's Baby Shampoo
 (235, 1, 18, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Himalaya Baby Lotion
 (236, 1, 10, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Pedigree Adult Dog Food Chicken & Vegetables
 (237, 1, 8, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Whiskas Adult Cat Food Ocean Fish
 (238, 2, 6, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Drools Puppy Chicken & Egg
 (239, 2, 25, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Pedigree Dentastix Medium
 (240, 1, 12, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Purepet Lavender Cat Litter
 (241, 1, 20, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Dabur Chyawanprash
 (242, 1, 35, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Bournvita Health Drink
 (243, 2, 18, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Horlicks Classic Malt
 (244, 2, 40, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Electral ORS Powder
 (245, 1, 4, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Patanjali Aloe Vera Juice   LOW
 (246, 1, 50, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Cycle Pure Agarbatti
 (247, 1, 30, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Mangaldeep Camphor
 (248, 2, 60, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Cotton Wicks
 (249, 2, 25, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Clay Diyas
 (250, 1, 40, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0);   -- Zed Black Dhoop Sticks
