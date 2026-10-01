# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# verYganar API

API en Spring Boot 3.5.3 / Java 21 / MySQL. Build con `./mvnw`. Paquete raíz
`com.verygana2`, clase principal `RifacelApplication`.

## Constitución

Principios innegociables del proyecto. Tienen prioridad sobre el resto de este
archivo, y en la revisión de un PR toda violación se marca.

@docs/constitution.md

## Build y tests

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw test                                   # todos los tests
./mvnw test -Dtest=AuthControllerRecaptchaTest # una clase
./mvnw test -Dtest='AuthControllerRecaptchaTest#nombreDelMetodo'
./mvnw verify                                  # lo que corre CI: tests + repackage del jar
./mvnw compile && ./mvnw pmd:check             # PMD: dos invocaciones separadas, no una
./mvnw spring-boot:run                         # perfil dev por defecto
```

El build **exige JDK 21**. Con un JDK más nuevo por defecto en el sistema, Maven
falla; hay que exportar `JAVA_HOME` como arriba.

PMD (`pmd-ruleset.xml`, solo reglas de código muerto) es puerta bloqueante en CI.
Si se corre como `./mvnw compile pmd:check` en una sola invocación, reporta falsos
positivos porque el auxclasspath se resuelve antes de compilar.

Ningún test toca base de datos ni usa `@SpringBootTest`. Hay dos estilos:

- Unitarios con Mockito. Los de controller usan `MockMvcBuilders.standaloneSetup`:
  hay que fijar `MappingJackson2HttpMessageConverter` con un `ObjectMapper` de
  `Jackson2ObjectMapperBuilder`, o gana el converter de XML y el cuerpo del error
  se pierde (ver `GameControllerPreviewAssetsTest`).
- `*SecurityIntegrationTest` / `*AuthorizationMatrixIntegrationTest`: `@WebMvcTest`
  con la cadena de seguridad real y JWT firmados en el test, para probar los
  `@PreAuthorize` rol por rol. Un endpoint nuevo con restricción de rol va aquí.
  Pese al nombre, corren con surefire (`-Dtest` funciona igual).

Fixtures compartidos en `src/test/java/com/verygana2/testsupport`.

## Secretos y perfiles

Los secretos viven en Infisical. El `.env` local es una copia generada con
`scripts/sync-env.ps1` (o se inyectan con `infisical run -- ./mvnw spring-boot:run`);
no se edita a mano ni se commitea. Ver README.

Perfiles: `dev` (activo por defecto), `prod`, y `beta`, que se monta **encima** de
prod (`SPRING_PROFILES_ACTIVE=prod,beta`) y solo sobrescribe dinero y terceros
para dejarlos en sandbox.

## Base de datos

- El esquema lo maneja Flyway (`src/main/resources/db/migration/`, `V<n>__*.sql`).
  Hibernate solo valida (`ddl-auto: validate`): una entidad con una columna que
  no tiene migración tumba el arranque. Todo cambio de entidad trae su migración.
- `V1__baseline.sql` es el dump del esquema; con `baseline-on-migrate` una base
  existente se marca en V1 y corre de V2 en adelante. Como las bases viejas de
  dev pueden tener ya cambios que hizo `ddl-auto: update`, las migraciones deben
  ser idempotentes (MySQL no tiene `ADD COLUMN IF NOT EXISTS`; ver el patrón con
  `information_schema` en V10).
- Nunca editar ni renumerar una migración ya aplicada: `validate-on-migrate`
  compara checksums y la app no arranca. Si dos ramas crean el mismo número, la
  que llega después toma el siguiente libre.
- Seeds: `DataSeeder` (`@Profile("dev")`) ejecuta los `.sql` de `db/seed/`.
  Otros initializers en `config/` cargan datos propios (p. ej. el cuestionario
  diagnóstico desde JSON).

## Arquitectura

Capas clásicas por paquete: `controllers` → `services` (interfaz en
`services/interfaces`, implementación `*ServiceImpl`) → `repositories` (Spring
Data JPA, filtros con `utils/specifications`) → `models`. DTOs en `dtos`,
mapeo con MapStruct en `mappers` (con DTOs `@Builder`, un `@AfterMapping` debe
recibir el builder como `@MappingTarget` o no se ejecuta, sin error ni warning;
se comprueba buscando el método en el `*MapperImpl` generado en
`target/generated-sources/annotations`). Cada capa se subdivide
por dominio: juegos/brandeo, mascotas, marketplace, rifas, finanzas, PQRS,
compliance, planes, encuestas, niveles/referidos.

Piezas transversales que no se ven desde un solo archivo:

- **Seguridad**: OAuth2 Resource Server con JWT firmado por RSA
  (`security/auth`: `TokenService`, `JwtBearerFilter`, refresh tokens en cookie
  httpOnly). Autorización por `@PreAuthorize` en los controllers. Las rutas
  sin autenticación están centralizadas en `security/PublicPaths`; cualquier
  webhook nuevo va ahí. reCAPTCHA en `security/recaptcha`; feature flags en
  `security/systemFeatures`.
- **Integraciones externas**: Wompi (pagos y payouts, webhooks en
  `/wompi/events`), ZapSign (firma de contratos, webhooks en `/zapsign/events`;
  al cambiar de ambiente hay que recrear los webhooks y cambiar la bandera
  sandbox), Twilio (SMS), correo en `services/email`, almacenamiento en R2 vía
  `storage/`.
- **Asincronía**: eventos de Spring en `event/` (contrato firmado/rechazado, XP,
  level up, referidos), un outbox en `services/outbox`, y jobs `@Scheduled` en
  `schedulers/` (payouts, conciliación, vencimientos, SLA de PQRS, alertas de
  presupuesto). Un cambio en un flujo de dinero o de estados suele tener una
  segunda mitad en un scheduler o listener.
- **Observabilidad**: Actuator + Prometheus; configs de Prometheus, Grafana y
  Alloy en `monitoring/`, validadas en CI.
- **Brandeo de juegos**: el anunciante nunca publica archivos directo en la
  configuración del juego. Sus recursos corporativos quedan privados
  (`branding/{id}/resources/`, URL temporal) y el diseñador, que es el auditor de
  contenido y calidad, los publica como assets. Lo que el anunciante *escribe*
  (preguntas, palabras, pistas) sí va a la configuración. No es un hueco: no
  agregar endpoints para que el comercial suba assets públicos.

## Qué revisar en cada PR

Estas reglas salen de bugs que ya llegaron a `main`. No son teóricas.

### Configuración por perfiles

Una clave nueva debe existir en los tres archivos: `application.yml`,
`application-dev.yml` y `application-prod.yml`. Un `@Value` sin default que
falte en un perfil tumba el arranque de ese perfil, y no se nota hasta el
despliegue. Si un PR mueve un bloque de configuración entre archivos, verificar
que ningún perfil se quede sin la clave. `application-beta.yml` hereda de prod y
solo hay que tocarlo si la clave tiene que ver con dinero o con terceros.

### Puertas de seguridad y efectos colaterales

Cuando un endpoint gana una validación que lanza excepción **antes** de llamar
al service, decir explícitamente qué deja de ocurrir: creación de usuarios,
correos de verificación, cobros, notificaciones. Un rechazo en el controller
cancela todo lo que venía después, y el síntoma que reporta el usuario suele ser
el efecto colateral, no la validación.

### reCAPTCHA

Cada endpoint verifica contra su propia acción (`login`, `register_consumer`,
`register_commercial`). Deben coincidir con lo que manda el frontend. Un PR que
agregue un endpoint con reCAPTCHA debe agregar también su acción a
`application.yml` y su test en `AuthControllerRecaptchaTest`.

### Dependencias externas

Toda llamada saliente necesita timeout. Y hay que distinguir dos fallos que no
son lo mismo: que el proveedor responda "inválido" (fallar cerrado es correcto)
y que no se pueda hablar con el proveedor (fallar cerrado convierte la caída del
proveedor en caída nuestra). Marcar cualquier `catch (Exception)` que colapse
ambos casos en el mismo retorno.

### Seguridad

Marcar siempre los cambios en anotaciones `@PreAuthorize` / `hasRole`, aunque el
diff sea de una línea. Marcar también cualquier log o mensaje de error que
incluya cédula, correo, teléfono o datos de la solicitud.

`hasRole('ROLE_X')` con el prefijo **no es un bug** en este repo: Security 6.5
no duplica el prefijo en `@PreAuthorize`. Solo hay que revisarlo si se sube a
Spring Boot 4 / Security 7.

### Tests

Un PR que arregla un bug debería traer el test que lo habría atrapado. Si el
test pasa igual con el bug reintroducido, no sirve.

## Documentacion
Una vez finalizado la feature revisar notion si ya existe documentacion hacerca de el feature actualizala, si no existe creela con casos de uso y diagramas de secuencia 
