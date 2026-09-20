import { useMutation, useQueryClient } from '@tanstack/react-query'
import { liquidarRecebivel } from '../api/lotesRecebiveisApi'

export function useLiquidarRecebivel(loteId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (recebivelId: string) => liquidarRecebivel(loteId, recebivelId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['lotes-recebiveis', 'detalhe', loteId] })
    },
  })
}
