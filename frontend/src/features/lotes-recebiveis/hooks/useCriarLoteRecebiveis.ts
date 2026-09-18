import { useMutation, useQueryClient } from '@tanstack/react-query'
import { criarLoteRecebiveis } from '../api/lotesRecebiveisApi'

export function useCriarLoteRecebiveis() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: criarLoteRecebiveis,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['lotes-recebiveis', 'lista'] })
    },
  })
}
