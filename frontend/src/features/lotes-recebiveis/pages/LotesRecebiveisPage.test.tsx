import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { LotesRecebiveisPage } from './LotesRecebiveisPage'

describe('LotesRecebiveisPage', () => {
  it('renderiza o titulo da aplicacao', () => {
    render(<LotesRecebiveisPage />)

    expect(screen.getByRole('heading', { name: 'SRM Credit Engine' })).toBeInTheDocument()
  })
})
