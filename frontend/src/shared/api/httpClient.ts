/**
 * Client HTTP isolado: nenhum componente deve chamar fetch/axios diretamente
 * (ver SPEC.md, "Arquitetura Frontend"). Servicos tipados por recurso (ex.:
 * features/lotes-recebiveis/api) usam esta funcao como unico ponto de acesso
 * a rede.
 */

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  errors?: { field: string; message: string }[]
}

export class ApiError extends Error {
  readonly status: number
  readonly problemDetail: ProblemDetail | null

  constructor(status: number, problemDetail: ProblemDetail | null) {
    super(problemDetail?.detail ?? `Erro na API (status ${status})`)
    this.name = 'ApiError'
    this.status = status
    this.problemDetail = problemDetail
  }
}

export async function httpClient<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  })

  if (!response.ok) {
    const problemDetail = (await response.json().catch(() => null)) as ProblemDetail | null
    throw new ApiError(response.status, problemDetail)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}
