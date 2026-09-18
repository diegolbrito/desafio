import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { listarLotesRecebiveis } from '../api/lotesRecebiveisApi'

const TAMANHO_PAGINA_PADRAO = 20

export function useLotesRecebiveis(page: number, size: number = TAMANHO_PAGINA_PADRAO) {
  return useQuery({
    queryKey: ['lotes-recebiveis', 'lista', page, size],
    queryFn: () => listarLotesRecebiveis(page, size),
    placeholderData: keepPreviousData,
  })
}
