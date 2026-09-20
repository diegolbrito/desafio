import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useFieldArray, useForm } from 'react-hook-form'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Alert } from '../../../shared/ui/Alert'
import { Select } from '../../../shared/ui/Select'
import { TextField } from '../../../shared/ui/TextField'
import { TEXTOS } from '../constants/textos'
import { useCriarLoteRecebiveis } from '../hooks/useCriarLoteRecebiveis'
import { extrairMensagemErro } from '../utils/extrairMensagemErro'
import {
  loteRecebiveisFormSchema,
  recebivelPadrao,
  type LoteRecebiveisFormValues,
} from './loteRecebiveisFormSchema'

const OPCOES_MOEDA = Object.entries(TEXTOS.moedas).map(([valor, rotulo]) => ({ value: valor, label: rotulo }))
const OPCOES_CATEGORIA_RISCO = Object.entries(TEXTOS.categoriasRisco).map(([valor, rotulo]) => ({
  value: valor,
  label: rotulo,
}))

export function LoteRecebiveisForm() {
  const [mensagemSucesso, setMensagemSucesso] = useState(false)
  const { mutate, isPending, isError, error } = useCriarLoteRecebiveis()

  const {
    register,
    control,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<LoteRecebiveisFormValues>({
    resolver: zodResolver(loteRecebiveisFormSchema),
    defaultValues: { recebiveis: [recebivelPadrao()] },
  })

  const { fields, append, remove } = useFieldArray({ control, name: 'recebiveis' })

  function onSubmit(valores: LoteRecebiveisFormValues) {
    setMensagemSucesso(false)
    mutate(valores, {
      onSuccess: () => {
        reset({ recebiveis: [recebivelPadrao()] })
        setMensagemSucesso(true)
      },
    })
  }

  const erroDoLote = errors.recebiveis?.root?.message ?? errors.recebiveis?.message

  return (
    <form onSubmit={handleSubmit(onSubmit)} aria-labelledby="titulo-novo-lote" noValidate className="space-y-4">
      <div>
        <h2 id="titulo-novo-lote" className="text-lg font-semibold text-foreground">
          {TEXTOS.form.tituloSecao}
        </h2>
        <p className="text-sm text-muted-foreground">{TEXTOS.form.subtituloSecao}</p>
      </div>

      <div className="space-y-3">
        {fields.map((field, index) => (
          <Card key={field.id}>
            <CardHeader>
              <CardTitle>
                {TEXTOS.form.legendaRecebivel} {index + 1}
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5">
                <TextField
                  label={TEXTOS.form.ativo}
                  error={errors.recebiveis?.[index]?.ativo?.message}
                  {...register(`recebiveis.${index}.ativo`)}
                />
                <TextField
                  label={TEXTOS.form.valorBruto}
                  inputMode="decimal"
                  placeholder={TEXTOS.form.valorBrutoPlaceholder}
                  error={errors.recebiveis?.[index]?.valorBruto?.message}
                  {...register(`recebiveis.${index}.valorBruto`)}
                />
                <Select
                  control={control}
                  name={`recebiveis.${index}.moedaPagamento`}
                  label={TEXTOS.form.moedaPagamento}
                  error={errors.recebiveis?.[index]?.moedaPagamento?.message}
                  options={OPCOES_MOEDA}
                />
                <TextField
                  label={TEXTOS.form.dataVencimento}
                  type="date"
                  error={errors.recebiveis?.[index]?.dataVencimento?.message}
                  {...register(`recebiveis.${index}.dataVencimento`)}
                />
                <Select
                  control={control}
                  name={`recebiveis.${index}.categoriaRisco`}
                  label={TEXTOS.form.categoriaRisco}
                  error={errors.recebiveis?.[index]?.categoriaRisco?.message}
                  options={OPCOES_CATEGORIA_RISCO}
                />
              </div>

              {fields.length > 1 && (
                <Button type="button" variant="destructive" onClick={() => remove(index)}>
                  {TEXTOS.form.removerRecebivel}
                </Button>
              )}
            </CardContent>
          </Card>
        ))}
      </div>

      {erroDoLote && <Alert variant="error">{erroDoLote}</Alert>}

      <div className="flex items-center gap-3">
        <Button type="button" variant="outline" onClick={() => append(recebivelPadrao())}>
          {TEXTOS.form.adicionarRecebivel}
        </Button>
        <Button type="submit" disabled={isPending}>
          {isPending ? TEXTOS.form.enviando : TEXTOS.form.enviar}
        </Button>
      </div>

      {isError && <Alert variant="error">{extrairMensagemErro(error)}</Alert>}
      {mensagemSucesso && <Alert variant="success">{TEXTOS.form.sucesso}</Alert>}
    </form>
  )
}
