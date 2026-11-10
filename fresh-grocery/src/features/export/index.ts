/*
 * Export of a list to Excel, CSV and PDF. A list page:
 *
 *   const exportSpec: ExportSpec<OrderSummary> = {
 *     title: 'Orders', fileName: 'orders', itemWords: ['order', 'orders'],
 *     columns: ORDER_EXPORT_COLUMNS,                       // [{ header: 'Total', type: 'money', value: (o) => o.totalAmount }, …]
 *     filters: filterLines([['Status', tabLabel], ['Store', storeName]]),
 *     rowKey: (order) => order.id,
 *     fetchPage: (page, size, signal) => searchOrders({ ...sameFiltersAsTheTable, page }, size, signal),
 *   }
 *   <TablePagination position="top" … actions={<ExportMenu spec={exportSpec} total={data.totalElements} />} />
 *
 * See AdminOrdersPage for a complete example.
 */
export { ExportMenu } from './ExportMenu'
export { filterLines } from './exportValues'
export type { ExportColumn, ExportSpec, ExportValueType } from './exportTypes'
