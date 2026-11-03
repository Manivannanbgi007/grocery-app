import type { User } from '../../api/types'

/*
 * The users of the backend's local seed (auth-service). Every seed user has the password "Password@123".
 * The storefront only lets CUSTOMERs in; the other roles are here so the login page can show the right message.
 */
export const DEMO_PASSWORD = 'Password@123'

export interface DemoUser extends User {
  password: string
}

function seedUser(id: number, fullName: string, email: string, phone: string, role: User['role'], status: User['status'] = 'ACTIVE', storeId: number | null = null): DemoUser {
  return { id, fullName, email, phone, role, status, storeId, lastLoginAt: null, createdAt: '2026-01-01T00:00:00Z', password: DEMO_PASSWORD }
}

export const demoUsers: DemoUser[] = [
  seedUser(1, 'Super Admin', 'superadmin@grocery.com', '9000000001', 'SUPER_ADMIN'),
  seedUser(2, 'Platform Admin', 'admin@grocery.com', '9000000002', 'ADMIN'),
  seedUser(3, 'Ravi Kumar', 'vendor1@grocery.com', '9000000003', 'VENDOR', 'ACTIVE', 1),
  seedUser(4, 'Priya Sharma', 'customer1@grocery.com', '9000000004', 'CUSTOMER'),
  seedUser(5, 'Arjun Singh', 'delivery1@grocery.com', '9000000005', 'DELIVERY_PARTNER'),
  seedUser(6, 'Meena Iyer', 'vendor2@grocery.com', '9000000006', 'VENDOR', 'ACTIVE', 2),
  seedUser(7, 'Rahul Verma', 'customer2@grocery.com', '9000000007', 'CUSTOMER'),
  seedUser(8, 'Karthik Reddy', 'delivery2@grocery.com', '9000000008', 'DELIVERY_PARTNER'),
  seedUser(9, 'Anita Desai', 'customer3@grocery.com', '9000000009', 'CUSTOMER', 'DISABLED'),
  // A vendor without a store yet (to try the store registration and its approval)
  seedUser(10, 'Suresh Patel', 'vendor3@grocery.com', '9000000010', 'VENDOR'),
]
