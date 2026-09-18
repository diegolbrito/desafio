import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../../../shared/test/renderWithProviders'
import * as api from '../api/lotesRecebiveisApi'
import { LoteRecebiveisDetalhe } from './LoteRecebiveisDetalhe'

vi.mock('../api/lotesRecebiveisApi')

describe('LoteRecebiveisDetalhe', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('mostra os recebiveis do lote com valores formatados', async () => {
    vi.mocked(api.buscarLoteRecebiveis).mockResolvedValue({
      id: 'lote-1',
      dataReferencia: '2026-09-18',
      status: 'PRECIFICADO',
      recebiveis: [
        {
          id: 'rec-1',
          cedente: 'Empresa Alfa',
          valorBruto: '1000.00',
          moeda: 'BRL',
          dataVencimento: '2026-12-31',
          categoriaRisco: 'B',
          status: 'PRECIFICADO',
          valorPresente: '950.00',
          valorDesagio: '50.00',
          taxaDescontoAplicada: '0.105000',
        },
      ],
    })

    renderWithProviders(<LoteRecebiveisDetalhe id="lote-1" />)

    expect(screen.getByText('Carregando lote...')).toBeInTheDocument()
    expect(await screen.findByText('Empresa Alfa')).toBeInTheDocument()
    // regex em vez de string exata: evita depender do caractere de espaco
    // exato que o Intl.NumberFormat usa entre "R$" e o valor
    expect(screen.getByText(/R\$\s*950,00/)).toBeInTheDocument()
    expect(screen.getByText('10,50%')).toBeInTheDocument()
  })

  it('mostra o motivo quando o recebivel foi rejeitado', async () => {
    vi.mocked(api.buscarLoteRecebiveis).mockResolvedValue({
      id: 'lote-1',
      dataReferencia: '2026-09-18',
      status: 'PRECIFICADO',
      recebiveis: [
        {
          id: 'rec-1',
          cedente: 'Empresa Beta',
          valorBruto: '500.00',
          moeda: 'BRL',
          dataVencimento: '2026-09-18',
          categoriaRisco: 'A',
          status: 'REJEITADO',
          motivoRejeicao: 'Data de vencimento invalida',
        },
      ],
    })

    renderWithProviders(<LoteRecebiveisDetalhe id="lote-1" />)

    expect(await screen.findByText('Data de vencimento invalida')).toBeInTheDocument()
    expect(screen.getByText('Rejeitado')).toBeInTheDocument()
  })

  it('mostra mensagem de erro quando a busca falha', async () => {
    vi.mocked(api.buscarLoteRecebiveis).mockRejectedValue(new Error('falhou'))

    renderWithProviders(<LoteRecebiveisDetalhe id="lote-1" />)

    expect(
      await screen.findByText('Não foi possível processar a solicitação. Tente novamente.'),
    ).toBeInTheDocument()
  })
})
