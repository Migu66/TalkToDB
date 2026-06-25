import { useState, type FormEvent } from 'react';
import type { ConnectionRequest } from '../api/types';
import { Spinner } from './Spinner';

interface ConnectionFormProps {
  onConnect: (request: ConnectionRequest) => void;
  loading: boolean;
}

const PLACEHOLDER_URL = 'jdbc:postgresql://localhost:5432/mi_base';

/**
 * Formulario de conexión a la base de datos del usuario.
 * Incluye el aviso de seguridad de usar credenciales de SOLO LECTURA
 * (CLAUDE.md §3.1 / §4.1): es la primera línea de defensa.
 */
export function ConnectionForm({ onConnect, loading }: ConnectionFormProps) {
  const [jdbcUrl, setJdbcUrl] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  const canSubmit =
    jdbcUrl.trim().length > 0 && username.trim().length > 0 && !loading;

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!canSubmit) return;
    onConnect({
      jdbcUrl: jdbcUrl.trim(),
      username: username.trim(),
      password,
    });
  }

  return (
    <form className="card connection-form" onSubmit={handleSubmit}>
      <h2 className="card__title">1. Conexión a la base de datos</h2>

      <div className="notice notice--warning" role="note">
        <strong>Usa credenciales de solo lectura.</strong> La cuenta debe tener
        únicamente permisos <code>SELECT</code>. Es la primera barrera de
        seguridad: aunque el resto de defensas fallasen, una cuenta de solo
        lectura no puede modificar ni borrar datos.
      </div>

      <label className="field">
        <span className="field__label">URL JDBC</span>
        <input
          type="text"
          className="field__input"
          value={jdbcUrl}
          onChange={(e) => setJdbcUrl(e.target.value)}
          placeholder={PLACEHOLDER_URL}
          autoComplete="off"
          spellCheck={false}
          disabled={loading}
          required
        />
        <span className="field__hint">
          Formatos admitidos: <code>jdbc:postgresql:</code>,{' '}
          <code>jdbc:mysql:</code>, <code>jdbc:h2:</code>.
        </span>
      </label>

      <div className="field-row">
        <label className="field">
          <span className="field__label">Usuario</span>
          <input
            type="text"
            className="field__input"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            spellCheck={false}
            disabled={loading}
            required
          />
        </label>

        <label className="field">
          <span className="field__label">Contraseña</span>
          <input
            type="password"
            className="field__input"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            disabled={loading}
          />
        </label>
      </div>

      <button type="submit" className="btn btn--primary" disabled={!canSubmit}>
        {loading ? <Spinner label="Conectando…" /> : 'Conectar'}
      </button>
    </form>
  );
}
