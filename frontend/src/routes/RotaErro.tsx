import { isRouteErrorResponse, useRouteError } from 'react-router-dom'

/** Error boundary de rota para falhas inesperadas (ver SPEC.md, "Tratamento de erros e estados"). */
export function RotaErro() {
  const erro = useRouteError()
  const mensagem = isRouteErrorResponse(erro)
    ? `${erro.status} ${erro.statusText}`
    : 'Ocorreu um erro inesperado.'

  return (
    <main className="mx-auto max-w-5xl space-y-2 px-4 py-8">
      <h1 className="text-2xl font-semibold text-foreground">Algo deu errado</h1>
      <p className="text-muted-foreground">{mensagem}</p>
    </main>
  )
}
