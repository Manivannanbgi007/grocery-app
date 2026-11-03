import { ArrowLeftRight } from 'lucide-react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import type { Product } from '../../api/types'
import { MAX_COMPARE_ITEMS, useCompare } from '../../features/compare/useCompare'
import { cn } from '../../utils/cn'
import { Button } from '../ui/Button'

interface CompareButtonProps {
  product: Product
  /** icon = the round button on product cards; text = "Compare" on the product page */
  variant?: 'icon' | 'text'
  className?: string
}

/** Adds the product to the comparison (at most 4) or takes it out again. A toggle, like the heart. */
export function CompareButton({ product, variant = 'icon', className }: CompareButtonProps) {
  const compare = useCompare()
  const navigate = useNavigate()
  const chosen = compare.isInCompare(product.id)
  const openCompare = { label: 'Compare now', onClick: () => navigate('/compare') }

  function handleClick() {
    const result = compare.toggle(product)
    if (result === 'added') toast.success(`${product.name} added to the comparison`, { action: openCompare })
    else if (result === 'removed') toast.success(`${product.name} removed from the comparison`)
    else toast.error(`You can compare up to ${MAX_COMPARE_ITEMS} products. Remove one first.`, { action: { label: 'Open the comparison', onClick: () => navigate('/compare') } })
  }

  const icon = <ArrowLeftRight className={cn('size-4', chosen ? 'text-brand-text' : 'text-heading')} aria-hidden="true" />

  if (variant === 'text') {
    return (
      <Button variant="outline" size="lg" onClick={handleClick} aria-pressed={chosen} className={cn('text-heading aria-pressed:border-brand aria-pressed:bg-brand-light', className)}>
        {icon}
        Compare
      </Button>
    )
  }

  return (
    <button
      type="button"
      onClick={handleClick}
      aria-pressed={chosen}
      aria-label={`Compare ${product.name}`}
      className={cn('flex size-9 items-center justify-center rounded-full border border-line bg-card transition-colors hover:border-brand aria-pressed:border-brand aria-pressed:bg-brand-light', className)}
    >
      {icon}
    </button>
  )
}
