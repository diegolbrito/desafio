import { ApiError } from '../../../shared/api/httpClient'
import { TEXTOS } from '../constants/textos'

export function extrairMensagemErro(erro: unknown): string {
  if (erro instanceof ApiError) {
    return erro.problemDetail?.detail ?? TEXTOS.erros.erroGenerico
  }
  return TEXTOS.erros.erroGenerico
}
