import { cn } from '../../utils/cn'
import { Skeleton } from '../ui/Skeleton'

/** Grey placeholder with the shape of a ProductCard (regular or small), shown while products load. */
export function ProductCardSkeleton({ size = 'regular' }: { size?: 'regular' | 'small' }) {
  const small = size === 'small'
  return (
    <div className="flex h-full flex-col overflow-hidden rounded-2xl border border-line bg-card" aria-hidden="true">
      <Skeleton className={cn('rounded-none', small ? 'aspect-[3/2]' : 'aspect-[4/3]')} />
      <div className={cn('flex flex-1 flex-col gap-2', small ? 'p-2.5' : 'p-3')}>
        <Skeleton className="h-4 w-4/5" />
        <Skeleton className="h-4 w-1/2" />
        <Skeleton className="h-3 w-2/3" />
        <Skeleton className="h-3 w-1/3" />
        <div className="mt-auto flex items-center justify-between pt-1">
          <Skeleton className="h-5 w-16" />
          <Skeleton className="h-8 w-16" />
        </div>
      </div>
    </div>
  )
}
