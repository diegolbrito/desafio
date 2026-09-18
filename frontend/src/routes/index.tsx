/* eslint-disable react-refresh/only-export-components -- modulo de configuracao de rotas, nao um componente */
import { lazy, Suspense } from 'react'
import { createBrowserRouter } from 'react-router-dom'

/**
 * Rotas declaradas em um unico lugar, com lazy loading por rota/feature
 * (ver SPEC.md, "Arquitetura Frontend").
 */
const LotesRecebiveisPage = lazy(() =>
  import('../features/lotes-recebiveis/pages/LotesRecebiveisPage').then((m) => ({
    default: m.LotesRecebiveisPage,
  })),
)

export const router = createBrowserRouter([
  {
    path: '/',
    element: (
      <Suspense fallback={<p>Carregando...</p>}>
        <LotesRecebiveisPage />
      </Suspense>
    ),
  },
])
