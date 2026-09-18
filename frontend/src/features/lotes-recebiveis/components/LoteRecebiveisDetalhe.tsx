import { Alert } from '../../../shared/ui/Alert'
import { TEXTOS } from '../constants/textos'
import { useLoteRecebiveis } from '../hooks/useLoteRecebiveis'
import { extrairMensagemErro } from '../utils/extrairMensagemErro'
import { formatarData, formatarMoeda, formatarPercentual } from '../utils/formatters'
import { StatusBadge } from './StatusBadge'

interface LoteRecebiveisDetalheProps {
  id: string
}

export function LoteRecebiveisDetalhe({ id }: LoteRecebiveisDetalheProps) {
  const { data: lote, isLoading, isError, error } = useLoteRecebiveis(id)

  if (isLoading) {
    return <p>{TEXTOS.detalhe.carregando}</p>
  }

  if (isError) {
    return <Alert variant="error">{extrairMensagemErro(error)}</Alert>
  }

  if (!lote) {
    return <Alert variant="info">{TEXTOS.detalhe.naoEncontrado}</Alert>
  }

  return (
    <section aria-labelledby="titulo-detalhe-lote">
      <p>
        {TEXTOS.tabela.colunaDataReferencia}: {formatarData(lote.dataReferencia)} —{' '}
        <StatusBadge status={lote.status} />
      </p>

      <h2 id="titulo-detalhe-lote">{TEXTOS.detalhe.tituloSecao}</h2>

      <table>
        <thead>
          <tr>
            <th scope="col">{TEXTOS.detalhe.colunaCedente}</th>
            <th scope="col">{TEXTOS.detalhe.colunaValorBruto}</th>
            <th scope="col">{TEXTOS.detalhe.colunaValorPresente}</th>
            <th scope="col">{TEXTOS.detalhe.colunaDesagio}</th>
            <th scope="col">{TEXTOS.detalhe.colunaTaxa}</th>
            <th scope="col">{TEXTOS.detalhe.colunaStatus}</th>
            <th scope="col">{TEXTOS.detalhe.colunaMotivo}</th>
          </tr>
        </thead>
        <tbody>
          {lote.recebiveis?.map((recebivel) => (
            <tr key={recebivel.id}>
              <td>{recebivel.cedente}</td>
              <td>{formatarMoeda(recebivel.valorBruto, recebivel.moeda)}</td>
              <td>{formatarMoeda(recebivel.valorPresente, recebivel.moeda)}</td>
              <td>{formatarMoeda(recebivel.valorDesagio, recebivel.moeda)}</td>
              <td>{formatarPercentual(recebivel.taxaDescontoAplicada)}</td>
              <td>
                <StatusBadge status={recebivel.status} />
              </td>
              <td>{recebivel.motivoRejeicao ?? '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}
