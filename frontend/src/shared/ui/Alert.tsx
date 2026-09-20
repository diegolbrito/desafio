import type { ReactNode } from 'react'
import { Alert as AlertRoot, AlertDescription } from '@/components/ui/alert'
import { cn } from '@/lib/utils'

interface AlertProps {
  variant: 'error' | 'success' | 'info'
  children: ReactNode
}

const CLASSES_POR_VARIANTE: Record<AlertProps['variant'], string> = {
  error: 'border-destructive/50 bg-destructive/5',
  success: 'border-emerald-500/50 bg-emerald-50 text-emerald-900 [&_[data-slot=alert-description]]:text-emerald-800',
  info: 'border-border bg-muted/50',
}

/**
 * Sobrescreve o role do componente base (sempre "alert") para "status" fora
 * do caso de erro: "alert" interrompe o leitor de tela, o que so faz sentido
 * para mensagens de erro - sucesso/info devem ser anunciados sem interromper.
 */
export function Alert({ variant, children }: AlertProps) {
  return (
    <AlertRoot
      role={variant === 'error' ? 'alert' : 'status'}
      variant={variant === 'error' ? 'destructive' : 'default'}
      className={cn(CLASSES_POR_VARIANTE[variant])}
    >
      <AlertDescription>{children}</AlertDescription>
    </AlertRoot>
  )
}
