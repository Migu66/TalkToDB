/**
 * Tipos del contrato REST con el backend Spring Boot.
 * Reflejan 1:1 los `record` DTO de `com.sqlai.sql_ia_translator.dto`.
 * Si cambia un DTO en el backend, actualizar aquí.
 */

// --- Petición de conexión: POST /api/connection ---
export interface ConnectionRequest {
  jdbcUrl: string;
  username: string;
  password: string;
}

// --- Respuesta de conexión (ConnectionResponseDTO) ---
export interface ConnectionResponse {
  connectionId: string;
  tableCount: number;
  tableNames: string[];
}

// --- Esquema (SchemaDTO + TableDTO + ColumnDTO + ForeignKeyDTO) ---
export interface Column {
  name: string;
  type: string;
  nullable: boolean;
}

export interface ForeignKey {
  column: string;
  referencedTable: string;
  referencedColumn: string;
}

export interface Table {
  name: string;
  columns: Column[];
  primaryKeys: string[];
  foreignKeys: ForeignKey[];
}

export interface Schema {
  tables: Table[];
}

// --- Consulta en lenguaje natural: POST /api/query ---
export interface NaturalLanguageQuery {
  connectionId: string;
  question: string;
}

// --- Resultado de la consulta (QueryResultDTO) ---
export interface QueryResult {
  generatedSql: string;
  columns: string[];
  rows: Array<Record<string, unknown>>;
  rowCount: number;
  truncated: boolean;
}

// --- Cuerpo de error estándar (ErrorResponseDTO) ---
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}
