## Variables de entorno (Infisical)

Los secretos viven en Infisical, no en el repo. El `.env` local es una copia
**generada y desechable** — no se edita a mano ni se commitea.

**Setup (una vez):**
1. Pedir acceso al proyecto de Infisical (un admin te agrega en Organization -> Members).
2. Instalar: Para Windows `winget install infisical`
3. `infisical login`

**Generar / actualizar el `.env`:**
```
.\scripts\sync-env.ps1                  # entorno por defecto (dev, de .infisical.json)
.\scripts\sync-env.ps1 -Environment prod
```
Regeneralo cada vez que alguien cambie un secreto en Infisical. Despues: boton Run
de VS Code, `mvn spring-boot:run` o `docker compose up` — los tres leen el mismo `.env`.

**Notas:**
- `.infisical.json` (lleva el `workspaceId`) SI va al repo: es un identificador, no un secreto. Sin login + acceso al proyecto no sirve de nada.
- `.env` NUNCA va al repo (ya esta en `.gitignore`).
- No usar `infisical export ... > .env` directo: el `>` de PowerShell escribe UTF-16 y rompe el parseo de Spring. Usar `sync-env.ps1`.
- Alternativa sin archivo: `infisical run -- mvn spring-boot:run` (inyecta las vars como entorno; no sirve para el boton Run de VS Code).

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
- infisical run -- mvn spring-boot:run


- preguntar a nestor a donde va lo de las llaves, consolidar contrato, cuando sale verygana?, rep legal revisar al cambiarlo?, que pasa con lo de antecedentes?
- revisar lo de los blocks motivos para los activos(encuestas, branding_request?, ver error de ads y si se puede desbloquear), notion, probar lo del aumento de presupuesto y historias de impacto
- logs de init, documentar procesos
- revisar sistema de recomendacion
- revisar tests de helen de surveys (pag 97)
- cambiar el contrato si el compliance cambia la actividad economica


- flujo juegos full(puntuaciones) keys per action, completion (formula)
- flujo de jugar, metricas, casos de juego, etc.
- timer de saldo para incentivar su uso? cuando saldo 0 no permitir ediciones y cambios de estado?

- flujo de sesiones y recompensa bien revisar.


grecaptcha.ready(() => {
  grecaptcha.execute('6Ldic44tAAAAAHzLT1mtmZo2c3aHA_VwORFbJrFg', {
    action: 'login'
  }).then(token => console.log(token));
});


Fórmula: costo = min(maxRewardPerSessionCents, completionRewardCents + puntaje × scoreRewardFactor). La deduje de los nombres de campo y de los seeds de juegos (completar 5000, tope 20000, factor 1, promedio 15000); nadie de negocio la confirmó. Está aislada en calculateSessionRewardCents, por si hay que cambiarla.