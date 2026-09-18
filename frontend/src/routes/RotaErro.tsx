import { isRouteErrorResponse, useRouteError } from 'react-router-dom'

/** Error boundary de rota para falhas inesperadas (ver SPEC.md, "Tratamento de erros e estados"). */
export function RotaErro() {
  const erro = useRouteError()
  const mensagem = isRouteErrorResponse(erro)
    ? `${erro.status} ${erro.statusText}`
    : 'Ocorreu um erro inesperado.'

  return (
    <main>
      <h1>Algo deu errado</h1>
      <p>{mensagem}</p>
    </main>
  )
}
