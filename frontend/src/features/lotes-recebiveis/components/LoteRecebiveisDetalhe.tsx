import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Alert } from '../../../shared/ui/Alert'
import { TEXTOS } from '../constants/textos'
import { useLiquidarRecebivel } from '../hooks/useLiquidarRecebivel'
import { useLoteRecebiveis } from '../hooks/useLoteRecebiveis'
import { extrairMensagemErro } from '../utils/extrairMensagemErro'
import { formatarData, formatarDataHora, formatarMoeda, formatarPercentual } from '../utils/formatters'
import { StatusBadge } from './StatusBadge'

interface LoteRecebiveisDetalheProps {
  id: string
}

export function LoteRecebiveisDetalhe({ id }: LoteRecebiveisDetalheProps) {
  const { data: lote, isLoading, isError, error } = useLoteRecebiveis(id)
  const { mutate: liquidar, isPending: liquidando, isError: erroAoLiquidar, variables: recebivelEmLiquidacao } =
    useLiquidarRecebivel(id)

  if (isLoading) {
    return <p className="text-sm text-muted-foreground">{TEXTOS.detalhe.carregando}</p>
  }

  if (isError) {
    return <Alert variant="error">{extrairMensagemErro(error)}</Alert>
  }

  if (!lote) {
    return <Alert variant="info">{TEXTOS.detalhe.naoEncontrado}</Alert>
  }

  return (
    <section aria-labelledby="titulo-detalhe-lote" className="space-y-3">
      <p className="flex items-center gap-2 text-sm text-muted-foreground">
        {TEXTOS.tabela.colunaDataReferencia}: {formatarData(lote.dataReferencia)} —{' '}
        <StatusBadge status={lote.status} />
      </p>

      <h2 id="titulo-detalhe-lote" className="text-lg font-semibold text-foreground">
        {TEXTOS.detalhe.tituloSecao}
      </h2>

      <div className="rounded-xl border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{TEXTOS.detalhe.colunaAtivo}</TableHead>
              <TableHead>{TEXTOS.detalhe.colunaValorBruto}</TableHead>
              <TableHead>{TEXTOS.detalhe.colunaMoedaPagamento}</TableHead>
              <TableHead>{TEXTOS.detalhe.colunaValorPresente}</TableHead>
              <TableHead>{TEXTOS.detalhe.colunaDesagio}</TableHead>
              <TableHead>{TEXTOS.detalhe.colunaTaxa}</TableHead>
              <TableHead>{TEXTOS.detalhe.colunaStatus}</TableHead>
              <TableHead>{TEXTOS.detalhe.colunaMotivo}</TableHead>
              <TableHead>{TEXTOS.detalhe.colunaLiquidadoEm}</TableHead>
              <TableHead>{TEXTOS.detalhe.colunaAcoes}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {lote.recebiveis?.map((recebivel) => (
              <TableRow key={recebivel.id}>
                <TableCell>{recebivel.ativo}</TableCell>
                <TableCell>{formatarMoeda(recebivel.valorBruto, recebivel.moeda)}</TableCell>
                <TableCell>{TEXTOS.moedas[recebivel.moedaPagamento ?? 'BRL']}</TableCell>
                <TableCell>
                  {formatarMoeda(recebivel.valorPresente, recebivel.moedaPagamento ?? recebivel.moeda)}
                </TableCell>
                <TableCell>{formatarMoeda(recebivel.valorDesagio, recebivel.moeda)}</TableCell>
                <TableCell>{formatarPercentual(recebivel.taxaDescontoAplicada)}</TableCell>
                <TableCell>
                  <StatusBadge status={recebivel.status} />
                </TableCell>
                <TableCell>{recebivel.motivoRejeicao ?? '—'}</TableCell>
                <TableCell>{formatarDataHora(recebivel.liquidadoEm)}</TableCell>
                <TableCell>
                  {recebivel.status === 'PRECIFICADO' && recebivel.id && (
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      disabled={liquidando}
                      onClick={() => liquidar(recebivel.id!)}
                    >
                      {liquidando && recebivelEmLiquidacao === recebivel.id
                        ? TEXTOS.detalhe.liquidando
                        : TEXTOS.detalhe.acaoLiquidar}
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      {erroAoLiquidar && <Alert variant="error">{TEXTOS.detalhe.liquidacaoErro}</Alert>}
    </section>
  )
}
