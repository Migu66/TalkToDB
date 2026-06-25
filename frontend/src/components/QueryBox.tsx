import { useState, type FormEvent, type KeyboardEvent } from 'react';
import { Spinner } from './Spinner';

interface QueryBoxProps {
  onQuery: (question: string) => void;
  loading: boolean;
}

const EXAMPLES = [
  'Dame todos los usuarios que vivan en Madrid',
  'Cuántos pedidos hay por cada cliente',
  'Los 10 productos más caros',
];

/** Cuadro de pregunta en lenguaje natural. */
export function QueryBox({ onQuery, loading }: QueryBoxProps) {
  const [question, setQuestion] = useState('');

  const canSubmit = question.trim().length > 0 && !loading;

  function submit() {
    if (!canSubmit) return;
    onQuery(question.trim());
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    submit();
  }

  // Ctrl/Cmd + Enter envía sin tener que apuntar al botón.
  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
      event.preventDefault();
      submit();
    }
  }

  return (
    <form className="card query-box" onSubmit={handleSubmit}>
      <h2 className="card__title">2. Tu pregunta</h2>

      <textarea
        className="query-box__textarea"
        value={question}
        onChange={(e) => setQuestion(e.target.value)}
        onKeyDown={handleKeyDown}
        placeholder="Escribe tu pregunta en lenguaje natural…"
        rows={3}
        disabled={loading}
      />

      <div className="query-box__examples">
        <span className="muted">Ejemplos:</span>
        {EXAMPLES.map((ex) => (
          <button
            key={ex}
            type="button"
            className="chip"
            onClick={() => setQuestion(ex)}
            disabled={loading}
          >
            {ex}
          </button>
        ))}
      </div>

      <div className="query-box__actions">
        <span className="muted query-box__hint">Ctrl/⌘ + Enter para enviar</span>
        <button type="submit" className="btn btn--primary" disabled={!canSubmit}>
          {loading ? <Spinner label="Consultando…" /> : 'Consultar'}
        </button>
      </div>
    </form>
  );
}
