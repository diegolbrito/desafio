import { z } from 'zod'
import { TEXTOS } from '../constants/textos'

/**
 * Espelha a validacao da API (ver SPEC.md, "Formularios"): mesmos campos
 * obrigatorios, mesma regra de valor positivo. A validacao de "data de
 * vencimento no passado" fica de fora de proposito - e' regra de negocio do
 * backend, que rejeita so o item sem abortar o lote (SPEC.md, Premissa 5).
 *
 * O ativo e' sempre denominado em BRL (ver SPEC.md, "Premissas adotadas"
 * item 3) - por isso nao ha campo de moeda do ativo aqui, so' a moeda de
 * pagamento (`moedaPagamento`), que sinaliza cross-currency quando diferente
 * de BRL.
 */
const recebivelSchema = z.object({
  ativo: z.string().trim().min(1, TEXTOS.erros.ativoObrigatorio),
  valorBruto: z
    .string()
    .trim()
    .min(1, TEXTOS.erros.valorBrutoObrigatorio)
    .regex(/^\d+(\.\d{1,2})?$/, TEXTOS.erros.valorBrutoInvalido)
    .refine((valor) => Number(valor) > 0, TEXTOS.erros.valorBrutoPositivo),
  moedaPagamento: z.enum(['BRL', 'USD']),
  dataVencimento: z.string().min(1, TEXTOS.erros.dataVencimentoObrigatoria),
  categoriaRisco: z.enum(['AA', 'A', 'B', 'C', 'D', 'E']),
})

export const loteRecebiveisFormSchema = z.object({
  recebiveis: z.array(recebivelSchema).min(1, TEXTOS.erros.loteVazio),
})

export type LoteRecebiveisFormValues = z.infer<typeof loteRecebiveisFormSchema>

export function recebivelPadrao(): LoteRecebiveisFormValues['recebiveis'][number] {
  return {
    ativo: '',
    valorBruto: '',
    moedaPagamento: 'BRL',
    dataVencimento: '',
    categoriaRisco: 'B',
  }
}
