import type { QueryResult } from '../api/types';

interface DynamicTableProps {
  columns: string[];
  rows: QueryResult['rows'];
}

/** Convierte cualquier valor de celda en algo imprimible y legible. */
function formatCell(value: unknown): string {
  if (value === null || value === undefined) return '';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

/**
 * Tabla que pinta columnas/filas desconocidas de antemano.
 * Las columnas vienen ordenadas desde el backend (ResultSetMetaData).
 */
export function DynamicTable({ columns, rows }: DynamicTableProps) {
  if (columns.length === 0) {
    return <p className="muted">La consulta no devolvió columnas.</p>;
  }

  return (
    <div className="table-scroll">
      <table className="data-table">
        <thead>
          <tr>
            <th className="data-table__rownum" aria-label="Número de fila">
              #
            </th>
            {columns.map((col) => (
              <th key={col}>{col}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, rowIndex) => (
            <tr key={rowIndex}>
              <td className="data-table__rownum">{rowIndex + 1}</td>
              {columns.map((col) => {
                const text = formatCell(row[col]);
                return (
                  <td key={col} className={text === '' ? 'cell--null' : undefined}>
                    {text === '' ? 'NULL' : text}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
