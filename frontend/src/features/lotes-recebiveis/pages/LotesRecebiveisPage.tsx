import { LoteRecebiveisForm } from '../components/LoteRecebiveisForm'
import { LotesRecebiveisListagem } from '../components/LotesRecebiveisListagem'
import { TEXTOS } from '../constants/textos'

export function LotesRecebiveisPage() {
  return (
    <main>
      <h1>{TEXTOS.app.titulo}</h1>
      <p>{TEXTOS.app.subtitulo}</p>

      <LoteRecebiveisForm />
      <LotesRecebiveisListagem />
    </main>
  )
}
