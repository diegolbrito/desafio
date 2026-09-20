import { useId } from 'react'
import { Controller, type Control, type FieldValues, type Path } from 'react-hook-form'
import { Label } from '@/components/ui/label'
import {
  Select as SelectRoot,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

interface SelectOption {
  value: string
  label: string
}

interface SelectFieldProps<TFieldValues extends FieldValues> {
  control: Control<TFieldValues>
  name: Path<TFieldValues>
  label: string
  error?: string
  options: SelectOption[]
}

export function Select<TFieldValues extends FieldValues>({
  control,
  name,
  label,
  error,
  options,
}: SelectFieldProps<TFieldValues>) {
  const generatedId = useId()
  const errorId = `${generatedId}-error`

  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <Label htmlFor={generatedId}>{label}</Label>
      <Controller
        control={control}
        name={name}
        render={({ field }) => (
          <SelectRoot value={field.value} onValueChange={field.onChange}>
            <SelectTrigger
              id={generatedId}
              className="w-full"
              aria-invalid={Boolean(error)}
              aria-describedby={error ? errorId : undefined}
              onBlur={field.onBlur}
            >
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {options.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </SelectRoot>
        )}
      />
      {error && (
        <p id={errorId} role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  )
}
