import type { ReactNode } from 'react'
import styles from './Alert.module.css'

interface AlertProps {
  variant: 'error' | 'success' | 'info'
  children: ReactNode
}

/** role="alert" força leitores de tela a anunciar a mensagem imediatamente (erro/sucesso). */
export function Alert({ variant, children }: AlertProps) {
  const classes = [styles.alert, styles[variant]].join(' ')
  return (
    <div className={classes} role={variant === 'error' ? 'alert' : 'status'}>
      {children}
    </div>
  )
}
