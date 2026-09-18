import { describe, expect, it } from 'vitest'
import { formatarData, formatarDataHora, formatarMoeda, formatarPercentual } from './formatters'

describe('formatarMoeda', () => {
  it('formata valor em BRL', () => {
    expect(formatarMoeda('1000.00', 'BRL')).toBe('R$ 1.000,00')
  })

  it('formata valor em USD (locale pt-BR mantem separadores pt-BR, so muda o simbolo)', () => {
    expect(formatarMoeda('1000.00', 'USD')).toBe('US$ 1.000,00')
  })

  it('retorna travessao quando valor ou moeda estao ausentes', () => {
    expect(formatarMoeda(undefined, 'BRL')).toBe('—')
    expect(formatarMoeda('1000.00', undefined)).toBe('—')
  })
})

describe('formatarPercentual', () => {
  it('formata fracao decimal como percentual', () => {
    expect(formatarPercentual('0.1465')).toBe('14,65%')
  })

  it('retorna travessao quando valor esta ausente', () => {
    expect(formatarPercentual(undefined)).toBe('—')
  })
})

describe('formatarData', () => {
  it('converte data ISO para pt-BR', () => {
    expect(formatarData('2026-12-31')).toBe('31/12/2026')
  })

  it('retorna travessao quando data esta ausente', () => {
    expect(formatarData(undefined)).toBe('—')
  })
})

describe('formatarDataHora', () => {
  it('retorna travessao quando data-hora esta ausente', () => {
    expect(formatarDataHora(undefined)).toBe('—')
  })

  it('formata um ISO datetime valido sem lancar erro', () => {
    expect(formatarDataHora('2026-09-18T15:16:42.527245Z')).not.toBe('—')
  })
})
