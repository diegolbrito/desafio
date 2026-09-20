import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useFieldArray, useForm } from 'react-hook-form'
import { Alert } from '../../../shared/ui/Alert'
import { Button } from '../../../shared/ui/Button'
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
import styles from './LoteRecebiveisForm.module.css'

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
    <form onSubmit={handleSubmit(onSubmit)} aria-labelledby="titulo-novo-lote" noValidate>
      <h2 id="titulo-novo-lote">{TEXTOS.form.tituloSecao}</h2>
      <p>{TEXTOS.form.subtituloSecao}</p>

      {fields.map((field, index) => (
        <fieldset key={field.id} className={styles.recebivel}>
          <legend>
            {TEXTOS.form.legendaRecebivel} {index + 1}
          </legend>

          <div className={styles.grid}>
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
              label={TEXTOS.form.moedaPagamento}
              error={errors.recebiveis?.[index]?.moedaPagamento?.message}
              {...register(`recebiveis.${index}.moedaPagamento`)}
            >
              <option value="BRL">{TEXTOS.moedas.BRL}</option>
              <option value="USD">{TEXTOS.moedas.USD}</option>
            </Select>
            <TextField
              label={TEXTOS.form.dataVencimento}
              type="date"
              error={errors.recebiveis?.[index]?.dataVencimento?.message}
              {...register(`recebiveis.${index}.dataVencimento`)}
            />
            <Select
              label={TEXTOS.form.categoriaRisco}
              error={errors.recebiveis?.[index]?.categoriaRisco?.message}
              {...register(`recebiveis.${index}.categoriaRisco`)}
            >
              {Object.entries(TEXTOS.categoriasRisco).map(([valor, rotulo]) => (
                <option key={valor} value={valor}>
                  {rotulo}
                </option>
              ))}
            </Select>
          </div>

          {fields.length > 1 && (
            <Button type="button" variant="danger" onClick={() => remove(index)}>
              {TEXTOS.form.removerRecebivel}
            </Button>
          )}
        </fieldset>
      ))}

      {erroDoLote && <Alert variant="error">{erroDoLote}</Alert>}

      <div className={styles.acoes}>
        <Button type="button" variant="secondary" onClick={() => append(recebivelPadrao())}>
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
