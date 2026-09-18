import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../../../shared/test/renderWithProviders'
import * as api from '../api/lotesRecebiveisApi'
import { LotesRecebiveisPage } from './LotesRecebiveisPage'

vi.mock('../api/lotesRecebiveisApi')

describe('LotesRecebiveisPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listarLotesRecebiveis).mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })
  })

  it('renderiza o titulo, o formulario e a listagem', async () => {
    renderWithProviders(<LotesRecebiveisPage />)

    expect(screen.getByRole('heading', { name: 'SRM Credit Engine', level: 1 })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Novo lote de recebíveis' })).toBeInTheDocument()
    expect(await screen.findByText('Nenhum lote encontrado.')).toBeInTheDocument()
  })
})
