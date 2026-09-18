import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Alert } from '../../../shared/ui/Alert'
import { Pagination } from '../../../shared/ui/Pagination'
import { TEXTOS } from '../constants/textos'
import { useLotesRecebiveis } from '../hooks/useLotesRecebiveis'
import { extrairMensagemErro } from '../utils/extrairMensagemErro'
import { formatarData, formatarDataHora } from '../utils/formatters'
import { StatusBadge } from './StatusBadge'

export function LotesRecebiveisListagem() {
  const [page, setPage] = useState(0)
  const { data, isLoading, isError, error } = useLotesRecebiveis(page)

  return (
    <section aria-labelledby="titulo-listagem-lotes">
      <h2 id="titulo-listagem-lotes">{TEXTOS.tabela.tituloSecao}</h2>

      {isLoading && <p>{TEXTOS.tabela.carregando}</p>}

      {isError && <Alert variant="error">{extrairMensagemErro(error)}</Alert>}

      {!isLoading && !isError && data && data.content?.length === 0 && <p>{TEXTOS.tabela.vazio}</p>}

      {!isLoading && !isError && data && (data.content?.length ?? 0) > 0 && (
        <>
          <table>
            <thead>
              <tr>
                <th scope="col">{TEXTOS.tabela.colunaDataReferencia}</th>
                <th scope="col">{TEXTOS.tabela.colunaStatus}</th>
                <th scope="col">{TEXTOS.tabela.colunaCriadoEm}</th>
                <th scope="col">{TEXTOS.tabela.colunaAcoes}</th>
              </tr>
            </thead>
            <tbody>
              {data.content?.map((lote) => (
                <tr key={lote.id}>
                  <td>{formatarData(lote.dataReferencia)}</td>
                  <td>
                    <StatusBadge status={lote.status} />
                  </td>
                  <td>{formatarDataHora(lote.createdAt)}</td>
                  <td>
                    <Link to={`/lotes-recebiveis/${lote.id}`}>{TEXTOS.tabela.verDetalhe}</Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          <Pagination
            page={data.page ?? 0}
            totalPages={data.totalPages ?? 1}
            onPageChange={setPage}
            textoAnterior={TEXTOS.tabela.anterior}
            textoProxima={TEXTOS.tabela.proxima}
            textoPagina={TEXTOS.tabela.pagina}
          />
        </>
      )}
    </section>
  )
}
