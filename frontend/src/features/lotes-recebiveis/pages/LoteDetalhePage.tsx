import { Link, useParams } from 'react-router-dom'
import { LoteRecebiveisDetalhe } from '../components/LoteRecebiveisDetalhe'
import { TEXTOS } from '../constants/textos'

export function LoteDetalhePage() {
  const { id } = useParams<{ id: string }>()

  return (
    <main>
      <p>
        <Link to="/">{TEXTOS.detalhe.voltar}</Link>
      </p>
      {id && <LoteRecebiveisDetalhe id={id} />}
    </main>
  )
}
