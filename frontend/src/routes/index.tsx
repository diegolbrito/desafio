/* eslint-disable react-refresh/only-export-components -- modulo de configuracao de rotas, nao um componente */
import { lazy, Suspense } from 'react'
import { createBrowserRouter } from 'react-router-dom'
import { RotaErro } from './RotaErro'

/**
 * Rotas declaradas em um unico lugar, com lazy loading por rota/feature
 * (ver SPEC.md, "Arquitetura Frontend").
 */
const LotesRecebiveisPage = lazy(() =>
  import('../features/lotes-recebiveis/pages/LotesRecebiveisPage').then((m) => ({
    default: m.LotesRecebiveisPage,
  })),
)

const LoteDetalhePage = lazy(() =>
  import('../features/lotes-recebiveis/pages/LoteDetalhePage').then((m) => ({
    default: m.LoteDetalhePage,
  })),
)

export const router = createBrowserRouter([
  {
    path: '/',
    errorElement: <RotaErro />,
    element: (
      <Suspense fallback={<p>Carregando...</p>}>
        <LotesRecebiveisPage />
      </Suspense>
    ),
  },
  {
    path: '/lotes-recebiveis/:id',
    errorElement: <RotaErro />,
    element: (
      <Suspense fallback={<p>Carregando...</p>}>
        <LoteDetalhePage />
      </Suspense>
    ),
  },
])
