## Dependencies:
- Spring web
- Spring Data JPA
- MySQL Connector
- Spring security
- oauth2 resource server
- configuration processor
- Lombok

## Observations:
- Implement Nimbus for JWT, implementar una clave separada para el refresh token, implementar redis para escalabilidad, accessToken en header
- La clave privada se usa para firmar el token. La clave pública se usa para verificarlo.
- Si se introducen refresh tokens, los self-signed JWTs pueden no ser lo mejor
- Article for JWTs: https://www.danvega.dev/blog/spring-security-jwt

- Se usa: configuración de seguridad basada en recursos (Resource Server) de Spring Boot
- Usar swagger para pruebas
- logs,
- Ver los preauthorize, ver mensajes de error para autenticaciones
- Implementar redis en vez de caché?
- Usar OffsetDateTime
- Añadir caché de usuarios?
- Mirar lo del cache de categorias
- https://www.datos.gov.co/api/v3/views/gdxc-w37w/export.csv?accessType=DOWNLOAD&app_token=bHWsGtRFRP9x8Hl8lYivqM1hQ -> Municipalitys and Departments
- Usar presigned URLs en s3?

Mejoras Implementadas en tokens:

Índices optimizados para queries rápidas
Limpieza automática con schedulers
Tracking de sesiones detallado con IP, UserAgent, Device ID
Límite de sesiones por usuario (anti-spam)
Queries estadísticas para monitoreo y reportes
Detección de actividad sospechosa

CREATE TABLE audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    username VARCHAR(100),              -- usuario que hizo la acción
    action VARCHAR(50) NOT NULL,        -- CREATE, UPDATE, DELETE, LOGIN, etc.
    entity_name VARCHAR(100) NOT NULL,  -- nombre de la entidad (User, Ad, etc.)
    entity_id BIGINT,                   -- id de la entidad afectada
    old_values TEXT,                    -- valores antes (JSON)
    new_values TEXT,                    -- valores después (JSON)
    ip_address VARCHAR(50),             -- IP del cliente (opcional)
    user_agent VARCHAR(255),            -- navegador/cliente (opcional)
    status VARCHAR(20) DEFAULT 'SUCCESS', -- SUCCESS / FAILURE
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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

- obtener session estandarizado
- Flujo de juegos:
    1. El juego se inicia con los parámetros de la url (session, userhash, branded flag, campaign_id)
    2. El juego lee los parámetros y llama al backend para pedir los assets y configuración
    3. El juego recibe los assets y configuración y empieza su ejecución


- cuando un usuario se inactiva se cierra la sesion
- validar que cuando un commercial activa un anuncio ya haya sido activado por el admin
- manejar errores de back a front
- si state devulve que ya tiene max ads bloquear el creacion de ads


  mirar que va en detalles de la campana y ajustar con planes




  const MIME_TYPES = {
  html: 'text/html',
  js: 'application/javascript',
  wasm: 'application/wasm',
  data: 'application/octet-stream',
  json: 'application/json',
  png: 'image/png',
  jpg: 'image/jpeg',
  css: 'text/css'
};

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    let path = url.pathname;

    if (path.startsWith('/')) {
      path = path.slice(1);
    }

    if (path.endsWith('/')) {
      path += 'index.html';
    }

    const key = path;
    console.log('R2 key:', key);

    const object = await env.VERYGANA_GAMES_BUCKET.get(key);

    if (!object) {
      return new Response('Game not found', { status: 404 });
    }

    const ext = key.split('.').pop();

    return new Response(object.body, {
      headers: {
        'Content-Type': MIME_TYPES[ext] || 'application/octet-stream',
        'Cache-Control': 'public, max-age=31536000, immutable',
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "GET, HEAD, OPTIONS",
        "Access-Control-Allow-Headers": "*"
      }
    });
  }
};


aws s3 cp . s3://verygana-games/builds/build-bogota/test1 `
  --recursive `
  --endpoint-url https://e1cb6cf5ad3bfde79bd415645b6a29e0.r2.cloudflarestorage.com `
  --exclude "*.gz"

aws s3 cp . s3://verygana-games/builds/build-bogota/test1 `
  --recursive `
  --endpoint-url https://e1cb6cf5ad3bfde79bd415645b6a29e0.r2.cloudflarestorage.com `
  --exclude "*" `
  --include "*.js.gz" `
  --content-encoding gzip `
  --content-type application/javascript

aws s3 cp . s3://verygana-games/builds/build-bogota/test1 `
  --recursive `
  --endpoint-url https://e1cb6cf5ad3bfde79bd415645b6a29e0.r2.cloudflarestorage.com `
  --exclude "*" `
  --include "*.data.gz" `
  --content-encoding gzip `
  --content-type application/octet-stream

aws s3 cp . s3://verygana-games/builds/build-bogota/test1 `
  --recursive `
  --endpoint-url https://e1cb6cf5ad3bfde79bd415645b6a29e0.r2.cloudflarestorage.com `
  --exclude "*" `
  --include "*.wasm.gz" `
  --content-encoding gzip `
  --content-type application/wasm
  
- hacer validacion de planes en el front globalmente no solo sidebar.?

- hacer env de valor de llaves o cents, wallets en cents, poner 100.000 maximo de likes, ver que los asstes se borren del cdn. mejorar el coso de like ad vista. preguntar a nestor, validar que el flujo sea perfecto.

- organizar mejor las rutas, seguir con ads full.