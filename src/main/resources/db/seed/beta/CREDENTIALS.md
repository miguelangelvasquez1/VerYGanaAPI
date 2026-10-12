# Credenciales demo — perfil `beta` / `demo`

Credenciales de los usuarios creados por `beta_users.sql`.

Todos los usuarios utilizan la misma contraseña:

```text
Test1234!
```

El hash utilizado en el seed fue generado específicamente para esta contraseña y verificado con BCrypt. El proyecto sigue utilizando `BCryptPasswordEncoder` para la autenticación.

## Cómo cargar el seed

El seed se ejecuta con el perfil de Spring `beta` (o `demo`):

```text
SPRING_PROFILES_ACTIVE=beta
```

No se ejecuta con los perfiles `dev` ni `prod`.

---

## Staff

| Rol                | Email                                                                                             | Teléfono          | public_id                            |
| ------------------ | ------------------------------------------------------------------------------------------------- | ----------------- | ------------------------------------ |
| ADMIN              | [veryganaoficial@gmail.com](mailto:veryganaoficial@gmail.com)                                     | 3900000001        | 0be7a000-0001-0000-0000-000000000001 |
| GAME_DESIGNER      | [vgmiguel16+designer@gmail.com](mailto:vgmiguel16+designer@gmail.com)                             | 3001971366 (real) | 0be7a000-0002-0000-0000-000000000001 |
| COMPLIANCE_OFFICER | [juanpablorodriguezglab+compliance@gmail.com](mailto:juanpablorodriguezglab+compliance@gmail.com) | 3104206559 (real) | 0be7a000-0003-0000-0000-000000000001 |

---

## Comerciales

Hay un usuario comercial por cada plan disponible en el seed.

| Plan     | Empresa                      | Email                                                                     | Teléfono   | public_id                            |
| -------- | ---------------------------- | ------------------------------------------------------------------------- | ---------- | ------------------------------------ |
| BASIC    | Panadería Trigo Dorado S.A.S | [juanp.rodriguezg+commercial@uqvirtual.edu.co](mailto:juanp.rodriguezg+commercial@uqvirtual.edu.co) | 3900000004 | 0be7a000-0004-0000-0000-000000000001 |
| STANDARD | Moda Urbana Colombia S.A.S   | [comercial.standard@verygana.com](mailto:comercial.standard@verygana.com) | 3900000005 | 0be7a000-0004-0000-0000-000000000002 |
| PREMIUM  | Ecosistema Andino S.A.S      | [comercial.premium@verygana.com](mailto:comercial.premium@verygana.com)   | 3900000006 | 0be7a000-0004-0000-0000-000000000003 |

---

## Consumidores

Los primeros tres consumidores utilizan números reales del equipo para poder probar el flujo de verificación de Twilio.

Se crean inicialmente como `PENDING_VERIFICATION`. Después de completar la verificación por SMS pueden iniciar sesión normalmente.

Los otros siete consumidores se crean como `ACTIVE` para tener cuentas disponibles para las pruebas y demos.

| #  | Nombre                | Email                                                                                       | Teléfono          | Estado               | Municipio           | Edad | Género            | Nivel     | Avatar     | public_id                            |
| -- | --------------------- | ------------------------------------------------------------------------------------------- | ----------------- | -------------------- | ------------------- | ---- | ----------------- | --------- | ---------- | ------------------------------------ |
| 1  | Juan Pablo Mejía      | [juanp.mejiap1+consumer1@uqvirtual.edu.co](mailto:juanp.mejiap1+consumer1@uqvirtual.edu.co) | 3057472606 (real) | PENDING_VERIFICATION | Bogotá, D.C.        | 24   | MALE              | Bronce    | strawberry | 0be7a000-0005-0000-0000-000000000001 |
| 2  | Helen Giraldo         | [helen8335+consumer2@gmail.com](mailto:helen8335+consumer2@gmail.com)                       | 3219806868 (real) | ACTIVE | Medellín            | 29   | FEMALE            | Plata     | pineapple  | 0be7a000-0005-0000-0000-000000000002 |
| 3  | Nicolás Castro        | [jnch2005+consumer3@gmail.com](mailto:jnch2005+consumer3@gmail.com)                         | 3135911252 (real) | PENDING_VERIFICATION | Santiago de Cali    | 26   | MALE              | Oro       | blueberry  | 0be7a000-0005-0000-0000-000000000003 |
| 4  | María Fernanda Torres | [juanparodriguezg+consumer4@gmail.com](mailto:juanparodriguezg+consumer4@gmail.com)                                       | 3900000007        | ACTIVE               | Barranquilla        | 34   | FEMALE            | Rubí      | strawberry | 0be7a000-0005-0000-0000-000000000004 |
| 5  | Andrés Felipe Gómez   | [andres.felipe.gomez+consumer5@verygana.com](mailto:andres.felipe.gomez+consumer5@verygana.com)                             | 3900000008        | ACTIVE               | Cartagena de Indias | 41   | MALE              | Esmeralda | pineapple  | 0be7a000-0005-0000-0000-000000000005 |
| 6  | Camila Andrea Ruiz    | [miguela.vasquezg1+consumer6@uqvirtual.edu.co](mailto:miguela.vasquezg1+consumer6@uqvirtual.edu.co)       | 3900000009        | ACTIVE               | Bucaramanga         | 22   | FEMALE            | Diamante  | blueberry  | 0be7a000-0005-0000-0000-000000000006 |
| 7  | Santiago Herrera      | [juanpablorodriguezglab+consumer7@gmail.com](mailto:juanpablorodriguezglab+consumer7@gmail.com)           | 3900000010        | ACTIVE               | Pereira             | 55   | MALE              | Bronce    | strawberry | 0be7a000-0005-0000-0000-000000000007 |
| 8  | Valentina Ospina      | [valentina.ospina+consumer8@verygana.com](mailto:valentina.ospina+consumer8@verygana.com)           | 3900000011        | ACTIVE               | Manizales           | 19   | OTHER             | Plata     | pineapple  | 0be7a000-0005-0000-0000-000000000008 |
| 9  | Daniel Ricardo Peña   | [daniel.ricardo.pena+consumer9@verygana.com](mailto:daniel.ricardo.pena+consumer9@verygana.com)     | 3900000012        | ACTIVE               | San José de Cúcuta  | 47   | PREFER_NOT_TO_SAY | Oro       | blueberry  | 0be7a000-0005-0000-0000-000000000009 |
| 10 | Laura Sofía Restrepo  | [laura.sofia.restrepo+consumer10@verygana.com](mailto:laura.sofia.restrepo+consumer10@verygana.com) | 3900000013        | ACTIVE               | Ibagué              | 63   | FEMALE            | Rubí      | strawberry | 0be7a000-0005-0000-0000-00000000000a |

---

## Qué incluye `beta_users.sql`

Este seed crea los usuarios y la información básica necesaria para utilizarlos en las pruebas.

Incluye:

* `users`
* `user_details`
* `consumer_details`
* `commercial_details`
* `admin_details`
* `game_designer_details`
* `compliance_officer_details`
* `consumer_preferences`
* `user_level_profile`

Este archivo base no crea por sí solo:

* `wallets`
* `key_wallets`
* `commercial_documents`
* `commercial_contracts`

Las wallets se agregan en `beta_finance.sql`; `beta_campaigns_surveys.sql`
agrega el onboarding beta mínimo para los tres comerciales congelados. No se
fabrican contratos firmados ni documentos legales.

Los comerciales sí tienen asignado su `current_plan_id`, por lo que pueden utilizarse como referencias para las pruebas de BASIC, STANDARD y PREMIUM.

Los seeds `beta_campaigns_surveys.sql` y `beta_raffles_marketplace.sql` reutilizan
esos tres `public_id` comerciales; no crean cuentas ni reemplazan identidades.
El seed de marketplace deja un PIN ficticio `246810` para la compra física
pendiente de reclamo. Los códigos de inventario, entregas y premios son ejemplos
no canjeables fuera de beta y se cifran al iniciar la aplicación.

El sorteo finalizado con ganador es un fixture de demo y no una extracción de
Random.org. Las rifas que se sorteen en beta requieren que
`RANDOM_ORG_BETA_API_KEY` esté configurada en Infisical con la llave de prueba,
distinta de `RANDOM_ORG_API_KEY` de producción.

`beta_finance.sql` crea wallets de llaves para los consumidores, movimientos
`CREDIT_INTERACTION` de los anuncios, encuestas completadas y una partida de
Trivia, además de reconciliar los saldos con esos movimientos. Los importes son
centavos COP; 1 llave equivale a `financial.key-value-cents` (1000 centavos en
dev). También incluye saldos de presupuesto comerciales y un payout ficticio
del producto digital. Los registros Wompi llevan `BETA_SEED_FIXTURE` en metadata:
son datos locales, no hubo cobro ni transferencia real.

La campaña de Trivia `970001` también queda ligada a una solicitud de brandeo
de demostración para que su detalle devuelva la marca y el objetivo. Las
interacciones financieras ficticias del seed se marcan como ya conciliadas;
el scheduler no debe tratarlas como emisiones nuevas de tesorería.

`beta_pqrs_notifications.sql` agrega dos casos abiertos asignados al admin beta,
dos casos resueltos/cerrados con respuesta y notificaciones no leídas para
consumidores activos. Se puede comprobar la bandeja con `GET /notifications`,
los casos propios con `GET /pqrs/mine` y la cola del administrador con
`GET /admin/pqrs`.

Para añadir una marca con una cuenta comercial nueva, registrarla mediante
`POST /auth/register/commercial` y completar el flujo
`/commercials/onboarding/...` antes de exportar los datos resultantes. El
onboarding mínimo de los comerciales congelados es solo un atajo de demo; no
sustituye el flujo real ni crea contratos firmados para esas cuentas.

---

## Nota sobre los seeds de test

Ese seed no fue modificado porque corresponde al entorno de test/dev y no forma parte de este seed BETA.

Si se van a utilizar esos usuarios para iniciar sesión, conviene revisar y corregir sus credenciales por separado.
