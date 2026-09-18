import { TEXTOS } from '../constants/textos'
import styles from './StatusBadge.module.css'

type Status = keyof typeof TEXTOS.status

const TOM_POR_STATUS: Record<Status, 'sucesso' | 'erro' | 'neutro'> = {
  RECEBIDO: 'neutro',
  PRECIFICADO: 'sucesso',
  ERRO: 'erro',
  PENDENTE: 'neutro',
  REJEITADO: 'erro',
}

interface StatusBadgeProps {
  status: Status | string | undefined
}

export function StatusBadge({ status }: StatusBadgeProps) {
  if (!status || !(status in TEXTOS.status)) {
    return <span className={styles.badge}>{status ?? '—'}</span>
  }

  const statusConhecido = status as Status
  const tom = TOM_POR_STATUS[statusConhecido]
  return <span className={`${styles.badge} ${styles[tom]}`}>{TEXTOS.status[statusConhecido]}</span>
}
