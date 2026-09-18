import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../../../shared/test/renderWithProviders'
import * as api from '../api/lotesRecebiveisApi'
import { LoteRecebiveisForm } from './LoteRecebiveisForm'

vi.mock('../api/lotesRecebiveisApi')

describe('LoteRecebiveisForm', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('comeca com um recebivel e permite adicionar/remover linhas', async () => {
    const user = userEvent.setup()
    renderWithProviders(<LoteRecebiveisForm />)

    expect(screen.getAllByLabelText('Cedente')).toHaveLength(1)
    expect(screen.queryByRole('button', { name: 'Remover' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '+ Adicionar recebível' }))
    expect(screen.getAllByLabelText('Cedente')).toHaveLength(2)
    expect(screen.getAllByRole('button', { name: 'Remover' })).toHaveLength(2)

    await user.click(screen.getAllByRole('button', { name: 'Remover' })[0])
    expect(screen.getAllByLabelText('Cedente')).toHaveLength(1)
  })

  it('mostra erros de validacao ao submeter com campos obrigatorios vazios', async () => {
    const user = userEvent.setup()
    renderWithProviders(<LoteRecebiveisForm />)

    await user.click(screen.getByRole('button', { name: 'Precificar lote' }))

    expect(await screen.findByText('Cedente é obrigatório')).toBeInTheDocument()
    expect(screen.getByText('Valor bruto é obrigatório')).toBeInTheDocument()
    expect(screen.getByText('Data de vencimento é obrigatória')).toBeInTheDocument()
    expect(api.criarLoteRecebiveis).not.toHaveBeenCalled()
  })

  it('rejeita valor bruto nao positivo', async () => {
    const user = userEvent.setup()
    renderWithProviders(<LoteRecebiveisForm />)

    await user.type(screen.getByLabelText('Cedente'), 'Empresa Teste')
    await user.type(screen.getByLabelText('Valor bruto'), '-10')
    await user.type(screen.getByLabelText('Data de vencimento'), '2027-01-01')
    await user.click(screen.getByRole('button', { name: 'Precificar lote' }))

    expect(
      await screen.findByText('Informe um número válido, com ponto para casas decimais (ex.: 1000.50)'),
    ).toBeInTheDocument()
    expect(api.criarLoteRecebiveis).not.toHaveBeenCalled()
  })

  it('envia o lote com dados validos e mostra mensagem de sucesso', async () => {
    vi.mocked(api.criarLoteRecebiveis).mockResolvedValue({
      id: 'lote-1',
      dataReferencia: '2026-09-18',
      status: 'PRECIFICADO',
      recebiveis: [],
    })
    const user = userEvent.setup()
    renderWithProviders(<LoteRecebiveisForm />)

    await user.type(screen.getByLabelText('Cedente'), 'Empresa Teste')
    await user.type(screen.getByLabelText('Valor bruto'), '1000.50')
    await user.type(screen.getByLabelText('Data de vencimento'), '2027-01-01')
    await user.click(screen.getByRole('button', { name: 'Precificar lote' }))

    // checa so o 1o argumento da chamada: o TanStack Query v5 passa um 2o
    // argumento de contexto interno (client/meta/mutationKey) para a mutationFn
    await waitFor(() => {
      expect(api.criarLoteRecebiveis).toHaveBeenCalled()
    })
    expect(vi.mocked(api.criarLoteRecebiveis).mock.calls[0][0]).toEqual({
      recebiveis: [
        {
          cedente: 'Empresa Teste',
          valorBruto: '1000.50',
          moeda: 'BRL',
          dataVencimento: '2027-01-01',
          categoriaRisco: 'B',
        },
      ],
    })

    expect(await screen.findByText('Lote registrado com sucesso.')).toBeInTheDocument()
  })

  it('mostra mensagem de erro quando a API rejeita', async () => {
    vi.mocked(api.criarLoteRecebiveis).mockRejectedValue(new Error('falhou'))
    const user = userEvent.setup()
    renderWithProviders(<LoteRecebiveisForm />)

    await user.type(screen.getByLabelText('Cedente'), 'Empresa Teste')
    await user.type(screen.getByLabelText('Valor bruto'), '1000')
    await user.type(screen.getByLabelText('Data de vencimento'), '2027-01-01')
    await user.click(screen.getByRole('button', { name: 'Precificar lote' }))

    expect(
      await screen.findByText('Não foi possível processar a solicitação. Tente novamente.'),
    ).toBeInTheDocument()
  })
})
