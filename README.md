## Observations:
- Implement Nimbus for JWT, implementar una clave separada para el refresh token, implementar redis para escalabilidad, accessToken en header
- La clave privada se usa para firmar el token. La clave pública se usa para verificarlo.
- Si se introducen refresh tokens, los self-signed JWTs pueden no ser lo mejor
- Article for JWTs: https://www.danvega.dev/blog/spring-security-jwt
- Se usa: configuración de seguridad basada en recursos (Resource Server) de Spring Boot

- Usar swagger para pruebas
- logs,
- Ver los preauthorize
- Implementar redis en vez de caché?
- Usar OffsetDateTime
- Añadir caché de usuarios?
- Mirar lo del cache de categorias
- https://www.datos.gov.co/api/v3/views/gdxc-w37w/export.csv?accessType=DOWNLOAD&app_token=bHWsGtRFRP9x8Hl8lYivqM1hQ -> Municipalitys and Departments

- cada vez que se cambia de ambiente toca crear los webhooks de zapsign y cambiar la bandera sandbox

## Docker cl:
docker compose down
docker compose up --build
docker compose restart (solo cambio de .env)
mvn spring-boot:run

## Para subir a docker.io:
1.
mvn clean package

2.
docker build -t miguelvasquez777/verygana-api:latest .
docker push miguelvasquez777/verygana-api:latest

## Para correr localmente:
docker build -t miguelvasquez777/verygana-api:latest .
docker run --env-file .env -p 8080:8080 miguelvasquez777/verygana-api:latest (cambiar a host.docker.internal en la bd)


probar sesion, check everything
revisar documentacion de nestor sobre los planes y empezar a hacer. (- si un commercial cambia a plan mas bajo que no se devuelva lo creado?)
- un empresario no se puede cambiar de plan, cada recarga genera un nuevo contrato?, plan especial si recarga de mas, suspension de activos si llega a 0, notific de presupuesto.
- metricas

- preguntar a nestor, flujo juegos full
- ver lo de antecedentes.
- flujo de jugar, metricas, casos de juego, etc.

- probar max_ads(state del plan lo devuelve), flujo de sesiones y recompensa bien revisar.

- ver que los asstes se borren del cdn.
- ver que se puede editar o no despues de los contratos


- actuator (backend metrics)?

cambio de plan:
C:\Users\Usuario\.claude\plans\1-antes-de-eso-jaunty-harbor.md


// -------------------------------------------
Revisé el flujo completo del lado del frontend. Lo bueno: no hay ningún bug ahí — authService.login() y authService.refresh() (en authService.ts) ya usan credentials: 'include' en ambos fetch, y no hay ningún código JS en el repo que toque document.cookie manualmente. La cookie del refresh token es 100% httpOnly y la controla el backend vía Set-Cookie — desde este repo no se "setea" nada de esa cookie.

Encontré algo concreto que sí explica el síntoma:

Dos cookies de sesión totalmente independientes, con vidas distintas:

La sesión de NextAuth (next-auth.session-token) dura 7 días — está hardcodeado en authOptions.ts:63: session: { strategy: 'jwt', maxAge: 7 * 24 * 60 * 60 }.
La cookie httpOnly del refresh token la pone el backend en la respuesta de /auth/login / /auth/refresh, y su duración depende de los atributos Max-Age/Expires que el backend le ponga — algo que no vive en este repo, así que no lo puedo inspeccionar directamente.
Esto explica exactamente tu síntoma: la app "cree" que seguís logueado por 7 días (gracias a la cookie de NextAuth, que sí sobrevive a cerrar el navegador), pero si la cookie del refresh token del backend no tiene Max-Age/Expires explícito, el navegador la trata como cookie de sesión y la borra al cerrar Chrome del todo — no al cerrar una pestaña, sino al matar el proceso. Al reabrir "en un rato", NextAuth sigue pensando que hay sesión, la app intenta refrescar, pero no hay cookie que mandar (o manda una vacía/vieja) → el backend no encuentra nada que coincida en la BD.

Cómo confirmarlo en 30 segundos:

Logueate, abrí DevTools → Application → Cookies → el dominio de tu backend (localhost:8080 en dev).
Mirá la columna Expires / Max-Age de la cookie del refresh token. Si dice "Session" en vez de una fecha concreta, ahí está el bug — hay que agregarle Max-Age/Expires (y en cross-origin, también SameSite=None; Secure) del lado del backend al setear esa cookie.
Como esto lo define el backend al hacer Set-Cookie, no es algo que pueda arreglar desde este repo (frontend). Si me pasás el código del endpoint /auth/login o /auth/refresh del backend (o el repo), reviso la config exacta de la cookie con vos.