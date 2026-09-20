import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../../../shared/test/renderWithProviders'
import * as api from '../api/lotesRecebiveisApi'
import { LoteRecebiveisDetalhe } from './LoteRecebiveisDetalhe'

vi.mock('../api/lotesRecebiveisApi')

const RECEBIVEL_PRECIFICADO = {
  id: 'rec-1',
  ativo: 'Empresa Alfa',
  valorBruto: '1000.00',
  moeda: 'BRL' as const,
  dataVencimento: '2026-12-31',
  categoriaRisco: 'B' as const,
  status: 'PRECIFICADO' as const,
  valorPresente: '950.00',
  valorDesagio: '50.00',
  taxaDescontoAplicada: '0.105000',
}

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
          ativo: 'Empresa Alfa',
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

  it('mostra o valor presente na moeda de pagamento quando cross-currency', async () => {
    vi.mocked(api.buscarLoteRecebiveis).mockResolvedValue({
      id: 'lote-1',
      dataReferencia: '2026-09-18',
      status: 'PRECIFICADO',
      recebiveis: [
        {
          id: 'rec-1',
          ativo: 'Empresa Gama',
          valorBruto: '1000.00',
          moeda: 'BRL',
          dataVencimento: '2026-12-31',
          categoriaRisco: 'B',
          status: 'PRECIFICADO',
          valorPresente: '190.00',
          valorDesagio: '50.00',
          taxaDescontoAplicada: '0.105000',
          moedaPagamento: 'USD',
          cotacaoCambio: '5.00',
        },
      ],
    })

    renderWithProviders(<LoteRecebiveisDetalhe id="lote-1" />)

    expect(await screen.findByText('Empresa Gama')).toBeInTheDocument()
    // valorBruto/desagio continuam em BRL, valorPresente na moeda de pagamento (USD)
    expect(screen.getByText(/R\$\s*1\.000,00/)).toBeInTheDocument()
    expect(screen.getByText(/US\$\s*190,00/)).toBeInTheDocument()
    expect(screen.getByText(/R\$\s*50,00/)).toBeInTheDocument()
    expect(screen.getByText('Dólar (USD)')).toBeInTheDocument()
  })

  it('mostra o motivo quando o recebivel foi rejeitado', async () => {
    vi.mocked(api.buscarLoteRecebiveis).mockResolvedValue({
      id: 'lote-1',
      dataReferencia: '2026-09-18',
      status: 'PRECIFICADO',
      recebiveis: [
        {
          id: 'rec-1',
          ativo: 'Empresa Beta',
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

  it('permite liquidar um recebivel precificado e atualiza o status apos sucesso', async () => {
    vi.mocked(api.buscarLoteRecebiveis).mockResolvedValue({
      id: 'lote-1',
      dataReferencia: '2026-09-18',
      status: 'PRECIFICADO',
      recebiveis: [RECEBIVEL_PRECIFICADO],
    })
    vi.mocked(api.liquidarRecebivel).mockResolvedValue({
      ...RECEBIVEL_PRECIFICADO,
      status: 'LIQUIDADO',
      liquidadoEm: '2026-09-20T12:00:00Z',
    })

    renderWithProviders(<LoteRecebiveisDetalhe id="lote-1" />)
    const usuario = userEvent.setup()

    const botaoLiquidar = await screen.findByRole('button', { name: 'Liquidar' })
    await usuario.click(botaoLiquidar)

    await waitFor(() => expect(api.liquidarRecebivel).toHaveBeenCalledWith('lote-1', 'rec-1'))
  })

  it('nao mostra o botao de liquidar para recebiveis que nao estao precificados', async () => {
    vi.mocked(api.buscarLoteRecebiveis).mockResolvedValue({
      id: 'lote-1',
      dataReferencia: '2026-09-18',
      status: 'PRECIFICADO',
      recebiveis: [
        {
          ...RECEBIVEL_PRECIFICADO,
          status: 'REJEITADO',
          motivoRejeicao: 'Data de vencimento invalida',
        },
      ],
    })

    renderWithProviders(<LoteRecebiveisDetalhe id="lote-1" />)

    await screen.findByText('Empresa Alfa')
    expect(screen.queryByRole('button', { name: 'Liquidar' })).not.toBeInTheDocument()
  })

  it('mostra mensagem de erro quando a liquidacao falha', async () => {
    vi.mocked(api.buscarLoteRecebiveis).mockResolvedValue({
      id: 'lote-1',
      dataReferencia: '2026-09-18',
      status: 'PRECIFICADO',
      recebiveis: [RECEBIVEL_PRECIFICADO],
    })
    vi.mocked(api.liquidarRecebivel).mockRejectedValue(new Error('falhou'))

    renderWithProviders(<LoteRecebiveisDetalhe id="lote-1" />)
    const usuario = userEvent.setup()

    const botaoLiquidar = await screen.findByRole('button', { name: 'Liquidar' })
    await usuario.click(botaoLiquidar)

    expect(
      await screen.findByText('Não foi possível liquidar o recebível. Tente novamente.'),
    ).toBeInTheDocument()
  })
})
