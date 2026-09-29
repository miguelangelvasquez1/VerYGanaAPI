# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# verYganar API

API en Spring Boot 3.5.3 / Java 21 / MySQL. Build con `./mvnw`. Paquete raíz
`com.verygana2`, clase principal `RifacelApplication`.

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

Los tests son unitarios con Mockito, sin base de datos ni `@SpringBootTest`. Los
tests de controller usan `MockMvcBuilders.standaloneSetup`: hay que fijar
`MappingJackson2HttpMessageConverter` con un `ObjectMapper` de
`Jackson2ObjectMapperBuilder`, o gana el converter de XML y el cuerpo del error se
pierde (ver `GameControllerPreviewAssetsTest`). Fixtures compartidos en
`src/test/java/com/verygana2/testsupport`.

Las credenciales de BD para correr la app viven en `.env`, nunca en el repo.

## Base de datos

- No hay Flyway. El esquema lo mantiene Hibernate con `ddl-auto: update` en dev y
  prod: agrega columnas y tablas, nunca borra ni renombra.
- Lo que `update` no puede hacer va en `src/main/resources/db/migration/` y se
  aplica a mano. Esos scripts deben ser idempotentes (MySQL no tiene
  `ADD COLUMN IF NOT EXISTS`; ver el patrón con `information_schema` en los
  existentes), porque las bases de dev ya pueden tener el cambio.
- Seeds: `DataSeeder` (`@Profile("dev")`) ejecuta los `.sql` de `db/seed/`.
  Otros initializers en `config/` cargan datos propios (p. ej. el cuestionario
  diagnóstico desde JSON).

## Arquitectura

Capas clásicas por paquete: `controllers` → `services` (interfaz en
`services/interfaces`, implementación `*ServiceImpl`) → `repositories` (Spring
Data JPA, filtros con `utils/specifications`) → `models`. DTOs en `dtos`,
mapeo con MapStruct en `mappers` (con DTOs `@Builder`, un `@AfterMapping` debe
recibir el builder como `@MappingTarget` o no se ejecuta). Cada capa se subdivide
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

Documentos de dominio en la raíz: `PAYOUT_SYSTEM.md`, `PQRS_SYSTEM.md`.

## Qué revisar en cada PR

Estas reglas salen de bugs que ya llegaron a `main`. No son teóricas.

### Configuración por perfiles

Una clave nueva debe existir en los tres archivos: `application.yml`,
`application-dev.yml` y `application-prod.yml`. Un `@Value` sin default que
falte en un perfil tumba el arranque de ese perfil, y no se nota hasta el
despliegue. Si un PR mueve un bloque de configuración entre archivos, verificar
que ningún perfil se quede sin la clave.

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

### Tests

Un PR que arregla un bug debería traer el test que lo habría atrapado. Si el
test pasa igual con el bug reintroducido, no sirve.
