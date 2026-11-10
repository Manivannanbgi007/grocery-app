# Exports (Excel, CSV, PDF)

Every data table in Fresh Grocery has an **Export** button above it (in the row with "Showing 1–20 of 132 …"), for every
kind of user: admins and the super admin, vendors, delivery partners and customers. It exports the **whole list with the
filters in use**, not only the page on screen.

| Format | File | Made for | Rows |
|---|---|---|---|
| Excel | `.xlsx` | Working with the numbers: amounts and counts are numbers (Excel sums them), dates are real dates, the header row stays in view. A second sheet, **About**, says who exported the list, when, with which filters, and whether rows are missing. | up to 10,000 |
| CSV | `.csv` | Any other tool: plain data, UTF-8 with a byte order mark (Excel shows "₹" correctly), RFC 4180 quoting, `2026-11-09 15:30` times. | up to 10,000 |
| PDF | `.pdf` | Reading, sharing and printing: A4 (landscape for wide tables), title, who and when, filters, the header row on every page, page numbers. | up to 2,000 |

File names: `<list>-<date>-<time>.<ext>`, e.g. `orders-2026-11-09-1530.xlsx`.

## How it behaves

- **Same data, same rights.** The export loads the list page by page from the *same* API and filters the table uses,
  so the server's role checks apply unchanged: nobody can export what they could not see on screen. No new endpoint.
- **Limits are said beforehand.** When a list is longer than a format holds, the menu says so ("The first 2,000 of
  4,210: narrow the filters for the rest"), the file says it again (PDF header, Excel "About" sheet), and so does the
  message after the export.
- **Gentle on the backend.** Pages of 100 rows (every service's `max-page-size`), three at a time, well under the
  gateway's rate limit (20 requests per second per user). A `429 Too Many Requests` or `503` is retried after 0.5, 1, 2
  and 4 s; any other error stops the export with its message.
- **Progress and Cancel.** While it runs, the button shows the progress and a Cancel button; leaving the page cancels it.
- **Consistent values.** Times are India time, like everywhere in the app (`Placed (IST)`); amounts are rupees
  (`Total (₹)`); statuses use the words on screen. A row that moves to the next page while the pages load is kept once.
- **Safe CSV.** A text that a spreadsheet would run as a formula (`=…`, `+…`, `-…`, `@…`) gets a leading apostrophe
  (CSV injection, OWASP). In `.xlsx` texts are always written as texts.
- **Small app.** The Excel and PDF libraries (`write-excel-file`, `jspdf` + `jspdf-autotable`) load only when someone
  makes such a file. The PDF font, Noto Sans (SIL Open Font License, `fresh-grocery/src/assets/fonts/OFL.txt`), has
  the rupee sign and Latin, Greek and Cyrillic letters; it is fetched once, on the first PDF. Without it (offline), the
  PDF falls back to Helvetica with "Rs." for "₹".

## Adding the export to a table

Everything lives in `fresh-grocery/src/features/export/`. A page declares its columns and where its rows come from;
`AdminOrdersPage.tsx` is the complete example:

```tsx
const EXPORT_COLUMNS: ExportColumn<OrderSummary>[] = [
  { header: 'Order #', type: 'number', value: (order) => order.id },
  { header: 'Placed', type: 'datetime', value: (order) => order.placedAt },
  { header: 'Total', type: 'money', value: (order) => order.totalAmount },
]

const exportSpec: ExportSpec<OrderSummary> = {
  title: 'Orders', fileName: 'orders', itemWords: ['order', 'orders'],
  columns: EXPORT_COLUMNS,
  filters: filterLines([['Status', statusLabel], ['Store', storeName]]),
  rowKey: (order) => order.id,
  // the SAME api function and filters as the table; the page is 0-based
  fetchPage: (page, size, signal) => searchOrders({ status, storeId, page }, size, signal),
}

<TablePagination position="top" … actions={<ExportMenu spec={exportSpec} total={data.totalElements} />} />
```

A list loaded whole (a report) passes `rows: theRows` instead of `fetchPage`. Column types: `text`, `number`, `money`,
`percent`, `date`, `datetime`, `boolean`. Tests: `src/test/exportFile.ts` (`exportList`, `csvLines`).

## Not covered yet

- **Bigger exports** (more than 10,000 rows, e.g. a year of orders) need a server-side export: a job that writes the
  file in the service and emails a link. The browser export is right for the filtered lists people work with.
- **An audit trail of exports** (who exported which list): exports are not written to the platform's audit log yet.
