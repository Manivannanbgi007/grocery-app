import type { Address } from '../../api/types'

/* The seed addresses of customer-service (local profile), per user id: Priya (4) has two, Rahul (7) one. */

export const demoAddresses: Record<number, Address[]> = {
  4: [
    {
      id: 1,
      label: 'HOME',
      contactName: 'Priya Sharma',
      contactPhone: '9000000004',
      addressLine1: 'Flat 302, Lakeview Apartments',
      addressLine2: '2nd Main Road, HAL 2nd Stage',
      landmark: 'Near Indiranagar Metro',
      city: 'Bengaluru',
      state: 'Karnataka',
      pincode: '560038',
      latitude: 12.9719,
      longitude: 77.6412,
      defaultAddress: true,
    },
    {
      // Koramangala: Green Basket delivers here, Fresh Mart does not
      id: 2,
      label: 'WORK',
      contactName: 'Priya Sharma',
      contactPhone: '9000000004',
      addressLine1: 'Tower B, 5th Floor, Tech Park',
      addressLine2: 'Koramangala 4th Block',
      landmark: null,
      city: 'Bengaluru',
      state: 'Karnataka',
      pincode: '560034',
      latitude: 12.9337,
      longitude: 77.627,
      defaultAddress: false,
    },
  ],
  7: [
    {
      id: 3,
      label: 'HOME',
      contactName: 'Rahul Verma',
      contactPhone: '9000000007',
      addressLine1: '18, Cambridge Layout',
      addressLine2: 'Ulsoor',
      landmark: null,
      city: 'Bengaluru',
      state: 'Karnataka',
      pincode: '560008',
      latitude: 12.9775,
      longitude: 77.6208,
      defaultAddress: true,
    },
  ],
}
