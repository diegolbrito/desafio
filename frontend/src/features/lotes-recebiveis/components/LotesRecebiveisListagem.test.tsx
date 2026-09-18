import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../../../shared/test/renderWithProviders'
import * as api from '../api/lotesRecebiveisApi'
import { LotesRecebiveisListagem } from './LotesRecebiveisListagem'

vi.mock('../api/lotesRecebiveisApi')

describe('LotesRecebiveisListagem', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('mostra estado de carregamento e depois a lista', async () => {
    vi.mocked(api.listarLotesRecebiveis).mockResolvedValue({
      content: [
        { id: 'lote-1', dataReferencia: '2026-09-18', status: 'PRECIFICADO', createdAt: '2026-09-18T10:00:00Z' },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })

    renderWithProviders(<LotesRecebiveisListagem />)

    expect(screen.getByText('Carregando lotes...')).toBeInTheDocument()

    expect(await screen.findByText('Precificado')).toBeInTheDocument()
    expect(screen.getByText('18/09/2026')).toBeInTheDocument()
  })

  it('mostra estado vazio quando nao ha lotes', async () => {
    vi.mocked(api.listarLotesRecebiveis).mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })

    renderWithProviders(<LotesRecebiveisListagem />)

    expect(await screen.findByText('Nenhum lote encontrado.')).toBeInTheDocument()
  })

  it('mostra mensagem de erro quando a API falha', async () => {
    vi.mocked(api.listarLotesRecebiveis).mockRejectedValue(new Error('falhou'))

    renderWithProviders(<LotesRecebiveisListagem />)

    expect(await screen.findByText('Não foi possível processar a solicitação. Tente novamente.')).toBeInTheDocument()
  })

  it('desabilita o botao "Anterior" na primeira pagina', async () => {
    vi.mocked(api.listarLotesRecebiveis).mockResolvedValue({
      content: [{ id: 'lote-1', dataReferencia: '2026-09-18', status: 'RECEBIDO', createdAt: '2026-09-18T10:00:00Z' }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 2,
    })

    renderWithProviders(<LotesRecebiveisListagem />)

    expect(await screen.findByRole('button', { name: 'Anterior' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Próxima' })).toBeEnabled()
  })
})
