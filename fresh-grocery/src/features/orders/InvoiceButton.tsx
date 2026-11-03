import { FileText } from 'lucide-react'
import { toast } from 'sonner'
import { isApiError } from '../../api/client'
import { Button } from '../../components/ui/Button'
import { useDownloadInvoice } from './useOrders'

/** "Download invoice": the tax invoice PDF, which exists once the store accepted the order. */
export function InvoiceButton({ orderId }: { orderId: number }) {
  const download = useDownloadInvoice(orderId)

  function handleClick() {
    download.mutate(undefined, {
      onError: (error) => {
        if (!isApiError(error, 401)) toast.error(error.message)
      },
    })
  }

  return (
    <Button variant="outline" loading={download.isPending} onClick={handleClick}>
      <FileText className="size-4" aria-hidden="true" />
      Download invoice
    </Button>
  )
}
