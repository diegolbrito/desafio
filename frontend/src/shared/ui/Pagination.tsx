import { Button } from './Button'
import styles from './Pagination.module.css'

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
    <nav aria-label={textoPagina} className={styles.pagination}>
      <Button
        type="button"
        variant="secondary"
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
        variant="secondary"
        disabled={page + 1 >= totalPages}
        onClick={() => onPageChange(page + 1)}
      >
        {textoProxima}
      </Button>
    </nav>
  )
}
