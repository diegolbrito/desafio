import { Link, useParams } from 'react-router-dom'
import { LoteRecebiveisDetalhe } from '../components/LoteRecebiveisDetalhe'
import { TEXTOS } from '../constants/textos'

export function LoteDetalhePage() {
  const { id } = useParams<{ id: string }>()

  return (
    <main className="mx-auto max-w-5xl space-y-4 px-4 py-8">
      <Link to="/" className="text-sm text-primary underline-offset-4 hover:underline">
        {TEXTOS.detalhe.voltar}
      </Link>
      {id && <LoteRecebiveisDetalhe id={id} />}
    </main>
  )
}
