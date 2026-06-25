# CLAUDE.md

> Este documento es el contexto persistente del proyecto. Debe leerse al inicio de cualquier sesión de trabajo con Claude (o Claude Code) sobre este repositorio. Mantenerlo actualizado a medida que avanza el desarrollo.

---

## 1. Descripción general

Aplicación **Text-to-SQL** que permite a un usuario escribir una pregunta en lenguaje natural (español) y obtener:
1. La sentencia SQL generada por una IA (OpenAI).
2. El resultado de ejecutar esa sentencia contra una base de datos relacional, mostrado en una tabla.

Ejemplo de uso:
> Usuario escribe: "Dame todos los usuarios que vivan en Madrid"
> Backend genera: `SELECT * FROM usuarios WHERE ciudad = 'Madrid';`
> Backend ejecuta la query (solo lectura) y devuelve filas/columnas al frontend.

La aplicación es **dinámica respecto a la base de datos**: el usuario introduce la cadena de conexión (host, puerto, BD, usuario, contraseña) desde el frontend, y el backend extrae el esquema de esa base de datos en el momento, sin que el esquema esté hardcodeado.

---

## 2. Arquitectura

```
┌─────────────┐        ┌──────────────────────┐        ┌─────────────┐
│   React     │  REST  │   Spring Boot        │  JDBC  │  BD del     │
│  (Frontend) │ ─────► │   (Backend)          │ ─────► │  usuario    │
│             │ ◄───── │                      │ ◄───── │ (MySQL/PG/  │
└─────────────┘        │  - Conexión dinámica │        │  etc.)      │
                       │  - Extracción esquema│        └─────────────┘
                       │  - Prompt building   │
                       │  - Validación SQL    │        ┌─────────────┐
                       │  - Ejecución segura  │ ─────► │  OpenAI API │
                       └──────────────────────┘ ◄───── │  (Chat/LLM) │
                                                       └─────────────┘
```

### Stack tecnológico

| Capa | Tecnología |
|---|---|
| Frontend | React (+ fetch/axios para llamadas REST) |
| Backend | Spring Boot (Java) |
| Acceso a datos | `JdbcTemplate` con `DataSource` dinámico (no JPA, porque el esquema es desconocido a priori) |
| IA | API de OpenAI (Chat Completions / Responses API) |
| Base de datos objetivo | Cualquier BD relacional soportada por JDBC (MySQL, PostgreSQL, etc.) — la introduce el usuario |

---

## 3. Flujo funcional completo

1. **Configuración de conexión** (Frontend → Backend)
   - El usuario introduce en React: URL JDBC, usuario y contraseña de la BD.
   - Se envían al backend (endpoint de "conectar" / "configurar conexión").
   - **Importante**: estas credenciales deben tener permisos de **solo lectura (SELECT)** a nivel de base de datos. Esto se debe indicar claramente en el frontend como requisito/recomendación de seguridad, ya que es la primera línea de defensa.

2. **Extracción de esquema** (Backend)
   - Spring Boot se conecta temporalmente a la BD usando las credenciales recibidas.
   - Extrae metadatos: tablas, columnas, tipos de datos, claves primarias/foráneas (usando `DatabaseMetaData` de JDBC o consultas a `information_schema`).
   - Este esquema se guarda en sesión (o en un store temporal asociado a un `connectionId`/token de sesión) para no tener que re-extraerlo en cada pregunta.

3. **Pregunta en lenguaje natural** (Frontend → Backend)
   - El usuario escribe su pregunta (ej. "Dame todos los usuarios que vivan en Madrid").
   - Se envía al backend junto con el identificador de la conexión/sesión activa.

4. **Prompt Engineering** (Backend → OpenAI)
   - El backend construye un prompt que incluye:
     - El esquema de la BD (tablas, columnas, tipos).
     - Instrucciones estrictas: "Genera ÚNICAMENTE una sentencia SQL de tipo SELECT, sin explicaciones, sin markdown, sin punto y coma final opcional pero preferiblemente con él, usando exclusivamente las tablas/columnas del esquema proporcionado."
     - La pregunta del usuario.
   - Se envía a la API de OpenAI.

5. **Respuesta de OpenAI** (OpenAI → Backend)
   - OpenAI devuelve (idealmente) solo el SQL.
   - El backend limpia la respuesta (quitar posibles bloques ```sql, espacios, etc.).

6. **Validación de seguridad** (Backend) — **CRÍTICO**
   - Antes de ejecutar nada, el SQL generado pasa por un validador que:
     - Comprueba que la sentencia **empieza estrictamente por `SELECT`** (case-insensitive, ignorando espacios/comentarios iniciales).
     - Rechaza la query si contiene palabras clave prohibidas en cualquier parte: `INSERT`, `UPDATE`, `DELETE`, `DROP`, `ALTER`, `TRUNCATE`, `CREATE`, `GRANT`, `REVOKE`, `EXEC`, `EXECUTE`, `CALL`, `MERGE`, etc.
     - Rechaza si hay **múltiples sentencias** (detectar `;` seguido de más contenido no vacío — evitar SQL stacking / statement injection).
     - Rechaza si hay comentarios SQL sospechosos (`--`, `/* */`) que puedan ocultar código adicional.
     - (Opcional/avanzado) Verifica que las tablas/columnas referenzadas existan en el esquema extraído, como capa adicional.
   - Si la validación falla, el backend **no ejecuta nada** y devuelve un error claro al frontend.

7. **Ejecución** (Backend → BD)
   - Si la query es válida, se ejecuta con `JdbcTemplate.queryForList(sql)` o similar, usando el `DataSource` dinámico creado con las credenciales del usuario.
   - Se recomienda también aplicar `LIMIT`/paginación por defecto si la IA no lo añade, para evitar resultados masivos.

8. **Respuesta al frontend** (Backend → React)
   - Se devuelve: el SQL generado (para mostrarlo de forma transparente al usuario) + las filas/columnas resultantes.
   - React pinta una tabla dinámica (columnas no conocidas de antemano).

---

## 4. Modelo de seguridad (capas de defensa)

Este proyecto ejecuta SQL generado por una IA a partir de texto libre del usuario. Esto es sensible y debe tratarse con varias capas de defensa, no solo una:

1. **Principio de mínimo privilegio en la BD**: las credenciales que el usuario introduce deben pertenecer a un rol/usuario de BD con permisos `SELECT` únicamente (sin `INSERT/UPDATE/DELETE/DDL`). Esto se debe comunicar explícitamente en la UI.
2. **Whitelist de comandos**: el validador del backend solo permite sentencias que empiecen por `SELECT`.
3. **Blacklist de palabras clave peligrosas**: bloquear `DROP`, `DELETE`, `UPDATE`, `ALTER`, `TRUNCATE`, `CREATE`, `GRANT`, `REVOKE`, `EXEC`, `CALL`, `MERGE`, `INSERT`, etc., en cualquier posición del string.
4. **Bloqueo de multi-statement**: no permitir varias sentencias separadas por `;`.
5. **Prompt hardening**: el prompt a OpenAI debe instruir explícitamente que solo se genere SQL de lectura y que ignore cualquier instrucción del usuario que pida modificar/borrar datos (mitigar prompt injection desde el texto del usuario hacia el LLM).
6. **Timeout y límite de filas**: evitar consultas que tarden demasiado o devuelvan demasiadas filas (proteger recursos).
7. **No persistir credenciales en claro**: si se guardan en sesión, evaluar cifrado o como mínimo no loguearlas ni guardarlas en BD propia sin cifrar.
8. **Manejo de errores sin fuga de información sensible**: no devolver al frontend stacktraces completos de JDBC que puedan revelar detalles internos.

> Claude debe recordar siempre estas reglas al generar código del backend: cualquier endpoint que ejecute SQL dinámico debe pasar por el validador antes de tocar el `JdbcTemplate`.

---

## 5. Backend (Spring Boot) — diseño propuesto

### 5.1 Estructura de paquetes sugerida

```
com.textosql.backend
 ├── controller
 │    ├── ConnectionController.java     // configurar/probar conexión BD
 │    └── QueryController.java          // recibir pregunta en lenguaje natural, devolver resultados
 ├── service
 │    ├── ConnectionService.java        // gestión de DataSource dinámico + sesión
 │    ├── SchemaExtractorService.java   // extrae esquema vía DatabaseMetaData / information_schema
 │    ├── OpenAiService.java            // construye prompt y llama a la API de OpenAI
 │    ├── SqlValidatorService.java      // valida el SQL devuelto por la IA
 │    └── QueryExecutionService.java    // ejecuta el SQL validado con JdbcTemplate
 ├── dto
 │    ├── ConnectionRequestDTO.java     // url, usuario, password
 │    ├── NaturalLanguageQueryDTO.java  // pregunta del usuario + connectionId/sessionId
 │    ├── QueryResultDTO.java          // sql generado + columnas + filas
 │    └── SchemaDTO.java               // representación del esquema extraído
 ├── exception
 │    ├── InvalidSqlException.java
 │    ├── DatabaseConnectionException.java
 │    └── GlobalExceptionHandler.java   // @ControllerAdvice
 ├── config
 │    ├── CorsConfig.java               // permitir llamadas desde React (dev: localhost:3000/5173)
 │    └── OpenAiConfig.java             // configuración del cliente HTTP a OpenAI (API key vía variable de entorno)
 └── BackendApplication.java
```

### 5.2 Endpoints REST previstos

| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/api/connection` | Recibe URL JDBC + usuario + password. Prueba la conexión, extrae el esquema y lo guarda asociado a una sesión/connectionId. Devuelve `connectionId` y un resumen del esquema. |
| `GET` | `/api/connection/{connectionId}/schema` | Devuelve el esquema extraído (para mostrarlo opcionalmente en el frontend). |
| `POST` | `/api/query` | Body: `{ connectionId, question }`. Construye el prompt, llama a OpenAI, valida el SQL, lo ejecuta y devuelve `{ sql, columns, rows }`. |
| `DELETE` | `/api/connection/{connectionId}` | Cierra/limpia la conexión y elimina el esquema de la sesión. |

> Los nombres exactos pueden ajustarse, pero esta es la referencia funcional mientras se desarrolla.

### 5.3 Decisiones técnicas clave

- **No usar JPA/Hibernate con entidades fijas**: como el esquema es desconocido y dinámico, usar `JdbcTemplate` (o `SimpleJdbcCall`/`DataSource` puro) tanto para extraer metadatos como para ejecutar las queries generadas.
- **DataSource dinámico**: no se puede usar el `application.yml` con una BD fija. Crear el `DataSource` en tiempo de ejecución (p.ej. con `DriverManagerDataSource` o un pool ligero de HikariCP configurado dinámicamente) a partir de los datos que llegan del frontend.
- **Gestión de sesión**: decidir si se usa `HttpSession`, un `Map` en memoria con un `connectionId` (UUID), o algo más robusto (Redis) si se prevé escalar. Para una primera versión, un `ConcurrentHashMap<String, ConnectionContext>` en memoria es suficiente.
- **Llamada a OpenAI**: usar `RestTemplate`/`WebClient` o el SDK oficial si se añade como dependencia. La API key de OpenAI se debe leer de variables de entorno (`application.properties` con `${OPENAI_API_KEY}`), nunca hardcodeada ni enviada desde el frontend.
- **CORS**: configurar para permitir el origen del frontend React en desarrollo (ej. `http://localhost:5173` si es Vite, o `http://localhost:3000` si es CRA).
- **Validador SQL**: implementarlo como clase independiente y testeable (`SqlValidatorService`), con tests unitarios cubriendo casos de inyección típicos (`; DROP TABLE`, `UNION`-based si se quiere ser más estricto, comentarios `--`, `/*`, palabras clave en mayúsculas/minúsculas/mixtas).

### 5.4 Endurecimiento (Fase 6) — implementado

- **Validación de la JDBC URL** (`JdbcUrlValidator`): whitelist de prefijos (`jdbc:mysql:`, `jdbc:postgresql:`, `jdbc:h2:`), bloqueo de credenciales embebidas y de parámetros peligrosos del driver (`allowLoadLocalInfile`, `autoDeserialize`, `socketFactory`, `INIT` de H2, etc.). Se invoca **antes** de crear el `DataSource`.
- **Saneado de logs**: `ConnectionService.sanitizeJdbcUrl` enmascara contraseña y credenciales embebidas; la URL solo se loguea enmascarada. `ConnectionRequestDTO.toString()` oculta la contraseña. Los errores de JDBC se traducen a mensajes genéricos hacia el cliente (sin stacktraces).
- **Credenciales en memoria**: la contraseña solo vive dentro del `HikariDataSource` del `ConnectionContext`; no se guarda como campo aparte ni se devuelve en ninguna respuesta.
- **TTL y limpieza**: `ConnectionContext` registra `lastAccess`; `ConnectionService.purgeExpired(ttl)` cierra conexiones inactivas y `ConnectionCleanupTask` (`@Scheduled`, `@EnableScheduling`) la ejecuta periódicamente. Configurable: `app.connection.ttl-minutes` (30), `app.connection.cleanup-interval-ms` (300000).
- **Rate limiting**: `RateLimitInterceptor` (en memoria, por IP, ventana de 1 min) sobre `POST /api/query`; responde `429` al superar el límite. Configurable: `app.ratelimit.enabled` (true), `app.ratelimit.requests-per-minute` (20).

---

## 6. Frontend (React) — pendiente de desarrollo

No es el foco de esta fase, pero para que el backend se diseñe de forma compatible, debe tener en cuenta:

- Un formulario de conexión (URL JDBC, usuario, password) con aviso de que debe ser una cuenta de solo lectura.
- Un cuadro de texto para la pregunta en lenguaje natural.
- Una vista que muestre: el SQL generado (transparencia) y una tabla con columnas dinámicas para los resultados.
- Manejo de estados de carga/error (conexión fallida, SQL rechazado por el validador, error de OpenAI, etc.).

---

## 7. Notas para Claude / Claude Code

- Cuando se trabaje en este repositorio, **leer primero este documento** para recordar el alcance, la arquitectura y, sobre todo, **las reglas de seguridad de la sección 4**, que son innegociables: nunca generar código que ejecute SQL sin pasar por el validador de whitelist/blacklist descrito.
- Si se añaden nuevos endpoints, servicios o decisiones técnicas relevantes, **actualizar este archivo** (especialmente las secciones 5.2, 5.3 y 7) para que siga siendo la fuente de verdad del proyecto.
- La API key de OpenAI y cualquier secreto **nunca** deben aparecer hardcodeados en el código ni en este documento.
- Mantener el backend desacoplado de un motor de BD concreto: usar JDBC genérico para que funcione con MySQL, PostgreSQL, etc., según la URL de conexión que introduzca el usuario.