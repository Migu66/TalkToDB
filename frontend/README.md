# Frontend — SQL IA Translator

UI mínima (Vite + React + TypeScript) para el backend Spring Boot del proyecto.
Permite conectar a una base de datos, ver su esquema, preguntar en lenguaje
natural y mostrar el SQL generado junto a los resultados.

## Requisitos

- Node.js 20+ (probado con 24) y npm.
- El backend arrancado (por defecto en `http://localhost:8080`).

## Configuración

La URL del backend se lee de la variable `VITE_API_URL`. Copia la plantilla:

```bash
cp .env.example .env
# Edita .env si tu backend no está en http://localhost:8080
```

Si no defines `.env`, se usa `http://localhost:8080` por defecto.

## Arranque

```bash
npm install
npm run dev      # http://localhost:5173
```

El origen `http://localhost:5173` ya está permitido por el CORS del backend
(`app.cors.allowed-origins` en `application.properties`).

## Scripts

| Script            | Descripción                                  |
| ----------------- | -------------------------------------------- |
| `npm run dev`     | Servidor de desarrollo con HMR.              |
| `npm run build`   | Type-check (`tsc -b`) + build de producción. |
| `npm run preview` | Sirve el build de `dist/`.                   |
| `npm run lint`    | Type-check sin emitir.                        |

## Estructura

```
src/
  api/
    types.ts     # tipos espejo de los DTO del backend
    client.ts    # axios + connect / getSchema / runQuery / deleteConnection
  components/
    ConnectionForm.tsx   # conexión + aviso de credenciales read-only
    SchemaViewer.tsx     # tablas/columnas colapsables
    QueryBox.tsx         # pregunta en lenguaje natural
    ResultView.tsx       # SQL generado + meta
    DynamicTable.tsx     # tabla de columnas/filas dinámicas
    Spinner.tsx          # indicador de carga
    ErrorBanner.tsx      # mensajes de error
  App.tsx        # orquestación de estados (conexión, consulta, errores)
```

## Nota de seguridad

La app avisa explícitamente de usar credenciales de **solo lectura (SELECT)**,
en línea con el modelo de seguridad del proyecto (ver `CLAUDE.md` §3.1 y §4).
Toda consulta se valida en el backend (whitelist `SELECT`) antes de ejecutarse.
