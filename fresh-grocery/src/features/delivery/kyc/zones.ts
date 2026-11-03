/**
 * The delivery zones a partner can choose in the KYC form.
 *
 * delivery-service has no zone list that partners may read (only admins can list zones), so these are the zones of the
 * local seed (delivery-service R__insert_default_delivery_data.sql). Replace this list with an API call once
 * delivery-service offers one; until then, a zone an admin adds later cannot be chosen here.
 */
export const KYC_ZONES: { id: number; name: string; city: string; pincodes: string[] }[] = [
  { id: 1, name: 'Indiranagar', city: 'Bengaluru', pincodes: ['560038', '560008', '560071'] },
  { id: 2, name: 'Koramangala', city: 'Bengaluru', pincodes: ['560034', '560095', '560047'] },
]
