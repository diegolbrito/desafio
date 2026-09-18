/**
 * Formatacao so na camada de exibicao (ver SPEC.md, "Arquitetura Frontend"):
 * o valor em estado/rede permanece string, a formatacao acontece so aqui.
 */

export function formatarMoeda(valor: string | undefined, moeda: 'BRL' | 'USD' | undefined): string {
  if (valor === undefined || moeda === undefined) return '—'
  const numero = Number(valor)
  if (Number.isNaN(numero)) return valor
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: moeda }).format(numero)
}

export function formatarPercentual(valor: string | undefined): string {
  if (valor === undefined) return '—'
  const numero = Number(valor)
  if (Number.isNaN(numero)) return valor
  return new Intl.NumberFormat('pt-BR', {
    style: 'percent',
    minimumFractionDigits: 2,
    maximumFractionDigits: 4,
  }).format(numero)
}

export function formatarData(data: string | undefined): string {
  if (!data) return '—'
  const [ano, mes, dia] = data.split('-')
  if (!ano || !mes || !dia) return data
  return `${dia}/${mes}/${ano}`
}

export function formatarDataHora(dataHoraIso: string | undefined): string {
  if (!dataHoraIso) return '—'
  const data = new Date(dataHoraIso)
  if (Number.isNaN(data.getTime())) return dataHoraIso
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(data)
}
