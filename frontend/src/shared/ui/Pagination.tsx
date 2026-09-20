import { Button } from '@/components/ui/button'

interface PaginationProps {
  page: number
  totalPages: number
  onPageChange: (page: number) => void
  textoAnterior: string
  textoProxima: string
  textoPagina: string
}

export function Pagination({
  page,
  totalPages,
  onPageChange,
  textoAnterior,
  textoProxima,
  textoPagina,
}: PaginationProps) {
  return (
    <nav aria-label={textoPagina} className="flex items-center gap-4 text-sm text-muted-foreground">
      <Button
        type="button"
        variant="outline"
        disabled={page <= 0}
        onClick={() => onPageChange(page - 1)}
      >
        {textoAnterior}
      </Button>
      <span aria-live="polite">
        {textoPagina} {page + 1} / {Math.max(totalPages, 1)}
      </span>
      <Button
        type="button"
        variant="outline"
        disabled={page + 1 >= totalPages}
        onClick={() => onPageChange(page + 1)}
      >
        {textoProxima}
      </Button>
    </nav>
  )
}
