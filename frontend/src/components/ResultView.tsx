import { useState } from 'react';
import type { QueryResult } from '../api/types';
import { DynamicTable } from './DynamicTable';

interface ResultViewProps {
  result: QueryResult;
}

/**
 * Vista de resultados: muestra el SQL generado (transparencia, CLAUDE.md §3.8)
 * con opción de copiar, el aviso de truncado y la tabla dinámica.
 */
export function ResultView({ result }: ResultViewProps) {
  const [copied, setCopied] = useState(false);

  async function copySql() {
    try {
      await navigator.clipboard.writeText(result.generatedSql);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      // El portapapeles puede no estar disponible (http, permisos): se ignora.
    }
  }

  return (
    <div className="card result-view">
      <h2 className="card__title">3. Resultado</h2>

      <div className="sql-block">
        <div className="sql-block__bar">
          <span className="sql-block__label">SQL generado</span>
          <button type="button" className="btn btn--ghost btn--sm" onClick={copySql}>
            {copied ? '¡Copiado!' : 'Copiar'}
          </button>
        </div>
        <pre className="sql-block__code">
          <code>{result.generatedSql}</code>
        </pre>
      </div>

      <div className="result-view__meta">
        <span className="badge">
          {result.rowCount} {result.rowCount === 1 ? 'fila' : 'filas'}
        </span>
        {result.truncated && (
          <span className="notice notice--info notice--inline">
            Resultados limitados a {result.rowCount} filas. Acota la pregunta para
            ver menos datos.
          </span>
        )}
      </div>

      {result.rowCount === 0 ? (
        <p className="muted">La consulta se ejecutó correctamente pero no devolvió filas.</p>
      ) : (
        <DynamicTable columns={result.columns} rows={result.rows} />
      )}
    </div>
  );
}
