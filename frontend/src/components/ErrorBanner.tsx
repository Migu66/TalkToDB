interface ErrorBannerProps {
  message: string;
  onDismiss?: () => void;
}

/** Banner de error no intrusivo. Se cierra con la X si se pasa `onDismiss`. */
export function ErrorBanner({ message, onDismiss }: ErrorBannerProps) {
  return (
    <div className="error-banner" role="alert">
      <span className="error-banner__icon" aria-hidden="true">
        ⚠
      </span>
      <span className="error-banner__text">{message}</span>
      {onDismiss && (
        <button
          type="button"
          className="error-banner__close"
          onClick={onDismiss}
          aria-label="Cerrar mensaje de error"
        >
          ×
        </button>
      )}
    </div>
  );
}
