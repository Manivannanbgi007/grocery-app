/** The colour names of the theme in styles/index.css ("--color-brand" is "brand"). */
export type ThemeColourName = 'brand' | 'brand-dark' | 'body'

/**
 * A colour of the theme as a value ("#09792f"), for code that needs a colour and cannot use a class: the map's lines
 * (Leaflet) and Razorpay's checkout. It reads the current value, so it follows the dark theme too, and the colour
 * stays in one place (index.css). Empty when the page has no styles (the tests' fake browser).
 */
export function themeColour(name: ThemeColourName): string {
  return getComputedStyle(document.documentElement).getPropertyValue(`--color-${name}`).trim()
}
