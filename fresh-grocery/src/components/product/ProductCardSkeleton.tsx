import { cn } from '../../utils/cn'
import { Skeleton } from '../ui/Skeleton'

/** Grey placeholder with the shape of a ProductCard (regular or small), shown while products load. */
export function ProductCardSkeleton({ size = 'regular' }: { size?: 'regular' | 'small' }) {
  const small = size === 'small'
  return (
    <div className={cn('flex h-full flex-col rounded-2xl border border-line bg-card', small ? 'gap-2 p-2.5' : 'gap-3 p-4')} aria-hidden="true">
      <Skeleton className={small ? 'aspect-[4/3]' : 'mx-3 mt-6 mb-3 aspect-square'} />
      {!small && <Skeleton className="h-3 w-1/3" />}
      <Skeleton className="h-4 w-4/5" />
      <Skeleton className="h-3 w-1/2" />
      <div className={cn('mt-auto flex items-center justify-between', small ? 'pt-2' : 'pt-4')}>
        <Skeleton className="h-5 w-16" />
        <Skeleton className="h-8 w-16" />
      </div>
    </div>
  )
}
