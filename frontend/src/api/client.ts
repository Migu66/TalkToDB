import axios, { AxiosError } from 'axios';
import type {
  ApiErrorBody,
  ConnectionRequest,
  ConnectionResponse,
  NaturalLanguageQuery,
  QueryResult,
  Schema,
} from './types';

const baseURL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';

const http = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' },
  // 35s: por encima del timeout de consulta del backend (10s por defecto) con margen.
  timeout: 35_000,
});

/**
 * Error normalizado que exponen todas las funciones del cliente.
 * `status` es 0 cuando no hubo respuesta (red/CORS/timeout).
 */
export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

function isApiErrorBody(data: unknown): data is ApiErrorBody {
  return (
    typeof data === 'object' &&
    data !== null &&
    'message' in data &&
    typeof (data as { message: unknown }).message === 'string'
  );
}

/**
 * Traduce un error de axios a un `ApiError` con un mensaje en español
 * apto para mostrar al usuario. Prefiere el `message` del backend cuando
 * existe; si no, usa un mensaje genérico según el código HTTP.
 */
function toApiError(error: unknown): ApiError {
  if (error instanceof AxiosError) {
    const status = error.response?.status ?? 0;
    const body = error.response?.data;

    if (isApiErrorBody(body) && body.message.trim().length > 0) {
      return new ApiError(body.message, status);
    }

    if (status === 0) {
      if (error.code === 'ECONNABORTED') {
        return new ApiError(
          'La petición ha tardado demasiado. Inténtalo de nuevo.',
          0,
        );
      }
      return new ApiError(
        'No se pudo contactar con el servidor. ¿Está arrancado el backend?',
        0,
      );
    }

    return new ApiError(genericMessageFor(status), status);
  }

  return new ApiError('Se ha producido un error inesperado.', 0);
}

function genericMessageFor(status: number): string {
  switch (status) {
    case 400:
      return 'La consulta generada no es válida o fue rechazada por seguridad.';
    case 404:
      return 'La conexión no existe o ha expirado. Vuelve a conectar.';
    case 429:
      return 'Has hecho demasiadas consultas en poco tiempo. Espera un momento.';
    case 502:
      return 'Error al comunicar con el servicio de IA o la base de datos.';
    default:
      return 'Error del servidor. Inténtalo de nuevo más tarde.';
  }
}

/** POST /api/connection — crea la conexión y devuelve el connectionId. */
export async function connect(
  request: ConnectionRequest,
): Promise<ConnectionResponse> {
  try {
    const { data } = await http.post<ConnectionResponse>(
      '/api/connection',
      request,
    );
    return data;
  } catch (error) {
    throw toApiError(error);
  }
}

/** GET /api/connection/{id}/schema — esquema completo de la BD conectada. */
export async function getSchema(connectionId: string): Promise<Schema> {
  try {
    const { data } = await http.get<Schema>(
      `/api/connection/${encodeURIComponent(connectionId)}/schema`,
    );
    return data;
  } catch (error) {
    throw toApiError(error);
  }
}

/** POST /api/query — pregunta en lenguaje natural → SQL + resultados. */
export async function runQuery(
  request: NaturalLanguageQuery,
): Promise<QueryResult> {
  try {
    const { data } = await http.post<QueryResult>('/api/query', request);
    return data;
  } catch (error) {
    throw toApiError(error);
  }
}

/** DELETE /api/connection/{id} — cierra la conexión y libera recursos. */
export async function deleteConnection(connectionId: string): Promise<void> {
  try {
    await http.delete(`/api/connection/${encodeURIComponent(connectionId)}`);
  } catch (error) {
    throw toApiError(error);
  }
}
