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
- Ver los preauthorize
- Implementar redis en vez de caché?
- Usar OffsetDateTime
- Añadir caché de usuarios?
- Mirar lo del cache de categorias
- https://www.datos.gov.co/api/v3/views/gdxc-w37w/export.csv?accessType=DOWNLOAD&app_token=bHWsGtRFRP9x8Hl8lYivqM1hQ -> Municipalitys and Departments

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

- cuando un usuario se inactiva se cierra la sesion
- validar que cuando un commercial activa un anuncio ya haya sido activado por el admin
- manejar errores de back a front
- si state devulve que ya tiene max ads bloquear el creacion de ads



- ver campo por campo del diagnostico, ver lo de firmas.
- ajustar como se clasifica al usuario y a que plan se asigna, ruta especial para proveedores y E y PEP(bandera para el compliance). se requiere contacto? ver campos de diagnostico, mensualidades? etc. archivos requeridos?(preguntar a nestor), ver documentos en el compliance -> correo y mensaje especial.
- que pasa con el dinero cuando se cierra una encuesta? (que no se pueda cerrar?)
- flujo de jugar, metricas, casos de juego, etc.
- si un commercial cambia a plan mas bajo que no se devuelva lo creado
- ver todo despues de haber preguntado a nestor(contratos).

- probar max_ads, flujo de sesiones y recompensa bien revisar.

- ver que los asstes se borren del cdn.
- ver que se puede editar o no despues de los contratos


- actuator (backend metrics)?


Backend — Flujo de registro comercial extendido
Nueva entidad CommercialOnboarding (1:1 con User, tabla commercial_onboarding) que registra el progreso a través de 5 pasos, más los enums de soporte en models/enums/commercial/.

Paso 1 (Registro básico): POST /auth/register/commercial sigue igual (crea automáticamente la fila de onboarding en TERMS_PENDING), pero ⚠️ FRONT: se quitó `municipalityCode` del body — ese dato ya no se pide/usa en este paso, se captura en el paso 3 (municipio del domicilio, ver abajo).
Paso 2 (T&C): POST /commercials/onboarding/terms — recibe versión, URL del PDF (que vive en el .env del frontend) y accepted=true; el backend registra fecha/hora, IP y User-Agent del servidor (no confía en el cliente para eso).
Paso 3 (Identificación jurídica): POST /commercials/onboarding/legal-identification — persona natural/jurídica, razón social, NIT, representante legal, actividad económica, domicilio, municipio. ⚠️ FRONT (cambio de contrato): `legalRepFullName` se eliminó del body — ahora van dos campos separados, `legalRepFirstName` y `legalRepLastName` (ambos requeridos). Además `companyName` dejó de ser obligatorio en el body: para persona jurídica (`personType=JURIDICA`) sigue siendo requerida (si no viene, el backend responde 422 pidiéndola); para persona natural (`personType=NATURAL`) es opcional — si no se envía, el backend la completa automáticamente con "{legalRepFirstName} {legalRepLastName}".
Paso 4 (Diagnóstico comercial): POST /commercials/onboarding/diagnostic — las 10 preguntas (Q3-Q12), devuelve directamente la clasificación calculada.
Paso 5 (Clasificación): GET /commercials/onboarding/classification (para releer antes de confirmar) y POST /commercials/onboarding/classification/confirm (finaliza el onboarding).
Paso 6-7 (Plan y resumen económico): GET /commercials/onboarding/plan y POST /commercials/onboarding/plan/accept. ⚠️ FRONT (cambio de contrato, ver prompt abajo): GET ya no devuelve un solo plan — devuelve el catálogo completo (BASIC/STANDARD/PREMIUM) para armar tabla comparativa, marcando cuál es el recomendado. POST ahora requiere `{ "planCode": "BASIC" | "STANDARD" | "PREMIUM", "investmentAmountCents": number }` en el body — el empresario puede elegir cualquiera, no solo el recomendado; `investmentAmountCents` es requerido para STANDARD/PREMIUM (debe caer dentro del rango del plan) y se ignora para BASIC.
Paso 10 (Revisión del empresario): ⚠️ FRONT — POST /commercials/onboarding/contract/request-changes dejó de devolver 204 sin body; ahora responde 200 con el `OnboardingStep` (string) al que quedó el onboarding, para redirigir directo a ese paso sin tener que volver a pedir /status. ⚠️ FRONT (cambio de contrato adicional): este endpoint ahora solo funciona con `currentStep === "CONTRACT_PENDING"` (mientras se revisa el resumen, GET /commercials/onboarding/summary, ANTES de generar el contrato) — 422 si se llama con el contrato ya generado (BUSINESS_REVIEW_PENDING en adelante). El botón "pedir cambios" debe vivir en la pantalla de resumen, no en la de revisión del contrato ya generado.
Paso 11 (Rechazo de VERYGANA): ⚠️ FRONT — GET /commercials/onboarding/status ahora incluye `rejectionReason` y `rejectedAt` (ambos null salvo que `contractStatus === "REJECTED"`) para que, sin importar a qué paso los redirijan, siempre puedan mostrarle al empresario el motivo del rechazo (modal + banner, ver prompt detallado). `rejectedAt` sirve de llave para no repetir el modal en cada navegación. Decisión de producto: el único autoservicio habilitado tras un rechazo es corregir documentos — POST /compliance/contracts/{id}/reject ahora requiere `documentsIssue` (boolean) además de `reason`; si es true, el onboarding vuelve a DOCUMENTS_PENDING (documentsCompleted pasa a false); si es false, el onboarding NO se reabre (currentStep no cambia) y el front debe mostrar que VERYGANA contactará directamente al empresario.
Paso 8 (Carga documental): ⚠️ FRONT — nuevo endpoint POST /commercials/onboarding/documents/continue. El avance de DOCUMENTS_PENDING → CONTRACT_PENDING dejó de ser automático al subir/descartar el último documento requerido; ahora es explícito vía este endpoint (necesita un botón "Continuar" en el tab de Documentos). Ver detalle abajo.
⚠️ FRONT — nuevo endpoint GET /commercials/onboarding/summary: resumen de solo lectura de todo lo capturado en los pasos 2-8 (T&C, identificación jurídica, diagnóstico, clasificación, plan, documentos). Pensado para mostrarse como pantalla de revisión ANTES de POST /commercials/onboarding/contract/generate, para que el comercial no tenga que generar un PDF nuevo solo para ver/verificar sus datos antes de decidir si pide cambios. Ver detalle abajo.
Paso 11 (Panel de compliance): ⚠️ FRONT (cambio de contrato) — GET /compliance/contracts/{contractId} (y también generate/approve/reject/getCurrent, que comparten el mismo DTO) ahora incluye un array `documents` con los documentos (no descartados) del comercial dueño del contrato — no hace falta un segundo endpoint. Ver detalle abajo.
Paso 11 (Panel de compliance) — ⚠️ FRONT (cambio de contrato de ruta): GET /compliance/contracts/pending se eliminó — ahora es GET /compliance/contracts (sin `/pending`), con un query param opcional `?status=` (PENDING_VERYGANA_REVIEW | APPROVED | REJECTED). Sin el query param trae los 3 estados juntos (no incluye PENDING_BUSINESS_REVIEW, que aún no llega a VERYGANA). Cada fila del listado (ContractReviewListItemDTO) ahora incluye `status` y `veryganaReviewedAt`. Ver detalle abajo.
Estado: GET /commercials/onboarding/status.
Reglas de clasificación A-E (propuesta que aprobaste, aislada en CommercialOnboardingServiceImpl.classify() para ajustarla fácilmente): negociación especial→E, sector regulado→D, requiere integración técnica o juegos personalizados→C, tarifa fija sin comisión→A, resto (comisión por venta estándar)→B.
Nuevas piezas clave:

CommercialOnboarding extendida con snapshot de plan/condiciones económicas + documentsCompletedAt.
CommercialDocument (entidad + tabla commercial_documents) para la carga documental, reutilizando el mismo patrón de subida que ya usan los anuncios: prepare-upload (URL pre-firmada a R2) → confirm (validación real de tamaño/MIME con Tika) → discard. Añadí soporte de PDF (SupportedMimeType.APPLICATION_PDF, MediaType.DOCUMENT) que no existía.
CommercialContract (entidad + tabla commercial_contracts) con su ciclo PENDING_BUSINESS_REVIEW → PENDING_VERYGANA_REVIEW → APPROVED/REJECTED.
Generación real de PDF: agregué la dependencia openhtmltopdf-pdfbox (no existía ninguna librería de PDF en el proyecto), una plantilla HTML en templates/contracts/contrato-marco.html (mismo estilo {{var}} que ya usan los emails) y un ContractPdfRenderer.
ComplianceContractController (/compliance/contracts, rol ROLE_COMPLIANCE_OFFICER) para el paso 11, calcado del patrón ya usado en ComplianceKycController.
Decisiones de negocio que tomé por ti (documentadas y fácilmente ajustables):

Mapeo Ruta → Plan: A→BASIC, B→STANDARD, C/D/E→PREMIUM, con un flag requiresAdvisorContact=true en D/E (aislado en resolvePlanForRoute()).
El "aceptar el plan" no activa el pago real — sigue existiendo el flujo actual de Wompi (checkout/webhook) para activar CommercialDetails.currentPlan; aceptar el plan en el onboarding solo congela un snapshot de las condiciones para el contrato. Evité tocar la lógica de pagos existente.
La plantilla del contrato es un esqueleto, no texto legal validado — está marcada explícitamente como "[Placeholder legal — pendiente de validación jurídica]" en el propio PDF. Necesita revisión de tu equipo jurídico antes de producción.
Campos jurídicos (paso 3) quedan bloqueados tras el primer envío; diagnóstico/plan/documentos (pasos 4, 6-8) se pueden corregir hasta que el contrato entra en revisión del empresario, punto desde el cual solo se reabren vía POST /contract/request-changes.


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