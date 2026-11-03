/*
 * The catalog has no photos yet, so products and categories without an image show a matching emoji
 * on a pastel tile instead (see ProductImage and CategoryIcon).
 */

/** First match wins, so the more specific words come first ("shampoo" before "apple" for "Green Apple Shampoo"). */
const PRODUCT_EMOJIS: Array<[keyword: string, emoji: string]> = [
  // before 'milk', 'egg', 'tomato', 'mango', 'chicken', 'apple' and 'haldiram' below
  ['dairy milk', '🍫'], ['mayonnaise', '🫙'], ['ketchup', '🥫'], ['sauce', '🥫'], ['pickle', '🫙'],
  ['dog food', '🐶'], ['puppy', '🐶'], ['cat food', '🐱'], ['cerelac', '🥣'], ['soan papdi', '🍬'],
  ['noodles', '🍜'], ['pasta', '🍝'], ['ghee', '🧈'], ['almonds', '🥜'], ['cashews', '🥜'],
  ['shampoo', '🧴'], ['face wash', '🧴'], ['toothpaste', '🪥'], ['beauty bar', '🧼'], ['soap', '🧼'],
  ['ice cream', '🍨'], ['corn flakes', '🥣'], ['oats', '🥣'], ['curd', '🥣'], ['dahi', '🥣'],
  ['tomato', '🍅'], ['onion', '🧅'], ['potato', '🥔'], ['spinach', '🥬'], ['carrot', '🥕'], ['cauliflower', '🥦'],
  ['broccoli', '🥦'], ['capsicum', '🫑'], ['cucumber', '🥒'], ['lemon', '🍋'], ['peas', '🌱'],
  ['banana', '🍌'], ['apple', '🍎'], ['mango', '🥭'], ['kiwi', '🥝'], ['orange', '🍊'], ['strawberr', '🍓'],
  ['grapes', '🍇'], ['watermelon', '🍉'],
  ['milk', '🥛'], ['butter', '🧈'], ['egg', '🥚'], ['cheese', '🧀'], ['paneer', '🧀'],
  ["lay's", '🍟'], ['fries', '🍟'], ['haldiram', '🥨'], ['cookies', '🍪'], ['biscuit', '🍪'], ['cola', '🥤'], ['aamras', '🧃'], ['juice', '🧃'],
  ['bread', '🍞'], ['jam', '🫙'], ['honey', '🍯'], ['tea', '🍵'], ['coffee', '☕'],
  ['chicken', '🍗'], ['fish', '🐟'], ['prawn', '🦐'],
  ['rice', '🍚'], ['atta', '🌾'], ['dal', '🫘'],
  ['surf', '🧺'], ['detergent', '🧺'], ['dishwash', '🧽'], ['scrub', '🧽'], ['toilet', '🧴'],
]

/** Emoji per category slug (the slug never changes, the name might). */
const CATEGORY_EMOJIS: Record<string, string> = {
  vegetables: '🥦',
  fruits: '🍎',
  'dairy-eggs': '🥛',
  'snacks-beverages': '🍿',
  household: '🧴',
  'breakfast-bakery': '🍞',
  'tea-coffee': '☕',
  'meat-seafood': '🐟',
  'rice-atta-dals': '🌾',
  'frozen-foods': '🧊',
  'personal-care': '🧼',
  'masala-spices': '🌶️',
  'oils-ghee': '🫒',
  'dry-fruits-nuts': '🥜',
  'sauces-pickles': '🥫',
  'noodles-pasta': '🍜',
  'chocolates-sweets': '🍫',
  'baby-care': '👶',
  'pet-care': '🐾',
  'health-wellness': '🌿',
  'pooja-needs': '🪔',
}

const DEFAULT_EMOJI = '🛒'

/** "\bcola" matches "Coca-Cola" but not "chocolate": keywords must start a word. */
const productPatterns = PRODUCT_EMOJIS.map(([keyword, emoji]) => [new RegExp(`\\b${keyword}`, 'i'), emoji] as const)

export function categoryEmoji(slug: string | null | undefined): string {
  return (slug && CATEGORY_EMOJIS[slug]) || DEFAULT_EMOJI
}

/** An emoji for a product name, e.g. "Amul Taaza Toned Milk" -> 🥛. Falls back to the category's emoji. */
export function productEmoji(name: string, categorySlug?: string | null): string {
  const match = productPatterns.find(([pattern]) => pattern.test(name))
  return match ? match[1] : categoryEmoji(categorySlug)
}

/** Pastel tile backgrounds, full class names so Tailwind can find them (never build names like `bg-tile-${n}`). */
const TILE_BACKGROUNDS = ['bg-tile-1', 'bg-tile-2', 'bg-tile-3', 'bg-tile-4', 'bg-tile-5', 'bg-tile-6']

/** The same id always gets the same pastel colour. */
export function tileBackground(id: number): string {
  return TILE_BACKGROUNDS[Math.abs(id) % TILE_BACKGROUNDS.length]
}
