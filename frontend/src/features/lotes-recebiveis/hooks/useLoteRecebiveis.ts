import { useQuery } from '@tanstack/react-query'
import { buscarLoteRecebiveis } from '../api/lotesRecebiveisApi'

export function useLoteRecebiveis(id: string | undefined) {
  return useQuery({
    queryKey: ['lotes-recebiveis', 'detalhe', id],
    queryFn: () => buscarLoteRecebiveis(id!),
    enabled: Boolean(id),
  })
}
