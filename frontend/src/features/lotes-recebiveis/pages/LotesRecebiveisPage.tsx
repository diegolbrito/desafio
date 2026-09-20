import { LoteRecebiveisForm } from '../components/LoteRecebiveisForm'
import { LotesRecebiveisListagem } from '../components/LotesRecebiveisListagem'
import { TEXTOS } from '../constants/textos'

export function LotesRecebiveisPage() {
  return (
    <main className="mx-auto max-w-5xl space-y-8 px-4 py-8">
      <div>
        <h1 className="text-2xl font-semibold text-foreground">{TEXTOS.app.titulo}</h1>
        <p className="text-muted-foreground">{TEXTOS.app.subtitulo}</p>
      </div>

      <LoteRecebiveisForm />
      <LotesRecebiveisListagem />
    </main>
  )
}
