interface SpinnerProps {
  /** Texto accesible y visible junto al spinner. */
  label?: string;
  /** Tamaño en píxeles del círculo. */
  size?: number;
}

/** Indicador de carga reutilizable. */
export function Spinner({ label, size = 18 }: SpinnerProps) {
  return (
    <span className="spinner" role="status" aria-live="polite">
      <span
        className="spinner__circle"
        style={{ width: size, height: size }}
        aria-hidden="true"
      />
      {label && <span className="spinner__label">{label}</span>}
    </span>
  );
}
