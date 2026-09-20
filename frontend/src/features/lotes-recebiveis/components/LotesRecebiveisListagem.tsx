import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
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
    <section aria-labelledby="titulo-listagem-lotes" className="space-y-3">
      <h2 id="titulo-listagem-lotes" className="text-lg font-semibold text-foreground">
        {TEXTOS.tabela.tituloSecao}
      </h2>

      {isLoading && <p className="text-sm text-muted-foreground">{TEXTOS.tabela.carregando}</p>}

      {isError && <Alert variant="error">{extrairMensagemErro(error)}</Alert>}

      {!isLoading && !isError && data && data.content?.length === 0 && (
        <p className="text-sm text-muted-foreground">{TEXTOS.tabela.vazio}</p>
      )}

      {!isLoading && !isError && data && (data.content?.length ?? 0) > 0 && (
        <>
          <div className="rounded-xl border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{TEXTOS.tabela.colunaDataReferencia}</TableHead>
                  <TableHead>{TEXTOS.tabela.colunaStatus}</TableHead>
                  <TableHead>{TEXTOS.tabela.colunaCriadoEm}</TableHead>
                  <TableHead>{TEXTOS.tabela.colunaAcoes}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.content?.map((lote) => (
                  <TableRow key={lote.id}>
                    <TableCell>{formatarData(lote.dataReferencia)}</TableCell>
                    <TableCell>
                      <StatusBadge status={lote.status} />
                    </TableCell>
                    <TableCell>{formatarDataHora(lote.createdAt)}</TableCell>
                    <TableCell>
                      <Link to={`/lotes-recebiveis/${lote.id}`} className="text-primary underline-offset-4 hover:underline">
                        {TEXTOS.tabela.verDetalhe}
                      </Link>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

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
