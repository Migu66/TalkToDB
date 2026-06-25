import { useState } from 'react';
import {
  ApiError,
  connect,
  deleteConnection,
  getSchema,
  runQuery,
} from './api/client';
import type { ConnectionRequest, QueryResult, Schema } from './api/types';
import { ConnectionForm } from './components/ConnectionForm';
import { SchemaViewer } from './components/SchemaViewer';
import { QueryBox } from './components/QueryBox';
import { ResultView } from './components/ResultView';
import { ErrorBanner } from './components/ErrorBanner';
import './App.css';

interface ActiveConnection {
  id: string;
  schema: Schema;
}

function messageOf(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Se ha producido un error inesperado.';
}

export default function App() {
  const [connection, setConnection] = useState<ActiveConnection | null>(null);
  const [result, setResult] = useState<QueryResult | null>(null);

  const [connecting, setConnecting] = useState(false);
  const [querying, setQuerying] = useState(false);

  const [connectError, setConnectError] = useState<string | null>(null);
  const [queryError, setQueryError] = useState<string | null>(null);

  async function handleConnect(request: ConnectionRequest) {
    setConnecting(true);
    setConnectError(null);
    setResult(null);
    setQueryError(null);
    try {
      const { connectionId } = await connect(request);
      const schema = await getSchema(connectionId);
      setConnection({ id: connectionId, schema });
    } catch (error) {
      setConnectError(messageOf(error));
    } finally {
      setConnecting(false);
    }
  }

  async function handleQuery(question: string) {
    if (!connection) return;
    setQuerying(true);
    setQueryError(null);
    try {
      const queryResult = await runQuery({
        connectionId: connection.id,
        question,
      });
      setResult(queryResult);
    } catch (error) {
      setQueryError(messageOf(error));
      // 404: la conexión expiró en el backend → forzar reconexión.
      if (error instanceof ApiError && error.status === 404) {
        setConnection(null);
        setResult(null);
        setConnectError(messageOf(error));
      }
    } finally {
      setQuerying(false);
    }
  }

  async function handleDisconnect() {
    if (!connection) return;
    const id = connection.id;
    // Optimista: limpiamos la UI ya; el cierre en backend es best-effort.
    setConnection(null);
    setResult(null);
    setQueryError(null);
    setConnectError(null);
    try {
      await deleteConnection(id);
    } catch {
      // Si falla el cierre remoto, el TTL del backend acabará liberándola.
    }
  }

  return (
    <div className="app">
      <header className="app__header">
        <h1 className="app__title">
          SQL <span className="app__title-accent">IA</span> Translator
        </h1>
        <p className="app__subtitle">
          Pregunta en lenguaje natural y obtén SQL de solo lectura ejecutado
          sobre tu base de datos.
        </p>
      </header>

      <main className="app__main">
        {!connection ? (
          <>
            {connectError && (
              <ErrorBanner
                message={connectError}
                onDismiss={() => setConnectError(null)}
              />
            )}
            <ConnectionForm onConnect={handleConnect} loading={connecting} />
          </>
        ) : (
          <>
            <div className="connection-bar">
              <span className="connection-bar__status">
                <span className="dot dot--ok" aria-hidden="true" />
                Conectado · {connection.schema.tables?.length ?? 0} tablas
              </span>
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                onClick={handleDisconnect}
              >
                Desconectar
              </button>
            </div>

            <div className="layout">
              <aside className="layout__side">
                <SchemaViewer schema={connection.schema} />
              </aside>

              <section className="layout__content">
                <QueryBox onQuery={handleQuery} loading={querying} />

                {queryError && (
                  <ErrorBanner
                    message={queryError}
                    onDismiss={() => setQueryError(null)}
                  />
                )}

                {result && <ResultView result={result} />}
              </section>
            </div>
          </>
        )}
      </main>

      <footer className="app__footer">
        <span className="muted">
          Las consultas se validan (solo <code>SELECT</code>) antes de
          ejecutarse. Usa siempre credenciales de solo lectura.
        </span>
      </footer>
    </div>
  );
}
