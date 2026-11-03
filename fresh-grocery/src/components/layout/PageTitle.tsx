/** Sets the browser tab title: "Shop | Fresh Grocery". React 19 moves a <title> into the page <head> by itself. */
export function PageTitle({ title }: { title?: string }) {
  return <title>{title ? `${title} | Fresh Grocery` : 'Fresh Grocery: fresh groceries from local stores'}</title>
}
