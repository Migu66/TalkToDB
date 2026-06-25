import { useMemo, useState } from 'react';
import type { Schema, Table } from '../api/types';

interface SchemaViewerProps {
  schema: Schema;
}

/**
 * Visor del esquema extraído. Lista las tablas de forma colapsable para que el
 * usuario sepa qué puede preguntar (columnas, PK y FK).
 */
export function SchemaViewer({ schema }: SchemaViewerProps) {
  const tables = schema.tables ?? [];

  if (tables.length === 0) {
    return (
      <div className="card">
        <h2 className="card__title">Esquema</h2>
        <p className="muted">No se han detectado tablas en esta base de datos.</p>
      </div>
    );
  }

  return (
    <div className="card">
      <h2 className="card__title">
        Esquema <span className="badge">{tables.length} tablas</span>
      </h2>
      <ul className="schema-list">
        {tables.map((table) => (
          <TableItem key={table.name} table={table} />
        ))}
      </ul>
    </div>
  );
}

function TableItem({ table }: { table: Table }) {
  const [open, setOpen] = useState(false);

  const pkSet = useMemo(
    () => new Set(table.primaryKeys ?? []),
    [table.primaryKeys],
  );
  const fkByColumn = useMemo(() => {
    const map = new Map<string, string>();
    for (const fk of table.foreignKeys ?? []) {
      map.set(fk.column, `${fk.referencedTable}.${fk.referencedColumn}`);
    }
    return map;
  }, [table.foreignKeys]);

  const columns = table.columns ?? [];

  return (
    <li className="schema-table">
      <button
        type="button"
        className="schema-table__header"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
      >
        <span className={`chevron ${open ? 'chevron--open' : ''}`} aria-hidden="true">
          ▶
        </span>
        <span className="schema-table__name">{table.name}</span>
        <span className="schema-table__count">{columns.length} columnas</span>
      </button>

      {open && (
        <table className="schema-columns">
          <thead>
            <tr>
              <th>Columna</th>
              <th>Tipo</th>
              <th>Atributos</th>
            </tr>
          </thead>
          <tbody>
            {columns.map((col) => {
              const ref = fkByColumn.get(col.name);
              return (
                <tr key={col.name}>
                  <td className="mono">{col.name}</td>
                  <td className="mono muted">{col.type}</td>
                  <td>
                    {pkSet.has(col.name) && (
                      <span className="tag tag--pk" title="Clave primaria">
                        PK
                      </span>
                    )}
                    {ref && (
                      <span className="tag tag--fk" title={`Referencia a ${ref}`}>
                        FK → {ref}
                      </span>
                    )}
                    {!col.nullable && (
                      <span className="tag tag--nn" title="No admite nulos">
                        NOT NULL
                      </span>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </li>
  );
}
