/**
 * Where the assistant is shown: the shop (guests and customers) or one of the panels. One chat API answers all of them;
 * the backend picks the tools and the rules from the login (docs/ai-assistant-plan.md, section 14), the frontend only
 * changes these texts.
 */
export type AssistantKind = 'shop' | 'vendor' | 'delivery' | 'admin'

export interface AssistantTexts {
  /** the chat's title */
  title: string
  /** the chat button's name */
  button: string
  /** under the title: what it knows about */
  topics: string
  /** the first message, after "Hi, <name>!" */
  greeting: string
  /** three questions to start with, one click each */
  examples: string[]
  placeholder: string
  /** under an answer the backend checked against the tool results ("grounded") */
  checked: string
  /** what changes once the user is logged in (the shop: a customer can also ask about their own orders) */
  loggedIn?: Partial<Pick<AssistantTexts, 'topics' | 'greeting' | 'examples'>>
}

export const ASSISTANT_TEXTS: Record<AssistantKind, AssistantTexts> = {
  shop: {
    title: 'Shopping assistant',
    button: 'Ask our assistant',
    topics: 'Products, prices, offers, delivery',
    greeting: "Ask me about our products, prices, offers or delivery. I answer from the shop's live data.",
    examples: ['Do you have tomatoes? How much are they?', 'Which offers are running?', 'Is delivery free?'],
    placeholder: 'Ask about a product, an offer or delivery',
    checked: "Checked against the shop's live data",
    loggedIn: {
      topics: 'Products, prices, offers, delivery, your orders',
      greeting: "Ask me about our products, prices, offers or delivery, or about your orders. I answer from the shop's live data.",
      examples: ['Do you have tomatoes? How much are they?', 'Which offers are running?', 'Where is my last order?'],
    },
  },
  vendor: {
    title: 'Store assistant',
    button: 'Ask the assistant',
    topics: 'Your orders, stock and sales',
    greeting: "Ask me about your store: new orders, products running low, your sales and best sellers. I answer from your store's live data.",
    examples: ['Do I have new orders?', 'What is running low on stock?', 'How much did I sell this week?'],
    placeholder: 'Ask about your orders, stock or sales',
    checked: "Checked against your store's live data",
  },
  delivery: {
    title: 'Delivery assistant',
    button: 'Ask the assistant',
    topics: 'Your deliveries and earnings',
    greeting: 'Ask me about your current deliveries, your earnings, or why no deliveries come to you. I answer from your live data.',
    examples: ['Do I have deliveries right now?', 'How much did I earn this week?', 'Am I online?'],
    placeholder: 'Ask about your deliveries or earnings',
    checked: 'Checked against your live data',
  },
  admin: {
    title: 'Admin assistant',
    button: 'Ask the assistant',
    topics: "Today's numbers, waiting work, sales",
    greeting: "Ask me about today's numbers, the work waiting for the team, the latest orders and the sales. I answer from the platform's live data.",
    examples: ['What needs my attention today?', 'How many orders did we get today?', 'Which store sold the most this week?'],
    placeholder: 'Ask about orders, stores, partners or sales',
    checked: "Checked against the platform's live data",
  },
}
