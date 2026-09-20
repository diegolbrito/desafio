import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import { TEXTOS } from '../constants/textos'

type Status = keyof typeof TEXTOS.status

const CLASSES_POR_STATUS: Record<Status, string> = {
  RECEBIDO: '',
  PRECIFICADO: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900 dark:text-emerald-200',
  ERRO: '',
  PENDENTE: '',
  REJEITADO: '',
}

interface StatusBadgeProps {
  status: Status | string | undefined
}

export function StatusBadge({ status }: StatusBadgeProps) {
  if (!status || !(status in TEXTOS.status)) {
    return <Badge variant="secondary">{status ?? '—'}</Badge>
  }

  const statusConhecido = status as Status
  const variant = statusConhecido === 'ERRO' || statusConhecido === 'REJEITADO' ? 'destructive' : 'secondary'

  return (
    <Badge variant={variant} className={cn(CLASSES_POR_STATUS[statusConhecido])}>
      {TEXTOS.status[statusConhecido]}
    </Badge>
  )
}
