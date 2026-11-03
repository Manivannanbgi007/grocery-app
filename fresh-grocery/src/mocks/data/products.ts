import type { Product, ProductStatus, ProductUnit } from '../../api/types'
import { demoCategories } from './categories'
import { demoReviews } from './reviews'

/*
 * The demo catalog: one row per product, with the same columns as the backend's seed SQL plus the stock.
 *
 * Ids 1-17 are copied from the backend's local seed (product-service products + inventory-service stock).
 * Ids 101-143 and 201-250 are demo products; "npm run seed:sql" generates the extra backend seed rows from this list,
 * so the mocks and the real backend show the same catalog. (201-250 and not 144+: products created through the API
 * take ids from 144 upwards in a running database, and the seed skips ids that already exist.)
 */
export interface DemoProductRow {
  id: number
  storeId: number
  categoryId: number
  name: string
  description: string
  brand: string | null
  sku: string
  unit: ProductUnit
  unitQuantity: number
  /** price before the discount (MRP) */
  price: number
  discountPercentage: number
  taxPercentage: number
  /** units in stock (inventory-service) */
  stock: number
  /** how many days ago the product was added: used for "newest first" */
  addedDaysAgo: number
  /** ACTIVE when left out */
  status?: ProductStatus
}

/** Products with this many units or fewer count as "low stock" (the stores' threshold in the seed). */
export const LOW_STOCK_THRESHOLD = 5

export const demoProductRows: DemoProductRow[] = [
  // ----- Fresh Mart (store 1): backend seed -----
  { id: 1, storeId: 1, categoryId: 1, name: 'Tomato', description: 'Farm fresh red tomatoes', brand: null, sku: 'FM-VEG-001', unit: 'KG', unitQuantity: 1, price: 40, discountPercentage: 10, taxPercentage: 0, stock: 120, addedDaysAgo: 40 },
  { id: 2, storeId: 1, categoryId: 1, name: 'Onion', description: 'Medium size red onions', brand: null, sku: 'FM-VEG-002', unit: 'KG', unitQuantity: 1, price: 35, discountPercentage: 0, taxPercentage: 0, stock: 200, addedDaysAgo: 40 },
  { id: 3, storeId: 1, categoryId: 1, name: 'Potato', description: 'Fresh potatoes', brand: null, sku: 'FM-VEG-003', unit: 'KG', unitQuantity: 1, price: 30, discountPercentage: 5, taxPercentage: 0, stock: 150, addedDaysAgo: 40 },
  { id: 4, storeId: 1, categoryId: 2, name: 'Banana Robusta', description: 'Ripe robusta bananas', brand: null, sku: 'FM-FRT-001', unit: 'DOZEN', unitQuantity: 1, price: 60, discountPercentage: 0, taxPercentage: 0, stock: 40, addedDaysAgo: 40 },
  { id: 5, storeId: 1, categoryId: 2, name: 'Apple Shimla', description: 'Crisp and juicy Shimla apples', brand: null, sku: 'FM-FRT-002', unit: 'KG', unitQuantity: 1, price: 180, discountPercentage: 10, taxPercentage: 0, stock: 25, addedDaysAgo: 40 },
  { id: 6, storeId: 1, categoryId: 3, name: 'Amul Taaza Toned Milk', description: 'Pasteurised toned milk', brand: 'Amul', sku: 'FM-DRY-001', unit: 'ML', unitQuantity: 500, price: 27, discountPercentage: 0, taxPercentage: 0, stock: 60, addedDaysAgo: 40 },
  { id: 7, storeId: 1, categoryId: 3, name: 'Amul Butter', description: 'Pasteurised salted butter', brand: 'Amul', sku: 'FM-DRY-002', unit: 'G', unitQuantity: 100, price: 56, discountPercentage: 0, taxPercentage: 12, stock: 3, addedDaysAgo: 40 },
  { id: 8, storeId: 1, categoryId: 3, name: 'Farm Eggs', description: 'Fresh white eggs', brand: null, sku: 'FM-DRY-003', unit: 'PIECE', unitQuantity: 6, price: 48, discountPercentage: 0, taxPercentage: 0, stock: 30, addedDaysAgo: 40 },
  { id: 9, storeId: 1, categoryId: 4, name: "Lay's Classic Salted", description: 'Potato chips, classic salted', brand: "Lay's", sku: 'FM-SNK-001', unit: 'G', unitQuantity: 52, price: 20, discountPercentage: 0, taxPercentage: 12, stock: 80, addedDaysAgo: 40 },
  { id: 10, storeId: 1, categoryId: 5, name: 'Surf Excel Matic Liquid', description: 'Front load washing liquid', brand: 'Surf Excel', sku: 'FM-HHD-001', unit: 'L', unitQuantity: 1, price: 230, discountPercentage: 15, taxPercentage: 18, stock: 12, addedDaysAgo: 40 },
  { id: 17, storeId: 1, categoryId: 2, name: 'Mango Alphonso', description: 'Ratnagiri Alphonso (out of season)', brand: null, sku: 'FM-FRT-003', unit: 'DOZEN', unitQuantity: 1, price: 350, discountPercentage: 0, taxPercentage: 0, stock: 0, addedDaysAgo: 40, status: 'INACTIVE' },

  // ----- Green Basket (store 2): backend seed -----
  { id: 11, storeId: 2, categoryId: 1, name: 'Organic Spinach', description: 'Pesticide-free spinach leaves', brand: 'Organic Tattva', sku: 'GB-VEG-001', unit: 'G', unitQuantity: 250, price: 30, discountPercentage: 0, taxPercentage: 0, stock: 20, addedDaysAgo: 40 },
  { id: 12, storeId: 2, categoryId: 1, name: 'Organic Carrot', description: 'Sweet organic carrots', brand: 'Organic Tattva', sku: 'GB-VEG-002', unit: 'G', unitQuantity: 500, price: 45, discountPercentage: 5, taxPercentage: 0, stock: 35, addedDaysAgo: 40 },
  { id: 13, storeId: 2, categoryId: 2, name: 'Pomegranate', description: 'Bhagwa pomegranates', brand: null, sku: 'GB-FRT-001', unit: 'KG', unitQuantity: 1, price: 200, discountPercentage: 20, taxPercentage: 0, stock: 0, addedDaysAgo: 40 },
  { id: 14, storeId: 2, categoryId: 3, name: 'Nandini Curd', description: 'Fresh set curd', brand: 'Nandini', sku: 'GB-DRY-001', unit: 'G', unitQuantity: 500, price: 30, discountPercentage: 0, taxPercentage: 5, stock: 4, addedDaysAgo: 40 },
  { id: 15, storeId: 2, categoryId: 4, name: "Haldiram's Aloo Bhujia", description: 'Crispy potato and gram flour noodles', brand: "Haldiram's", sku: 'GB-SNK-001', unit: 'G', unitQuantity: 200, price: 55, discountPercentage: 0, taxPercentage: 12, stock: 50, addedDaysAgo: 40 },
  { id: 16, storeId: 2, categoryId: 5, name: 'Vim Dishwash Liquid', description: 'Lemon dishwash gel', brand: 'Vim', sku: 'GB-HHD-001', unit: 'ML', unitQuantity: 500, price: 110, discountPercentage: 10, taxPercentage: 18, stock: 18, addedDaysAgo: 40 },

  // ----- New demo products (F5 adds them to the backend seed) -----
  // Vegetables
  { id: 101, storeId: 1, categoryId: 1, name: 'Cauliflower', description: 'Fresh white cauliflower', brand: null, sku: 'FM-VEG-101', unit: 'PIECE', unitQuantity: 1, price: 35, discountPercentage: 0, taxPercentage: 0, stock: 40, addedDaysAgo: 12 },
  { id: 102, storeId: 1, categoryId: 1, name: 'Green Capsicum', description: 'Crunchy green bell peppers', brand: null, sku: 'FM-VEG-102', unit: 'G', unitQuantity: 500, price: 40, discountPercentage: 10, taxPercentage: 0, stock: 30, addedDaysAgo: 20 },
  { id: 103, storeId: 2, categoryId: 1, name: 'Organic Broccoli', description: 'Tender organic broccoli', brand: 'Organic Tattva', sku: 'GB-VEG-103', unit: 'PIECE', unitQuantity: 1, price: 80, discountPercentage: 5, taxPercentage: 0, stock: 15, addedDaysAgo: 3 },
  { id: 104, storeId: 2, categoryId: 1, name: 'Organic Cucumber', description: 'Crisp organic cucumbers', brand: 'Organic Tattva', sku: 'GB-VEG-104', unit: 'G', unitQuantity: 500, price: 38, discountPercentage: 0, taxPercentage: 0, stock: 25, addedDaysAgo: 8 },
  { id: 105, storeId: 1, categoryId: 1, name: 'Lemon', description: 'Juicy lemons', brand: null, sku: 'FM-VEG-105', unit: 'PIECE', unitQuantity: 4, price: 20, discountPercentage: 0, taxPercentage: 0, stock: 60, addedDaysAgo: 25 },
  // Fruits
  { id: 106, storeId: 1, categoryId: 2, name: 'Green Kiwi', description: 'Imported green kiwis', brand: null, sku: 'FM-FRT-106', unit: 'PIECE', unitQuantity: 3, price: 90, discountPercentage: 10, taxPercentage: 0, stock: 18, addedDaysAgo: 5 },
  { id: 107, storeId: 1, categoryId: 2, name: 'Nagpur Orange', description: 'Sweet and juicy Nagpur oranges', brand: null, sku: 'FM-FRT-107', unit: 'KG', unitQuantity: 1, price: 120, discountPercentage: 0, taxPercentage: 0, stock: 45, addedDaysAgo: 2 },
  { id: 108, storeId: 2, categoryId: 2, name: 'Strawberry', description: 'Mahabaleshwar strawberries', brand: null, sku: 'GB-FRT-108', unit: 'G', unitQuantity: 200, price: 99, discountPercentage: 15, taxPercentage: 0, stock: 4, addedDaysAgo: 6 },
  { id: 109, storeId: 2, categoryId: 2, name: 'Green Grapes', description: 'Seedless green grapes', brand: null, sku: 'GB-FRT-109', unit: 'G', unitQuantity: 500, price: 70, discountPercentage: 0, taxPercentage: 0, stock: 22, addedDaysAgo: 9 },
  { id: 110, storeId: 2, categoryId: 2, name: 'Watermelon', description: 'Sweet Kiran watermelon', brand: null, sku: 'GB-FRT-110', unit: 'PIECE', unitQuantity: 1, price: 60, discountPercentage: 0, taxPercentage: 0, stock: 10, addedDaysAgo: 1 },
  // Dairy & Eggs
  { id: 111, storeId: 1, categoryId: 3, name: 'Amul Masti Dahi', description: 'Thick and creamy curd', brand: 'Amul', sku: 'FM-DRY-111', unit: 'G', unitQuantity: 400, price: 35, discountPercentage: 0, taxPercentage: 5, stock: 40, addedDaysAgo: 30 },
  { id: 112, storeId: 1, categoryId: 3, name: 'Amul Cheese Slices', description: 'Processed cheese slices', brand: 'Amul', sku: 'FM-DRY-112', unit: 'G', unitQuantity: 200, price: 140, discountPercentage: 5, taxPercentage: 12, stock: 20, addedDaysAgo: 18 },
  { id: 113, storeId: 2, categoryId: 3, name: 'Mother Dairy Paneer', description: 'Soft fresh paneer', brand: 'Mother Dairy', sku: 'GB-DRY-113', unit: 'G', unitQuantity: 200, price: 90, discountPercentage: 0, taxPercentage: 5, stock: 12, addedDaysAgo: 4 },
  { id: 114, storeId: 2, categoryId: 3, name: 'Akshayakalpa Organic Milk', description: 'Organic full cream milk', brand: 'Akshayakalpa', sku: 'GB-DRY-114', unit: 'L', unitQuantity: 1, price: 85, discountPercentage: 0, taxPercentage: 0, stock: 25, addedDaysAgo: 14 },
  // Snacks & Beverages
  { id: 115, storeId: 1, categoryId: 4, name: 'Coca-Cola Soft Drink', description: 'Chilled cola soft drink bottle', brand: 'Coca-Cola', sku: 'FM-SNK-115', unit: 'L', unitQuantity: 2, price: 95, discountPercentage: 0, taxPercentage: 28, stock: 36, addedDaysAgo: 22 },
  { id: 116, storeId: 1, categoryId: 4, name: 'Britannia Good Day Cashew Cookies', description: 'Crunchy cashew cookies', brand: 'Britannia', sku: 'FM-SNK-116', unit: 'G', unitQuantity: 200, price: 40, discountPercentage: 0, taxPercentage: 18, stock: 50, addedDaysAgo: 35 },
  { id: 117, storeId: 2, categoryId: 4, name: 'Paper Boat Aamras', description: 'Mango drink made with Alphonso pulp', brand: 'Paper Boat', sku: 'GB-SNK-117', unit: 'ML', unitQuantity: 250, price: 30, discountPercentage: 0, taxPercentage: 12, stock: 30, addedDaysAgo: 11 },
  { id: 118, storeId: 2, categoryId: 4, name: "Haldiram's Moong Dal", description: 'Crispy fried moong dal namkeen', brand: "Haldiram's", sku: 'GB-SNK-118', unit: 'G', unitQuantity: 200, price: 50, discountPercentage: 10, taxPercentage: 12, stock: 25, addedDaysAgo: 16 },
  // Household
  { id: 119, storeId: 1, categoryId: 5, name: 'Harpic Toilet Cleaner', description: 'Disinfectant toilet cleaner liquid', brand: 'Harpic', sku: 'FM-HHD-119', unit: 'ML', unitQuantity: 500, price: 105, discountPercentage: 5, taxPercentage: 18, stock: 20, addedDaysAgo: 27 },
  { id: 120, storeId: 2, categoryId: 5, name: 'Scotch-Brite Scrub Pad', description: 'Scrub pads for utensils, pack of 3', brand: 'Scotch-Brite', sku: 'GB-HHD-120', unit: 'PIECE', unitQuantity: 3, price: 60, discountPercentage: 0, taxPercentage: 18, stock: 40, addedDaysAgo: 19 },
  // Breakfast & Bakery
  { id: 121, storeId: 1, categoryId: 21, name: 'Britannia Brown Bread', description: 'Soft whole wheat brown bread', brand: 'Britannia', sku: 'FM-BKY-121', unit: 'G', unitQuantity: 400, price: 50, discountPercentage: 0, taxPercentage: 0, stock: 25, addedDaysAgo: 1 },
  { id: 122, storeId: 1, categoryId: 21, name: "Kellogg's Corn Flakes", description: 'Crispy breakfast cereal', brand: "Kellogg's", sku: 'FM-BKY-122', unit: 'G', unitQuantity: 475, price: 199, discountPercentage: 10, taxPercentage: 18, stock: 18, addedDaysAgo: 24 },
  { id: 123, storeId: 2, categoryId: 21, name: 'Saffola Masala Oats', description: 'Classic masala instant oats', brand: 'Saffola', sku: 'GB-BKY-123', unit: 'G', unitQuantity: 500, price: 185, discountPercentage: 15, taxPercentage: 18, stock: 22, addedDaysAgo: 13 },
  { id: 124, storeId: 2, categoryId: 21, name: 'Kissan Mixed Fruit Jam', description: 'Jam made with real fruit', brand: 'Kissan', sku: 'GB-BKY-124', unit: 'G', unitQuantity: 500, price: 160, discountPercentage: 0, taxPercentage: 12, stock: 3, addedDaysAgo: 7 },
  { id: 125, storeId: 1, categoryId: 21, name: 'Dabur Honey', description: 'Pure natural honey', brand: 'Dabur', sku: 'FM-BKY-125', unit: 'G', unitQuantity: 500, price: 225, discountPercentage: 20, taxPercentage: 5, stock: 14, addedDaysAgo: 17 },
  // Tea & Coffee
  { id: 126, storeId: 1, categoryId: 22, name: 'Tata Tea Gold', description: 'Assam tea leaves with long leaf', brand: 'Tata Tea', sku: 'FM-TEA-126', unit: 'G', unitQuantity: 500, price: 290, discountPercentage: 10, taxPercentage: 5, stock: 30, addedDaysAgo: 33 },
  { id: 127, storeId: 1, categoryId: 22, name: 'Nescafe Classic Coffee', description: 'Instant coffee in a glass jar', brand: 'Nescafe', sku: 'FM-TEA-127', unit: 'G', unitQuantity: 100, price: 320, discountPercentage: 0, taxPercentage: 18, stock: 15, addedDaysAgo: 21 },
  { id: 128, storeId: 2, categoryId: 22, name: 'Organic India Tulsi Green Tea', description: 'Tulsi green tea, 25 tea bags', brand: 'Organic India', sku: 'GB-TEA-128', unit: 'PACK', unitQuantity: 1, price: 175, discountPercentage: 0, taxPercentage: 5, stock: 20, addedDaysAgo: 10 },
  { id: 129, storeId: 2, categoryId: 22, name: 'Bru Instant Coffee', description: 'Coffee and chicory blend', brand: 'Bru', sku: 'GB-TEA-129', unit: 'G', unitQuantity: 100, price: 210, discountPercentage: 5, taxPercentage: 18, stock: 0, addedDaysAgo: 26 },
  // Meat & Seafood
  { id: 130, storeId: 1, categoryId: 23, name: 'Chicken Breast Boneless', description: 'Fresh boneless chicken breast', brand: null, sku: 'FM-MTS-130', unit: 'G', unitQuantity: 500, price: 260, discountPercentage: 0, taxPercentage: 0, stock: 10, addedDaysAgo: 2 },
  { id: 131, storeId: 1, categoryId: 23, name: 'Rohu Fish Curry Cut', description: 'Cleaned rohu fish, curry cut', brand: null, sku: 'FM-MTS-131', unit: 'G', unitQuantity: 500, price: 220, discountPercentage: 10, taxPercentage: 0, stock: 6, addedDaysAgo: 4 },
  { id: 132, storeId: 2, categoryId: 23, name: 'Prawns Medium Cleaned', description: 'Deveined medium prawns', brand: null, sku: 'GB-MTS-132', unit: 'G', unitQuantity: 250, price: 299, discountPercentage: 0, taxPercentage: 0, stock: 5, addedDaysAgo: 3 },
  // Rice, Atta & Dals
  { id: 133, storeId: 1, categoryId: 24, name: 'India Gate Basmati Rice', description: 'Aged long grain basmati rice', brand: 'India Gate', sku: 'FM-GRN-133', unit: 'KG', unitQuantity: 5, price: 640, discountPercentage: 12, taxPercentage: 5, stock: 20, addedDaysAgo: 38 },
  { id: 134, storeId: 1, categoryId: 24, name: 'Aashirvaad Whole Wheat Atta', description: 'Whole wheat flour for soft rotis', brand: 'Aashirvaad', sku: 'FM-GRN-134', unit: 'KG', unitQuantity: 5, price: 285, discountPercentage: 5, taxPercentage: 5, stock: 35, addedDaysAgo: 36 },
  { id: 135, storeId: 2, categoryId: 24, name: 'Organic Toor Dal', description: 'Unpolished organic toor dal', brand: 'Organic Tattva', sku: 'GB-GRN-135', unit: 'KG', unitQuantity: 1, price: 210, discountPercentage: 0, taxPercentage: 0, stock: 18, addedDaysAgo: 15 },
  { id: 136, storeId: 2, categoryId: 24, name: 'Tata Sampann Moong Dal', description: 'Unpolished yellow moong dal', brand: 'Tata Sampann', sku: 'GB-GRN-136', unit: 'G', unitQuantity: 500, price: 95, discountPercentage: 0, taxPercentage: 0, stock: 26, addedDaysAgo: 29 },
  // Frozen Foods
  { id: 137, storeId: 1, categoryId: 25, name: 'McCain French Fries', description: 'Frozen crispy french fries', brand: 'McCain', sku: 'FM-FRZ-137', unit: 'G', unitQuantity: 420, price: 125, discountPercentage: 0, taxPercentage: 12, stock: 16, addedDaysAgo: 23 },
  { id: 138, storeId: 1, categoryId: 25, name: 'Amul Vanilla Ice Cream', description: 'Creamy vanilla ice cream tub', brand: 'Amul', sku: 'FM-FRZ-138', unit: 'L', unitQuantity: 1, price: 180, discountPercentage: 0, taxPercentage: 18, stock: 9, addedDaysAgo: 6 },
  { id: 139, storeId: 2, categoryId: 25, name: 'Safal Frozen Green Peas', description: 'Sweet green peas, frozen fresh', brand: 'Safal', sku: 'GB-FRZ-139', unit: 'G', unitQuantity: 500, price: 95, discountPercentage: 10, taxPercentage: 5, stock: 24, addedDaysAgo: 20 },
  // Personal Care
  { id: 140, storeId: 1, categoryId: 26, name: 'Dove Cream Beauty Bar', description: 'Moisturising bathing bar', brand: 'Dove', sku: 'FM-PRC-140', unit: 'G', unitQuantity: 125, price: 72, discountPercentage: 0, taxPercentage: 18, stock: 40, addedDaysAgo: 32 },
  { id: 141, storeId: 1, categoryId: 26, name: 'Colgate Strong Teeth Toothpaste', description: 'Calcium toothpaste for strong teeth', brand: 'Colgate', sku: 'FM-PRC-141', unit: 'G', unitQuantity: 200, price: 110, discountPercentage: 5, taxPercentage: 18, stock: 50, addedDaysAgo: 34 },
  { id: 142, storeId: 2, categoryId: 26, name: 'Himalaya Neem Face Wash', description: 'Purifying neem face wash', brand: 'Himalaya', sku: 'GB-PRC-142', unit: 'ML', unitQuantity: 150, price: 175, discountPercentage: 10, taxPercentage: 18, stock: 28, addedDaysAgo: 18 },
  { id: 143, storeId: 2, categoryId: 26, name: 'Biotique Green Apple Shampoo', description: 'Fresh daily purifying shampoo', brand: 'Biotique', sku: 'GB-PRC-143', unit: 'ML', unitQuantity: 180, price: 199, discountPercentage: 0, taxPercentage: 18, stock: 2, addedDaysAgo: 9 },

  // ----- More categories (27-36), 5 products each: ids 201-250 -----
  // Masala & Spices
  { id: 201, storeId: 1, categoryId: 27, name: 'MDH Garam Masala', description: 'Aromatic blend of whole spices, ground', brand: 'MDH', sku: 'FM-SPC-201', unit: 'G', unitQuantity: 100, price: 92, discountPercentage: 0, taxPercentage: 5, stock: 40, addedDaysAgo: 14 },
  { id: 202, storeId: 1, categoryId: 27, name: 'Everest Turmeric Powder', description: 'Bright yellow haldi powder', brand: 'Everest', sku: 'FM-SPC-202', unit: 'G', unitQuantity: 200, price: 68, discountPercentage: 5, taxPercentage: 5, stock: 55, addedDaysAgo: 22 },
  { id: 203, storeId: 2, categoryId: 27, name: 'Catch Black Pepper Powder', description: 'Finely ground black pepper', brand: 'Catch', sku: 'GB-SPC-203', unit: 'G', unitQuantity: 100, price: 120, discountPercentage: 10, taxPercentage: 5, stock: 20, addedDaysAgo: 9 },
  { id: 204, storeId: 2, categoryId: 27, name: 'Organic Cumin Seeds', description: 'Whole organic jeera', brand: 'Organic Tattva', sku: 'GB-SPC-204', unit: 'G', unitQuantity: 200, price: 140, discountPercentage: 0, taxPercentage: 5, stock: 18, addedDaysAgo: 31 },
  { id: 205, storeId: 1, categoryId: 27, name: 'Everest Kitchen King Masala', description: 'All-purpose masala for vegetable dishes', brand: 'Everest', sku: 'FM-SPC-205', unit: 'G', unitQuantity: 100, price: 85, discountPercentage: 0, taxPercentage: 5, stock: 3, addedDaysAgo: 5 },
  // Oils & Ghee
  { id: 206, storeId: 1, categoryId: 28, name: 'Fortune Sunflower Oil', description: 'Refined sunflower cooking oil', brand: 'Fortune', sku: 'FM-OIL-206', unit: 'L', unitQuantity: 1, price: 175, discountPercentage: 8, taxPercentage: 5, stock: 30, addedDaysAgo: 19 },
  { id: 207, storeId: 1, categoryId: 28, name: 'Amul Pure Ghee', description: 'Pure cow ghee with a rich aroma', brand: 'Amul', sku: 'FM-OIL-207', unit: 'L', unitQuantity: 1, price: 650, discountPercentage: 5, taxPercentage: 12, stock: 14, addedDaysAgo: 27 },
  { id: 208, storeId: 2, categoryId: 28, name: 'Saffola Gold Oil', description: 'Blended rice bran and sunflower oil', brand: 'Saffola', sku: 'GB-OIL-208', unit: 'L', unitQuantity: 1, price: 210, discountPercentage: 10, taxPercentage: 5, stock: 22, addedDaysAgo: 12 },
  { id: 209, storeId: 2, categoryId: 28, name: 'Cold Pressed Groundnut Oil', description: 'Wood pressed groundnut oil', brand: 'Organic Tattva', sku: 'GB-OIL-209', unit: 'L', unitQuantity: 1, price: 380, discountPercentage: 0, taxPercentage: 5, stock: 10, addedDaysAgo: 6 },
  { id: 210, storeId: 1, categoryId: 28, name: 'Fortune Kachi Ghani Mustard Oil', description: 'Strong and pungent mustard oil', brand: 'Fortune', sku: 'FM-OIL-210', unit: 'L', unitQuantity: 1, price: 190, discountPercentage: 0, taxPercentage: 5, stock: 25, addedDaysAgo: 35 },
  // Dry Fruits & Nuts
  { id: 211, storeId: 1, categoryId: 29, name: 'Happilo California Almonds', description: 'Premium whole almonds', brand: 'Happilo', sku: 'FM-DRF-211', unit: 'G', unitQuantity: 200, price: 299, discountPercentage: 15, taxPercentage: 5, stock: 26, addedDaysAgo: 8 },
  { id: 212, storeId: 1, categoryId: 29, name: 'Happilo Whole Cashews', description: 'Crunchy W320 cashew nuts', brand: 'Happilo', sku: 'FM-DRF-212', unit: 'G', unitQuantity: 200, price: 289, discountPercentage: 10, taxPercentage: 5, stock: 4, addedDaysAgo: 15 },
  { id: 213, storeId: 2, categoryId: 29, name: 'Seedless Black Raisins', description: 'Sweet seedless black kishmish', brand: null, sku: 'GB-DRF-213', unit: 'G', unitQuantity: 250, price: 150, discountPercentage: 0, taxPercentage: 5, stock: 30, addedDaysAgo: 24 },
  { id: 214, storeId: 2, categoryId: 29, name: 'Nutraj Walnut Kernels', description: 'Light halves of walnut kernels', brand: 'Nutraj', sku: 'GB-DRF-214', unit: 'G', unitQuantity: 200, price: 360, discountPercentage: 12, taxPercentage: 5, stock: 12, addedDaysAgo: 3 },
  { id: 215, storeId: 1, categoryId: 29, name: 'Lion Seedless Dates', description: 'Soft seedless khajur', brand: 'Lion', sku: 'FM-DRF-215', unit: 'G', unitQuantity: 500, price: 180, discountPercentage: 5, taxPercentage: 12, stock: 28, addedDaysAgo: 30 },
  // Sauces & Pickles
  { id: 216, storeId: 1, categoryId: 30, name: 'Kissan Fresh Tomato Ketchup', description: 'Tangy tomato ketchup', brand: 'Kissan', sku: 'FM-SAU-216', unit: 'G', unitQuantity: 950, price: 155, discountPercentage: 10, taxPercentage: 12, stock: 35, addedDaysAgo: 26 },
  { id: 217, storeId: 1, categoryId: 30, name: 'Veeba Eggless Mayonnaise', description: 'Creamy eggless mayonnaise', brand: 'Veeba', sku: 'FM-SAU-217', unit: 'G', unitQuantity: 250, price: 99, discountPercentage: 0, taxPercentage: 12, stock: 20, addedDaysAgo: 11 },
  { id: 218, storeId: 2, categoryId: 30, name: "Mother's Recipe Mango Pickle", description: 'Spicy raw mango pickle in oil', brand: "Mother's Recipe", sku: 'GB-SAU-218', unit: 'G', unitQuantity: 500, price: 160, discountPercentage: 0, taxPercentage: 12, stock: 15, addedDaysAgo: 33 },
  { id: 219, storeId: 2, categoryId: 30, name: "Ching's Secret Schezwan Chutney", description: 'Hot and spicy schezwan chutney', brand: "Ching's Secret", sku: 'GB-SAU-219', unit: 'G', unitQuantity: 250, price: 95, discountPercentage: 5, taxPercentage: 12, stock: 0, addedDaysAgo: 7 },
  { id: 220, storeId: 1, categoryId: 30, name: 'Maggi Hot & Sweet Tomato Chilli Sauce', description: 'Sweet and spicy tomato chilli sauce', brand: 'Maggi', sku: 'FM-SAU-220', unit: 'G', unitQuantity: 400, price: 130, discountPercentage: 0, taxPercentage: 12, stock: 18, addedDaysAgo: 17 },
  // Noodles & Pasta
  { id: 221, storeId: 1, categoryId: 31, name: 'Maggi 2-Minute Masala Noodles', description: 'Instant masala noodles, pack of 4', brand: 'Maggi', sku: 'FM-NDL-221', unit: 'G', unitQuantity: 280, price: 56, discountPercentage: 0, taxPercentage: 18, stock: 60, addedDaysAgo: 21 },
  { id: 222, storeId: 1, categoryId: 31, name: 'Sunfeast Yippee Magic Masala Noodles', description: 'Non-sticky instant noodles, pack of 6', brand: 'Sunfeast', sku: 'FM-NDL-222', unit: 'G', unitQuantity: 360, price: 84, discountPercentage: 5, taxPercentage: 18, stock: 40, addedDaysAgo: 13 },
  { id: 223, storeId: 2, categoryId: 31, name: 'Del Monte Penne Pasta', description: 'Durum wheat penne rigate', brand: 'Del Monte', sku: 'GB-NDL-223', unit: 'G', unitQuantity: 500, price: 120, discountPercentage: 10, taxPercentage: 12, stock: 18, addedDaysAgo: 10 },
  { id: 224, storeId: 2, categoryId: 31, name: 'MTR Rava Idli Mix', description: 'Ready mix for soft rava idlis', brand: 'MTR', sku: 'GB-NDL-224', unit: 'G', unitQuantity: 500, price: 135, discountPercentage: 0, taxPercentage: 18, stock: 22, addedDaysAgo: 28 },
  { id: 225, storeId: 1, categoryId: 31, name: "Ching's Hakka Noodles", description: 'Veg hakka noodles', brand: "Ching's Secret", sku: 'FM-NDL-225', unit: 'G', unitQuantity: 150, price: 45, discountPercentage: 0, taxPercentage: 18, stock: 30, addedDaysAgo: 4 },
  // Chocolates & Sweets
  { id: 226, storeId: 1, categoryId: 32, name: 'Cadbury Dairy Milk Silk', description: 'Smooth milk chocolate bar', brand: 'Cadbury', sku: 'FM-CHO-226', unit: 'G', unitQuantity: 150, price: 190, discountPercentage: 0, taxPercentage: 18, stock: 32, addedDaysAgo: 9 },
  { id: 227, storeId: 1, categoryId: 32, name: 'Nestle KitKat', description: 'Crispy wafer fingers in milk chocolate, pack of 5', brand: 'Nestle', sku: 'FM-CHO-227', unit: 'PIECE', unitQuantity: 5, price: 100, discountPercentage: 0, taxPercentage: 18, stock: 45, addedDaysAgo: 16 },
  { id: 228, storeId: 2, categoryId: 32, name: "Haldiram's Soan Papdi", description: 'Flaky gram flour sweet', brand: "Haldiram's", sku: 'GB-CHO-228', unit: 'G', unitQuantity: 500, price: 140, discountPercentage: 15, taxPercentage: 5, stock: 20, addedDaysAgo: 20 },
  { id: 229, storeId: 2, categoryId: 32, name: 'Bikanervala Rasgulla', description: 'Soft rasgullas in sugar syrup, tin', brand: 'Bikanervala', sku: 'GB-CHO-229', unit: 'KG', unitQuantity: 1, price: 240, discountPercentage: 0, taxPercentage: 5, stock: 14, addedDaysAgo: 34 },
  { id: 230, storeId: 1, categoryId: 32, name: 'Ferrero Rocher', description: 'Hazelnut chocolates, box of 16', brand: 'Ferrero', sku: 'FM-CHO-230', unit: 'PIECE', unitQuantity: 16, price: 799, discountPercentage: 10, taxPercentage: 18, stock: 5, addedDaysAgo: 2 },
  // Baby Care
  { id: 231, storeId: 1, categoryId: 33, name: 'Pampers Baby Dry Pants Medium', description: 'Diaper pants for 7-12 kg babies, 56 pieces', brand: 'Pampers', sku: 'FM-BBY-231', unit: 'PIECE', unitQuantity: 56, price: 1299, discountPercentage: 20, taxPercentage: 12, stock: 12, addedDaysAgo: 18 },
  { id: 232, storeId: 1, categoryId: 33, name: 'Himalaya Gentle Baby Wipes', description: 'Soft wipes with aloe vera, 72 pieces', brand: 'Himalaya', sku: 'FM-BBY-232', unit: 'PIECE', unitQuantity: 72, price: 199, discountPercentage: 10, taxPercentage: 12, stock: 30, addedDaysAgo: 23 },
  { id: 233, storeId: 2, categoryId: 33, name: 'Nestle Cerelac Wheat Apple', description: 'Baby cereal from 8 months', brand: 'Nestle', sku: 'GB-BBY-233', unit: 'G', unitQuantity: 300, price: 285, discountPercentage: 0, taxPercentage: 18, stock: 16, addedDaysAgo: 12 },
  { id: 234, storeId: 2, categoryId: 33, name: "Johnson's Baby Shampoo", description: 'No more tears baby shampoo', brand: "Johnson's", sku: 'GB-BBY-234', unit: 'ML', unitQuantity: 200, price: 210, discountPercentage: 5, taxPercentage: 18, stock: 22, addedDaysAgo: 29 },
  { id: 235, storeId: 1, categoryId: 33, name: 'Himalaya Baby Lotion', description: 'Gentle moisturising baby lotion', brand: 'Himalaya', sku: 'FM-BBY-235', unit: 'ML', unitQuantity: 200, price: 185, discountPercentage: 0, taxPercentage: 18, stock: 18, addedDaysAgo: 6 },
  // Pet Care
  { id: 236, storeId: 1, categoryId: 34, name: 'Pedigree Adult Dog Food Chicken & Vegetables', description: 'Complete dry food for adult dogs', brand: 'Pedigree', sku: 'FM-PET-236', unit: 'KG', unitQuantity: 3, price: 840, discountPercentage: 10, taxPercentage: 18, stock: 10, addedDaysAgo: 25 },
  { id: 237, storeId: 1, categoryId: 34, name: 'Whiskas Adult Cat Food Ocean Fish', description: 'Dry food for adult cats', brand: 'Whiskas', sku: 'FM-PET-237', unit: 'KG', unitQuantity: 1.2, price: 520, discountPercentage: 5, taxPercentage: 18, stock: 8, addedDaysAgo: 14 },
  { id: 238, storeId: 2, categoryId: 34, name: 'Drools Puppy Chicken & Egg', description: 'Dry food for puppies', brand: 'Drools', sku: 'GB-PET-238', unit: 'KG', unitQuantity: 3, price: 780, discountPercentage: 15, taxPercentage: 18, stock: 6, addedDaysAgo: 9 },
  { id: 239, storeId: 2, categoryId: 34, name: 'Pedigree Dentastix Medium', description: 'Dental chew treats for medium dogs', brand: 'Pedigree', sku: 'GB-PET-239', unit: 'G', unitQuantity: 180, price: 199, discountPercentage: 0, taxPercentage: 18, stock: 25, addedDaysAgo: 19 },
  { id: 240, storeId: 1, categoryId: 34, name: 'Purepet Lavender Cat Litter', description: 'Clumping cat litter with lavender scent', brand: 'Purepet', sku: 'FM-PET-240', unit: 'KG', unitQuantity: 5, price: 450, discountPercentage: 0, taxPercentage: 18, stock: 12, addedDaysAgo: 31 },
  // Health & Wellness
  { id: 241, storeId: 1, categoryId: 35, name: 'Dabur Chyawanprash', description: 'Ayurvedic immunity booster', brand: 'Dabur', sku: 'FM-HLT-241', unit: 'KG', unitQuantity: 1, price: 375, discountPercentage: 10, taxPercentage: 12, stock: 20, addedDaysAgo: 30 },
  { id: 242, storeId: 1, categoryId: 35, name: 'Bournvita Health Drink', description: 'Chocolate health drink powder', brand: 'Cadbury', sku: 'FM-HLT-242', unit: 'G', unitQuantity: 500, price: 245, discountPercentage: 0, taxPercentage: 18, stock: 35, addedDaysAgo: 15 },
  { id: 243, storeId: 2, categoryId: 35, name: 'Horlicks Classic Malt', description: 'Malted health drink powder', brand: 'Horlicks', sku: 'GB-HLT-243', unit: 'G', unitQuantity: 500, price: 260, discountPercentage: 5, taxPercentage: 18, stock: 18, addedDaysAgo: 22 },
  { id: 244, storeId: 2, categoryId: 35, name: 'Electral ORS Powder', description: 'Oral rehydration salts, 10 sachets', brand: 'Electral', sku: 'GB-HLT-244', unit: 'PACK', unitQuantity: 1, price: 220, discountPercentage: 0, taxPercentage: 12, stock: 40, addedDaysAgo: 7 },
  { id: 245, storeId: 1, categoryId: 35, name: 'Patanjali Aloe Vera Juice', description: 'Aloe vera juice with fibre', brand: 'Patanjali', sku: 'FM-HLT-245', unit: 'L', unitQuantity: 1, price: 220, discountPercentage: 10, taxPercentage: 12, stock: 4, addedDaysAgo: 11 },
  // Pooja Needs
  { id: 246, storeId: 1, categoryId: 36, name: 'Cycle Pure Agarbatti', description: 'Three-in-one fragrance incense sticks', brand: 'Cycle', sku: 'FM-POO-246', unit: 'PACK', unitQuantity: 1, price: 90, discountPercentage: 0, taxPercentage: 5, stock: 50, addedDaysAgo: 36 },
  { id: 247, storeId: 1, categoryId: 36, name: 'Mangaldeep Camphor', description: 'Pure camphor tablets for aarti', brand: 'Mangaldeep', sku: 'FM-POO-247', unit: 'G', unitQuantity: 50, price: 80, discountPercentage: 0, taxPercentage: 18, stock: 30, addedDaysAgo: 20 },
  { id: 248, storeId: 2, categoryId: 36, name: 'Cotton Wicks', description: 'Round cotton wicks for diyas', brand: null, sku: 'GB-POO-248', unit: 'PACK', unitQuantity: 1, price: 30, discountPercentage: 0, taxPercentage: 5, stock: 60, addedDaysAgo: 27 },
  { id: 249, storeId: 2, categoryId: 36, name: 'Clay Diyas', description: 'Hand-made clay lamps, pack of 6', brand: null, sku: 'GB-POO-249', unit: 'PIECE', unitQuantity: 6, price: 60, discountPercentage: 0, taxPercentage: 5, stock: 25, addedDaysAgo: 4 },
  { id: 250, storeId: 1, categoryId: 36, name: 'Zed Black Dhoop Sticks', description: 'Long-lasting dhoop sticks', brand: 'Zed Black', sku: 'FM-POO-250', unit: 'PACK', unitQuantity: 1, price: 75, discountPercentage: 0, taxPercentage: 5, stock: 40, addedDaysAgo: 13 },
]

const DAY = 24 * 60 * 60 * 1000

/** Rounds money to 2 decimals, like the backend (HALF_UP). */
function roundMoney(amount: number): number {
  return Math.round(amount * 100) / 100
}

/** Average stars (1 decimal) and count of the visible demo reviews of one product. */
function ratingOf(productId: number): { ratingAverage: number | null; ratingCount: number } {
  const ratings = demoReviews.filter((review) => review.productId === productId && review.status === 'VISIBLE').map((review) => review.rating)
  if (ratings.length === 0) return { ratingAverage: null, ratingCount: 0 }
  const average = ratings.reduce((sum, rating) => sum + rating, 0) / ratings.length
  return { ratingAverage: Math.round(average * 10) / 10, ratingCount: ratings.length }
}

/** Builds the ProductResponse JSON that product-read-service would return for a row. */
export function toProduct(row: DemoProductRow): Product {
  const category = demoCategories.find((c) => c.id === row.categoryId)
  return {
    id: row.id,
    storeId: row.storeId,
    categoryId: row.categoryId,
    categoryName: category?.name ?? '',
    name: row.name,
    description: row.description,
    brand: row.brand,
    sku: row.sku,
    unit: row.unit,
    unitQuantity: row.unitQuantity,
    price: row.price,
    discountPercentage: row.discountPercentage,
    sellingPrice: roundMoney((row.price * (100 - row.discountPercentage)) / 100),
    taxPercentage: row.taxPercentage,
    imageUrls: [],
    status: row.status ?? 'ACTIVE',
    availableQuantity: row.stock,
    inStock: row.stock > 0,
    lowStock: row.stock <= LOW_STOCK_THRESHOLD,
    ...ratingOf(row.id),
    updatedAt: new Date(Date.now() - row.addedDaysAgo * DAY).toISOString(),
  }
}
