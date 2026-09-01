# verYganar API

API en Spring Boot 3.5.3 / Java 21 / MySQL. Build con `./mvnw`.

## Build y tests

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw test
```

El build **exige JDK 21**. Con un JDK más nuevo por defecto en el sistema, Maven
falla; hay que exportar `JAVA_HOME` como arriba.

Los tests son unitarios con Mockito, sin base de datos. Las credenciales de BD
para correr la app viven en `.env`, nunca en el repo. Los seeds están en
`DataSeeder`; `ddl-auto` no borra datos.

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
