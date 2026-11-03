import type { Product } from '../../api/types'
import { useCategories } from '../../hooks/useCategories'
import { cn } from '../../utils/cn'
import { productEmoji } from '../../utils/emoji'
import { ImageWithFallback } from '../ui/ImageWithFallback'

interface ProductImageProps {
  product: Pick<Product, 'name' | 'categoryId' | 'imageUrls'>
  /** size and shape of the box (square by default) */
  className?: string
  /** size of the emoji placeholder, e.g. "text-3xl" for small images */
  emojiClassName?: string
  /** which photo (the product page's thumbnails); the first one by default */
  photoIndex?: number
}

/**
 * The product's first photo, or (no photo yet) a matching emoji. Every product sits on the same theme background
 * (bg-surface, the grey of boxes inside cards; dark in the dark theme), so pictures next to each other never look like
 * colour blocks. It is decoration next to the product name, so it has no alt text of its own.
 */
export function ProductImage({ product, className, emojiClassName, photoIndex = 0 }: ProductImageProps) {
  const { data: categories } = useCategories()
  const categorySlug = categories?.find((category) => category.id === product.categoryId)?.slug

  const placeholder = (
    <div className="flex size-full items-center justify-center">
      <span className={cn('text-6xl', emojiClassName)} aria-hidden="true">
        {productEmoji(product.name, categorySlug)}
      </span>
    </div>
  )

  return (
    <div className={cn('aspect-square w-full overflow-hidden rounded-xl bg-surface', className)}>
      <ImageWithFallback src={product.imageUrls[photoIndex]} alt="" fallback={placeholder} className="size-full object-contain" />
    </div>
  )
}
