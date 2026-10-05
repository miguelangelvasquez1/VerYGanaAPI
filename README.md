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

## Docker

**La imagen no lleva la clave privada del JWT.** `.dockerignore` excluye
`src/main/resources/certs/private.pem` (la pública, `public.pem`, sí está versionada y entra). En un
contenedor la API toma las claves del entorno, y **si faltan no arranca**
(`rsa.private-key ... certs/private.pem ... does not exist`) en vez de firmar con una clave empaquetada.
`mvn spring-boot:run` y los tests no cambian: siguen leyendo `certs/` del disco.

- `RSA_PRIVATEKEY` y `RSA_PUBLICKEY` salen de Infisical (entorno `dev` para Docker local). Formato: **PEM
  completo, con saltos de línea reales**; la privada en PKCS#8 (`-----BEGIN PRIVATE KEY-----`, no
  `BEGIN RSA PRIVATE KEY`). Un `\n` literal, el PEM entre comillas dentro de la propia variable o solo el
  base64 no funcionan, y con un formato inválido Spring Boot escribe la clave privada entera en el log al
  fallar el arranque: no pegues ese log en ningún lado.
- Comprobar el formato sin imprimir la clave: `infisical run --env=dev -- sh -c 'printf "%s\n" "$RSA_PRIVATEKEY" | wc -l; printf %s "$RSA_PRIVATEKEY" | head -c 27'`
  debe dar unas 28 líneas y `-----BEGIN PRIVATE KEY-----`.

**Correr en local con Docker:**
```
docker compose down
docker compose up --build        # lee .env (env_file)
docker compose restart           # solo cambio de .env
mvn spring-boot:run              # sin Docker
```
`docker compose` con `env_file` **sí** admite el PEM de varias líneas si el `.env` lo trae entre comillas
(`RSA_PRIVATEKEY="-----BEGIN PRIVATE KEY-----` ... `-----END PRIVATE KEY-----"`); un `\n` literal sin
comillas no sirve. Tras agregar las claves en Infisical, regenera el `.env` y comprueba, sin imprimir la
clave, que la cabecera y el número de líneas son los correctos (`sync-env.ps1` no se ha probado con
valores de varias líneas; si el `.env` queda con `\n` literal, usa `infisical run -- docker ...`):
```
awk '/^RSA_PRIVATEKEY=/{print substr($0,1,40); f=1} f{n++} /END PRIVATE KEY/{if(f){print n" lineas"; f=0}}' .env
```

**Correr la imagen sin compose** (`docker build` + `docker run`): `docker run --env-file .env` **no**
admite valores de varias líneas (falla con `contains whitespaces`). Pasa las dos claves desde el entorno
del proceso (`-e NOMBRE` sin valor las copia del host) y el resto desde un `.env` sin ellas:
```
docker build -t verygana-api:local .
infisical run --env=dev -- bash -c 'docker run -p 8080:8080 -e RSA_PRIVATEKEY -e RSA_PUBLICKEY \
  --env-file <(grep -E "^[A-Z][A-Z0-9_]*=" .env | grep -vE "^RSA_") verygana-api:local'
```
(cambiar a `host.docker.internal` en la URL de la base). Alternativa: montar los dos archivos con `-v` y
fijar `-e RSA_PRIVATEKEY=file:/ruta`.

**Subir la imagen a un registry:** solo a uno **privado** (ECR cuando exista). La beta no usa registry:
`docker save | ssh ... docker load` (ver `specs/006-beta-deployment`). **Nunca publicar la imagen en un
registry público** ni con la cuenta personal de nadie: un repositorio público de Docker Hub llegó a tener
la clave privada de dev dentro, y quien la tenga puede firmarse un token de administrador. Antes de subir
o guardar una imagen, comprobar que no lleva la clave (debe dar 0):
```
docker run --rm --entrypoint grep <imagen> -c 'certs/private.pem' /app/app.jar
```
Para construir desde un commit exacto, sin lo que haya sin commitear: `git archive <sha> | docker build -t verygana-api:<sha> -`.

La prueba de carga (`stress-tests/`) genera su propio par de claves de prueba; ver su guía.
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