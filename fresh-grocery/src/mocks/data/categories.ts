import type { Category } from '../../api/types'

/*
 * The demo categories.
 * Ids 1-5 are copied from the backend's local seed (product-service); ids 21-36 are demo categories
 * that "npm run seed:sql" adds to that seed, so mock data and real data stay the same.
 */
export const demoCategories: Category[] = [
  { id: 1, name: 'Vegetables', slug: 'vegetables', description: 'Fresh vegetables delivered daily', imageUrl: null, displayOrder: 1, active: true },
  { id: 2, name: 'Fruits', slug: 'fruits', description: 'Seasonal and exotic fruits', imageUrl: null, displayOrder: 2, active: true },
  { id: 3, name: 'Dairy & Eggs', slug: 'dairy-eggs', description: 'Milk, curd, butter, cheese, eggs', imageUrl: null, displayOrder: 3, active: true },
  { id: 4, name: 'Snacks & Beverages', slug: 'snacks-beverages', description: 'Chips, namkeen, biscuits, drinks', imageUrl: null, displayOrder: 4, active: true },
  { id: 5, name: 'Household', slug: 'household', description: 'Cleaning and home care', imageUrl: null, displayOrder: 5, active: true },
  { id: 21, name: 'Breakfast & Bakery', slug: 'breakfast-bakery', description: 'Bread, cereals, oats, jams and honey', imageUrl: null, displayOrder: 6, active: true },
  { id: 22, name: 'Tea & Coffee', slug: 'tea-coffee', description: 'Tea leaves, green tea and coffee', imageUrl: null, displayOrder: 7, active: true },
  { id: 23, name: 'Meat & Seafood', slug: 'meat-seafood', description: 'Fresh chicken, fish and prawns', imageUrl: null, displayOrder: 8, active: true },
  { id: 24, name: 'Rice, Atta & Dals', slug: 'rice-atta-dals', description: 'Rice, wheat flour and pulses', imageUrl: null, displayOrder: 9, active: true },
  { id: 25, name: 'Frozen Foods', slug: 'frozen-foods', description: 'Frozen snacks, peas and ice cream', imageUrl: null, displayOrder: 10, active: true },
  { id: 26, name: 'Personal Care', slug: 'personal-care', description: 'Soap, face wash, shampoo and oral care', imageUrl: null, displayOrder: 11, active: true },
  { id: 27, name: 'Masala & Spices', slug: 'masala-spices', description: 'Whole spices, powders and masala mixes', imageUrl: null, displayOrder: 12, active: true },
  { id: 28, name: 'Oils & Ghee', slug: 'oils-ghee', description: 'Cooking oils and pure ghee', imageUrl: null, displayOrder: 13, active: true },
  { id: 29, name: 'Dry Fruits & Nuts', slug: 'dry-fruits-nuts', description: 'Almonds, cashews, raisins, walnuts and dates', imageUrl: null, displayOrder: 14, active: true },
  { id: 30, name: 'Sauces & Pickles', slug: 'sauces-pickles', description: 'Ketchup, mayonnaise, pickles and chutneys', imageUrl: null, displayOrder: 15, active: true },
  { id: 31, name: 'Noodles & Pasta', slug: 'noodles-pasta', description: 'Instant noodles, pasta and ready-to-cook mixes', imageUrl: null, displayOrder: 16, active: true },
  { id: 32, name: 'Chocolates & Sweets', slug: 'chocolates-sweets', description: 'Chocolates and Indian sweets', imageUrl: null, displayOrder: 17, active: true },
  { id: 33, name: 'Baby Care', slug: 'baby-care', description: 'Diapers, wipes, baby food and bath care', imageUrl: null, displayOrder: 18, active: true },
  { id: 34, name: 'Pet Care', slug: 'pet-care', description: 'Food, treats and litter for dogs and cats', imageUrl: null, displayOrder: 19, active: true },
  { id: 35, name: 'Health & Wellness', slug: 'health-wellness', description: 'Health drinks, chyawanprash and ORS', imageUrl: null, displayOrder: 20, active: true },
  { id: 36, name: 'Pooja Needs', slug: 'pooja-needs', description: 'Agarbatti, camphor, wicks and diyas', imageUrl: null, displayOrder: 21, active: true },
]
