import { PageTitle } from '../../components/layout/PageTitle'
import { DailyFreshPicks } from './DailyFreshPicks'
import { DealsOfTheDay } from './DealsOfTheDay'
import { FeaturedCategories } from './FeaturedCategories'
import { HeroSlider } from './HeroSlider'
import { OffersStrip } from './OffersStrip'
import { PopularProducts } from './PopularProducts'
import { ProductColumns } from './ProductColumns'
import { PromoCards } from './PromoCards'

/**
 * The home page: a list of independent sections. Each one loads its own data, shows its own skeleton while loading
 * and its own error with "Try again", so one failing request never blanks the whole page.
 */
export function HomePage() {
  return (
    <>
      <PageTitle />
      <HeroSlider />
      <FeaturedCategories />
      <PromoCards />
      <PopularProducts />
      <DailyFreshPicks />
      <DealsOfTheDay />
      <OffersStrip />
      <ProductColumns />
    </>
  )
}
