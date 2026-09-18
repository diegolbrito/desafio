import { httpClient } from '../../../shared/api/httpClient'
import type { components } from '../../../shared/api/schema'

export type LoteRecebiveisRequest = components['schemas']['LoteRecebiveisRequest']
export type RecebivelRequest = components['schemas']['RecebivelRequest']
export type LoteRecebiveisResponse = components['schemas']['LoteRecebiveisResponse']
export type RecebivelResponse = components['schemas']['RecebivelResponse']
export type LoteRecebiveisResumoResponse = components['schemas']['LoteRecebiveisResumoResponse']
export type PaginaLotesResumo = components['schemas']['PaginaResponseLoteRecebiveisResumoResponse']

/**
 * Unico ponto de acesso a rede para o recurso lotes-recebiveis (ver SPEC.md:
 * "Nenhum componente chama fetch/axios diretamente"). Hooks (TanStack Query)
 * chamam estas funcoes; componentes chamam os hooks.
 */
export function criarLoteRecebiveis(payload: LoteRecebiveisRequest): Promise<LoteRecebiveisResponse> {
  return httpClient<LoteRecebiveisResponse>('/api/v1/lotes-recebiveis', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function listarLotesRecebiveis(page: number, size: number): Promise<PaginaLotesResumo> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  return httpClient<PaginaLotesResumo>(`/api/v1/lotes-recebiveis?${params.toString()}`)
}

export function buscarLoteRecebiveis(id: string): Promise<LoteRecebiveisResponse> {
  return httpClient<LoteRecebiveisResponse>(`/api/v1/lotes-recebiveis/${id}`)
}
