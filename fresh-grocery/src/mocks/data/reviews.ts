import type { Review } from '../../api/types'

/*
 * Demo product reviews. The reviewers are the two customers of the backend's local seed (auth-service).
 * A customer can review a product once per delivered order, so every product has at most one review per customer.
 * Ids start at 101: lower ids may already be used by reviews written while testing the backend.
 */
const PRIYA = 'Priya Sharma' // customer1@grocery.com
const RAHUL = 'Rahul Verma' // customer2@grocery.com

const DAY = 24 * 60 * 60 * 1000

function review(id: number, productId: number, storeId: number, reviewerName: string, rating: number, comment: string, daysAgo: number): Review {
  return {
    id,
    productId,
    storeId,
    // Each customer reviews from one delivered order per store (demo order ids)
    orderId: (reviewerName === PRIYA ? 9000 : 9100) + storeId,
    reviewerName,
    rating,
    comment,
    status: 'VISIBLE',
    hiddenReason: null,
    createdAt: new Date(Date.now() - daysAgo * DAY).toISOString(),
  }
}

export const demoReviews: Review[] = [
  review(101, 1, 1, PRIYA, 5, 'Fresh and firm, perfect for curries.', 12),
  review(102, 1, 1, RAHUL, 4, 'Good tomatoes, a couple were a bit soft.', 9),
  review(103, 4, 1, PRIYA, 5, 'Ripe and sweet, the whole dozen was good.', 20),
  review(104, 5, 1, RAHUL, 4, 'Crisp apples. Slightly small this time.', 15),
  review(105, 5, 1, PRIYA, 5, 'Very juicy, my kids loved them.', 6),
  review(106, 6, 1, PRIYA, 5, 'Always fresh, delivered cold.', 3),
  review(107, 6, 1, RAHUL, 5, 'Same as in the shop, well packed.', 8),
  review(108, 7, 1, RAHUL, 4, 'Butter is fine, packet was a little dented.', 18),
  review(109, 7, 1, PRIYA, 5, 'Fresh stock, long expiry date.', 25),
  review(110, 9, 1, RAHUL, 4, 'Crunchy, as expected.', 30),
  review(111, 10, 1, PRIYA, 5, 'Works well in my front-load machine.', 40),
  review(112, 10, 1, RAHUL, 4, 'Good value with the discount.', 22),
  review(113, 11, 2, PRIYA, 5, 'Clean leaves, very little sand.', 5),
  review(114, 12, 2, RAHUL, 4, 'Sweet carrots, nice for salads.', 11),
  review(115, 14, 2, PRIYA, 5, 'Thick curd, tastes homemade.', 2),
  review(116, 14, 2, RAHUL, 4, 'Good curd, I wish there was a bigger pack.', 14),
  review(117, 15, 2, RAHUL, 5, 'Our favourite tea-time snack.', 19),
  review(118, 15, 2, PRIYA, 5, 'Fresh and crispy.', 27),
  review(119, 16, 2, PRIYA, 4, 'Cleans greasy pans well.', 33),
  review(120, 102, 1, PRIYA, 4, 'Crunchy and fresh.', 7),
  review(121, 106, 1, RAHUL, 5, 'Sweet and ready to eat.', 10),
  review(122, 108, 2, PRIYA, 5, 'Bright red and sweet, well packed.', 4),
  review(123, 108, 2, RAHUL, 4, 'Tasty, two were bruised.', 6),
  review(124, 112, 1, PRIYA, 4, 'Good for sandwiches.', 13),
  review(125, 115, 1, RAHUL, 3, 'Fine, but it arrived warm.', 16),
  review(126, 116, 1, PRIYA, 5, 'Fresh biscuits, not broken.', 21),
  review(127, 116, 1, RAHUL, 4, 'Good, as always.', 24),
  review(128, 122, 1, RAHUL, 4, 'Crispy flakes, good with cold milk.', 28),
  review(129, 122, 1, PRIYA, 4, 'Good, the box was slightly crushed.', 31),
  review(130, 123, 2, PRIYA, 3, 'A bit too spicy for me.', 9),
  review(131, 123, 2, RAHUL, 4, 'Quick breakfast, tastes good.', 12),
  review(132, 125, 1, PRIYA, 5, 'Pure and thick honey.', 17),
  review(133, 126, 1, RAHUL, 5, 'Strong and fragrant, our daily tea.', 26),
  review(134, 126, 1, PRIYA, 5, 'Best tea at this price.', 35),
  review(135, 127, 1, PRIYA, 4, 'Good coffee, jar was sealed well.', 14),
  review(136, 127, 1, RAHUL, 5, 'Rich taste.', 20),
  review(137, 133, 1, RAHUL, 5, 'Long grains, cooks well.', 23),
  review(138, 133, 1, PRIYA, 4, 'Good rice, a bit pricey.', 29),
  review(139, 134, 1, PRIYA, 4, 'Soft rotis every time.', 37),
  review(140, 137, 1, RAHUL, 4, 'Crispy when air fried.', 8),
  review(141, 137, 1, PRIYA, 3, 'Okay, the pack had some ice.', 15),
  review(142, 141, 1, RAHUL, 4, 'Usual Colgate quality.', 19),
  review(143, 142, 2, PRIYA, 5, 'Gentle on my skin.', 10),
]
