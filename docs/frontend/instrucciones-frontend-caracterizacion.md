# Instrucciones de implementación — Frontend

## Ficha tributaria, compromiso de formalización, expediente y requerimientos

> **Para:** agente de frontend de VerYGana.
> **Backend:** ya implementado y probado. No inventes endpoints ni campos: usa exactamente los de este documento.
> **Roles:** `COMMERCIAL` (empresario) y `COMPLIANCE_OFFICER` (oficial de cumplimiento).

---

## 0. Antes de empezar

### 0.1 Contexto de negocio en una línea por tema

| Tema | Qué es | Regla que no se puede romper |
|---|---|---|
| Ficha tributaria | Información tributaria **declarada** por el empresario (incisos a–h del contrato). Cada envío es una versión. | La ficha no cambia la vocación, la comisión ni el plan. |
| Establecimientos | Sedes físicas del empresario. | No se borran: se cierran con motivo. Una URL o red social no es una sede. |
| Expediente documental | Documentos del empresario con historial, vencimiento y revisión. | No se borran: se reemplazan y el anterior queda en el historial. |
| Compromiso de formalización | Declaración legal que el empresario manifiesta **una sola vez**, con una casilla, al aprobar el contrato. | Casilla **siempre desmarcada** al inicio. Solo en la aprobación del contrato. Nunca bloquea la aprobación. |
| Requerimientos | Solicitudes concretas de VERYGANA al empresario (documento, dato o aclaración). | Responder no cierra la solicitud; la cierra compliance al revisar. |
| Hallazgos | Decisión de compliance con fundamento. Es lo único que puede restringir o suspender una cuenta. | Pedir confirmación explícita antes de aplicar una medida. |
| Alertas | Avisos informativos para compliance. | Son informativas: no sancionan ni cambian nada. |

### 0.2 Convenciones de la API

- **Base URL y autenticación:** las mismas de siempre. El JWT viaja en `Authorization: Bearer …`.
- **IDs:** los de usuarios y empresarios son `publicId` (UUID). Los de documentos, establecimientos, alertas, reglas y entradas de catálogo son numéricos. Los de requerimientos y hallazgos son UUID (`publicId`).
- **Fechas:**
  - `LocalDate` llega como `"YYYY-MM-DD"`.
  - Las fechas con hora llegan en ISO-8601; muéstralas en hora de Colombia.
- **Dinero:** siempre en **centavos** (`*Cents`). Para mostrar, divide por 100 y formatea en COP. Para enviar, multiplica por 100 y redondea a entero.
- **Paginación:**
  ```ts
  interface PagedResponse<T> {
    data: T[];
    meta: { page: number; size: number; totalElements: number; totalPages: number;
            hasNext: boolean; hasPrevious: boolean; sorted: boolean };
  }
  ```
  Query params: `page` (empieza en 0), `size`, `sort` (`campo,asc|desc`).
- **Errores:**
  ```ts
  interface ApiError {
    status: number;
    error: string;
    message: string;               // texto en español listo para mostrar
    timestamp: string;
    path: string;
    details?: Record<string, string>; // errores de validación por campo (400)
  }
  ```
  - **400:** muestra `message` en un toast o alerta. Si hay `details`, ponlos bajo cada campo del formulario: la clave es el nombre del campo del DTO, por ejemplo `currentCapacity` o `activities[0].ciiuCode`.
  - **403:** sin permiso.
  - **404:** no encontrado.
  - El backend valida todas las reglas de negocio. Valida en el front para mejorar la experiencia, pero **muestra siempre el mensaje del backend** cuando rechace algo.
- **204:** `GET /commercials/tax-profile` responde 204 **sin cuerpo** si el empresario aún no envió la ficha. Trátalo como "no existe" (`null`), no como error.

### 0.3 Archivos que vas a crear (ajusta a la estructura del proyecto)

```
types/
  taxProfile.ts
  expediente.ts
  declarations.ts
  informationRequests.ts
api/
  taxProfile.api.ts
  expediente.api.ts
  declarations.api.ts
  informationRequests.api.ts
  compliance.taxProfile.api.ts
  compliance.informationRequests.api.ts
features/commercial/
  onboarding/TaxProfileStep
  onboarding/ContractApproval (modificar la existente)
  profile/TaxProfilePage
  profile/EstablishmentsPage
  profile/ExpedientePage
  profile/InformationRequestsPage
features/compliance/
  taxProfiles/TaxProfilesQueuePage
  taxProfiles/TaxProfileReviewPage
  commercials/CommercialCasePage
  taxCatalog/TaxCatalogPage
  informationRequests/InformationRequestsQueuePage
  informationRequests/InformationRequestDetailPage
  informationRequests/CreateInformationRequestForm
  findings/FindingForm
  alerts/AlertsPage
  alerts/AlertRulesPage
shared/
  FileUploader (reutilizar el del onboarding)
  StatusBadge
  ConfirmDialog
```

---

## 1. Tipos (TypeScript)

Cópialos tal cual. Los nombres de campos coinciden con el JSON del backend.

### 1.1 `types/taxProfile.ts`

```ts
export type TaxProfileStatus = 'SUBMITTED' | 'OBSERVED' | 'REVIEWED';
export type TaxProfileSection =
  | 'A_RUT' | 'B_REGIME_ACTIVITIES' | 'C_VAT_INC' | 'D_INVOICING_WITHHOLDING'
  | 'E_INCOME' | 'F_ESTABLISHMENTS' | 'G_GROWTH_CAPACITY' | 'H_ADVISORY';
export type SectionReviewResult = 'VERIFIED' | 'OBSERVED' | 'NOT_APPLICABLE';
export type IncomeConcept = 'INGRESOS' | 'VENTAS' | 'RECAUDOS';
export type IncomeScope = 'EXTERNAL_ONLY' | 'PLATFORM_ONLY' | 'CONSOLIDATED_INCLUDES_PLATFORM';
export type TaxCatalogType = 'REGIME' | 'RUT_RESPONSIBILITY' | 'VAT_CONDITION' | 'INC_CONDITION' | 'INVOICING_METHOD';
export type EstablishmentRelation = 'OWNED' | 'LEASED' | 'FRANCHISE' | 'OTHER';
export type EstablishmentStatus = 'ACTIVE' | 'CLOSED';
export type CommercialActivityType = 'PRODUCTS' | 'SERVICES';

// ---------- Peticiones ----------
export interface ActivityInput {
  ciiuCode: string;          // exactamente 4 dígitos
  description?: string;      // máx 300
  main: boolean;             // exactamente una en true
}

export interface IncomeReportInput {
  concept: IncomeConcept;
  periodStart: string;       // YYYY-MM-DD
  periodEnd: string;         // YYYY-MM-DD, >= periodStart y <= hoy
  amountCents: number;       // >= 0
  currency: string;          // 3 letras mayúsculas, por defecto 'COP'
  scope: IncomeScope;
  supportDocumentId?: number | null;
  notes?: string;            // máx 500
}

export interface TaxProfileSubmitRequest {
  // a) RUT
  rutDocumentId?: number | null;
  rutResponsibilityCodes: string[];            // códigos RUT_RESPONSIBILITY, sin repetir
  // b) Régimen y actividades
  regimeCode?: string | null;                  // código REGIME
  activities: ActivityInput[];                 // mínimo 1
  // c) IVA e INC
  vatConditionCode?: string | null;            // código VAT_CONDITION
  incConditionCode?: string | null;            // código INC_CONDITION
  taxConditionScope?: string;                  // máx 1000
  // d) Facturación y retención
  invoicingObligated: boolean;
  invoicingMethodCode?: string | null;         // código INVOICING_METHOD
  invoicingNotes?: string;                     // máx 1000
  withholdingAgent: boolean;
  withholdingNotes?: string;                   // máx 1000
  // e) Ingresos o ventas
  incomeReports: IncomeReportInput[];          // mínimo 1
  // g) Capacidad de crecimiento
  currentCapacity: string;                     // obligatorio, máx 2000
  growthScenario?: string;                     // máx 2000
  growthMeasures?: string;                     // máx 2000
  // h) Asesoría
  hasAccountingAdvisor: boolean;
  advisorScope?: string;                       // máx 1000
}

export interface EstablishmentRequest {
  name: string;                // máx 150
  address: string;             // máx 300, dirección física
  municipalityCode: string;    // código DANE de 5 dígitos
  relation: EstablishmentRelation;
}
export interface CloseEstablishmentRequest { reason: string } // obligatorio, máx 300

export interface SectionReviewRequest { result: SectionReviewResult; notes?: string } // notes obligatorio si OBSERVED

export interface CatalogEntryRequest {
  catalogType: TaxCatalogType;
  code: string;          // máx 30
  label: string;         // máx 200
  legalBasis: string;    // máx 500, obligatorio
  validFrom: string;     // YYYY-MM-DD
}
export interface RetireCatalogEntryRequest { validTo: string } // YYYY-MM-DD

// ---------- Respuestas ----------
export interface Activity { ciiuCode: string; description: string | null; main: boolean }
export interface IncomeReport {
  concept: IncomeConcept; periodStart: string; periodEnd: string; amountCents: number;
  currency: string; scope: IncomeScope; supportDocumentId: number | null; notes: string | null;
}
export interface TaxProfileVersionDetail {
  versionNumber: number;
  submittedAt: string;
  rutDocumentId: number | null;
  rutResponsibilityCodes: string[];
  regimeCode: string | null;
  activities: Activity[];
  vatConditionCode: string | null;
  incConditionCode: string | null;
  taxConditionScope: string | null;
  invoicingObligated: boolean;
  invoicingMethodCode: string | null;
  invoicingNotes: string | null;
  withholdingAgent: boolean;
  withholdingNotes: string | null;
  incomeReports: IncomeReport[];
  establishmentsCount: number;   // activos al momento del envío
  currentCapacity: string;
  growthScenario: string | null;
  growthMeasures: string | null;
  hasAccountingAdvisor: boolean;
  advisorScope: string | null;
}
export interface SectionReview {
  section: TaxProfileSection; result: SectionReviewResult; notes: string | null; reviewedAt: string;
}
export interface Establishment {
  id: number; name: string; address: string; municipalityCode: string; municipalityName: string;
  relation: EstablishmentRelation; status: EstablishmentStatus; createdAt: string;
  closedAt: string | null; closedReason: string | null;
}
export interface TaxProfileResponse {
  status: TaxProfileStatus;
  currentVersion: number;
  updatedAt: string;
  version: TaxProfileVersionDetail;
  sectionReviews: SectionReview[];             // una por sección revisada; si falta, está pendiente
  activeEstablishments: Establishment[];
  formalizationCommitment: Manifestation | null; // de types/declarations.ts
}
export interface VersionSummary { versionNumber: number; submittedAt: string }
export interface CatalogEntry {
  id: number; catalogType: TaxCatalogType; code: string; label: string; legalBasis: string;
  validFrom: string; validTo: string | null; active: boolean;
}
// Compliance
export interface TaxProfileSummary {
  commercialPublicId: string; companyName: string; status: TaxProfileStatus;
  currentVersion: number; updatedAt: string;
}
export interface ComplianceTaxProfileResponse {
  commercialPublicId: string;
  companyName: string;
  nit: string;
  commercialActivityType: CommercialActivityType | null; // solo para contrastar
  profile: TaxProfileResponse;
  versions: VersionSummary[];
  establishmentHistory: Establishment[];                 // activos y cerrados
}
```

### 1.2 `types/expediente.ts`

```ts
export type CommercialDocumentType =
  | 'RUT' | 'CAMARA_COMERCIO' | 'CEDULA_REPRESENTANTE' | 'CERTIFICACION_BANCARIA'
  | 'PERMISO_SECTORIAL' | 'MARCA_REGISTRADA' | 'OTRO';
export type CommercialDocumentStatus = 'PENDING' | 'VALIDATED' | 'ORPHANED' | 'SUPERSEDED';
export type CommercialDocumentReviewStatus = 'NOT_REVIEWED' | 'ACCEPTED' | 'OBSERVED' | 'REJECTED';

export interface ExpedienteDocumentUploadRequest {
  documentType: CommercialDocumentType;
  originalFileName: string;
  contentType: 'application/pdf' | 'image/jpeg' | 'image/png';
  sizeBytes: number;                 // tamaño exacto del archivo
  replacesDocumentId?: number | null; // id del documento VIGENTE del mismo tipo que se reemplaza
}
export interface FileUploadPermission { uploadUrl: string; expiresInSeconds: number }
export interface DocumentUploadPermission { documentId: number; permission: FileUploadPermission }

export interface ExpedienteDocument {
  id: number;
  documentType: CommercialDocumentType;
  originalFileName: string;
  sizeBytes: number;
  status: CommercialDocumentStatus;   // VALIDATED = vigente, SUPERSEDED = historial
  uploadedAt: string;
  replacesDocumentId: number | null;
  supersededAt: string | null;
  reviewStatus: CommercialDocumentReviewStatus | null; // null en documentos antiguos = sin revisar
  reviewedAt: string | null;
  reviewNotes: string | null;
  holderIdentification: string | null;
  issuedAt: string | null;
  expiresAt: string | null;           // null = sin vencimiento
  expired: boolean;
  downloadUrl: string | null;         // válida 5 minutos
}

// Compliance
export interface DocumentReviewRequest {
  result: 'ACCEPTED' | 'OBSERVED' | 'REJECTED';
  holderIdentification?: string;  // obligatorio para ACCEPTED en RUT, CAMARA_COMERCIO, CERTIFICACION_BANCARIA, CEDULA_REPRESENTANTE
  issuedAt?: string;              // YYYY-MM-DD
  expiresAt?: string;             // YYYY-MM-DD, >= issuedAt; vacío si no tiene vigencia comprobable
  notes?: string;                 // obligatorio si OBSERVED o REJECTED, máx 1000
}
```

### 1.3 `types/declarations.ts`

```ts
export type DeclarationCode = 'FORMALIZATION_COMMITMENT';
export type ManifestationStatus = 'MANIFESTED' | 'NOT_MANIFESTED' | 'EXPRESS_REFUSAL' | 'TECHNICAL_INCIDENT';
export type ManifestationDecision = 'ACCEPT' | 'REFUSE';
export type DisclosureContext = 'TAX_PROFILE' | 'CONTRACT_SHEET';

export interface Manifestation {
  id: number;
  status: ManifestationStatus;
  declarationVersion: string;
  contractId: number;
  contractVersion: number;
  occurredAt: string;
  detail: string | null;          // motivo de la negativa o descripción de la falla
  actorLegalRepName: string | null;
}
export interface DeclarationView {
  code: DeclarationCode;
  version: string;
  fullText: string;               // mostrar LITERAL y completo
  textHash: string;
  manifestation: Manifestation | null;
}
export interface ContractApproveRequest {
  declarationVersion?: string;    // la `version` que se mostró
  decision?: ManifestationDecision | null; // null/omitido = no marcó la casilla
  refusalReason?: string;         // máx 1000, solo con REFUSE
}
```

### 1.4 `types/informationRequests.ts`

```ts
export type InformationRequestTrigger =
  | 'DOCUMENT_EXPIRED' | 'ACTIVITY_CHANGE' | 'ESTABLISHMENT_CHANGE' | 'MATERIAL_OPERATION_INCREASE'
  | 'NEW_REQUIREMENT' | 'INCONSISTENCY' | 'INCIDENT' | 'RISK' | 'MISSING_MANIFESTATION' | 'EXPRESS_REFUSAL';
export type InformationRequestStatus =
  | 'OPEN' | 'RESPONDED' | 'OBSERVED' | 'CLOSED_SATISFIED' | 'CLOSED_WITH_FINDING' | 'CANCELLED';
export type RequestItemType = 'FIELD' | 'DOCUMENT' | 'CLARIFICATION';
export type RequestReviewResult = 'SUFFICIENT' | 'INSUFFICIENT';
export type FindingClassification =
  | 'SUBSANABLE_ERROR' | 'LACK_OF_UPDATE' | 'ISOLATED_INCONSISTENCY' | 'VERIFIED_FALSE_INFO' | 'EXPRESS_REFUSAL';
export type FindingSeverity = 'LOW' | 'MEDIUM' | 'HIGH';
export type ContractualMeasure = 'NONE' | 'WARNING' | 'PREVENTIVE_RESTRICTION' | 'SUSPENSION' | 'TERMINATION_REFERRAL';
export type AccountStatus =
  | 'REGISTRATION_STARTED' | 'PENDING_ACCEPTANCE' | 'PENDING_VERIFICATION' | 'PENDING_ACTIVATION'
  | 'ACTIVE' | 'PREVENTIVELY_RESTRICTED' | 'SUSPENDED' | 'TERMINATED';
export type AlertRuleType = 'PLATFORM_SALES_GROWTH';

export interface RequestItem {
  itemType: RequestItemType;
  documentType: CommercialDocumentType | null;
  fieldPath: string | null;
  establishmentId: number | null;
  description: string;
  acceptableResponse: string | null;
  linkedDocumentId: number | null;   // documento que ya existe en el expediente
}
export interface RequestResponse { message: string | null; documentIds: number[]; receivedAt: string }
export interface RequestReview { result: RequestReviewResult; notes: string | null; reviewedAt: string }
export interface RequestCommunication {
  channel: 'IN_APP' | 'EMAIL'; sentAt: string; deliveryStatus: 'SENT' | 'FAILED'; errorDetail: string | null;
}
export interface InformationRequest {
  publicId: string;
  commercialPublicId: string;
  companyName: string;
  trigger: InformationRequestTrigger;
  ruleCode: string | null;
  ruleVersion: number | null;
  reason: string;
  relationToOperation: string;
  status: InformationRequestStatus;
  dueDate: string | null;
  issuedBySystem: boolean;
  issuedAt: string;
  closedAt: string | null;
  closingNotes: string | null;
  items: RequestItem[];
  responses: RequestResponse[];
  reviews: RequestReview[];
  communications: RequestCommunication[]; // vacío en la vista del empresario
}

// Empresario
export interface RespondRequest { message?: string; documentIds?: number[] } // al menos uno de los dos

// Compliance
export interface ItemInput {
  itemType: RequestItemType;
  documentType?: CommercialDocumentType;   // obligatorio si DOCUMENT
  fieldPath?: string;                       // obligatorio si FIELD, máx 100
  establishmentId?: number;
  description: string;                      // obligatorio, máx 500
  acceptableResponse?: string;              // máx 500
}
export interface CreateInformationRequest {
  commercialPublicId: string;
  trigger: InformationRequestTrigger;
  reason: string;               // obligatorio, máx 1000
  relationToOperation: string;  // obligatorio, máx 1000
  dueDate?: string | null;
  items: ItemInput[];           // mínimo 1
  alertId?: number | null;
}
export interface ReviewInformationRequest { result: RequestReviewResult; notes?: string } // notes obligatorio si INSUFFICIENT
export interface CancelInformationRequest { reason: string }

export interface FindingRequest {
  commercialPublicId: string;
  requestPublicId?: string | null;
  factObserved: string;         // obligatorio, máx 2000
  evidence?: string;            // máx 2000
  classification: FindingClassification;
  severity: FindingSeverity;
  impact: string;               // obligatorio, máx 1000
  remediable: boolean;
  contractualBasis: string;     // obligatorio, máx 1000
  measure: ContractualMeasure;
  measureScope?: string;        // máx 500
}
export interface Finding {
  publicId: string; requestPublicId: string | null; factObserved: string; evidence: string | null;
  classification: FindingClassification; severity: FindingSeverity; impact: string; remediable: boolean;
  contractualBasis: string; measure: ContractualMeasure; measureScope: string | null;
  accountStatusApplied: AccountStatus | null; decidedAt: string;
}

export interface IncidentRequest {
  commercialPublicId: string; context: string; description: string; requestPublicId?: string | null;
}
export interface Incident {
  id: number; context: string; description: string; requestPublicId: string | null;
  manifestationId: number | null; reportedBySystem: boolean; occurredAt: string;
}

export interface AlertRuleRequest {
  code: string;                 // /^[A-Z0-9_]{3,50}$/
  ruleType: AlertRuleType;
  description: string;          // máx 500
  legalBasis: string;           // máx 500
  windowDays: number;           // >= 7
  minGrowthPct: number;         // >= 0.01
  minCurrentSalesCents: number; // >= 0
}
export interface AlertRule {
  id: number; code: string; version: number; ruleType: AlertRuleType; description: string;
  legalBasis: string; paramsJson: string; active: boolean; createdAt: string;
}
export interface Alert {
  id: number; ruleCode: string | null; ruleVersion: number | null; trigger: InformationRequestTrigger;
  commercialPublicId: string; companyName: string; detectedAt: string; dataUsedJson: string;
  message: string; reviewedAt: string | null; reviewNotes: string | null; requestPublicId: string | null;
}
export interface AlertReviewRequest { notes: string } // obligatorio, máx 1000
```

> **Cambio en un tipo existente:** agrega `'TAX_PROFILE_PENDING'` al tipo `OnboardingStep`, entre `'DOCUMENTS_PENDING'` y `'CONTRACT_PENDING'`.

---

## 2. Clientes de API

Un archivo por grupo, usando el cliente HTTP que ya tiene el proyecto. Firmas sugeridas:

```ts
// api/taxProfile.api.ts  (COMMERCIAL)
getTaxProfile(): Promise<TaxProfileResponse | null>                 // GET  /commercials/tax-profile   (204 → null)
submitTaxProfile(body: TaxProfileSubmitRequest): Promise<TaxProfileResponse> // PUT /commercials/tax-profile
listTaxProfileVersions(): Promise<VersionSummary[]>                  // GET  /commercials/tax-profile/versions
getTaxProfileVersion(n: number): Promise<TaxProfileVersionDetail>    // GET  /commercials/tax-profile/versions/{n}
getTaxCatalog(): Promise<CatalogEntry[]>                             // GET  /commercials/tax-profile/catalog
listEstablishments(): Promise<Establishment[]>                       // GET  /commercials/establishments
createEstablishment(body: EstablishmentRequest): Promise<Establishment> // POST /commercials/establishments
closeEstablishment(id: number, body: CloseEstablishmentRequest): Promise<Establishment> // POST /commercials/establishments/{id}/close

// api/expediente.api.ts  (COMMERCIAL)
listExpediente(): Promise<ExpedienteDocument[]>                      // GET  /commercials/documents
prepareExpedienteUpload(body: ExpedienteDocumentUploadRequest): Promise<DocumentUploadPermission> // POST /commercials/documents/prepare-upload
confirmExpedienteUpload(documentId: number): Promise<ExpedienteDocument> // POST /commercials/documents/{documentId}/confirm

// api/declarations.api.ts  (COMMERCIAL)
getDeclaration(code: DeclarationCode, context: DisclosureContext): Promise<DeclarationView>
  // GET /commercials/declarations/{code}?context=CONTRACT_SHEET|TAX_PROFILE
approveContract(body: ContractApproveRequest, idempotencyKey: string): Promise<ContractSummary>
  // POST /commercials/onboarding/contract/approve  + header Idempotency-Key  (REEMPLAZA la llamada actual sin cuerpo)

// api/informationRequests.api.ts  (COMMERCIAL)
listMyInformationRequests(): Promise<InformationRequest[]>           // GET  /commercials/information-requests
getMyInformationRequest(id: string): Promise<InformationRequest>     // GET  /commercials/information-requests/{id}
respondInformationRequest(id: string, body: RespondRequest): Promise<InformationRequest> // POST /commercials/information-requests/{id}/responses

// api/compliance.taxProfile.api.ts  (COMPLIANCE_OFFICER)
listTaxProfiles(params: { status?: TaxProfileStatus; page?: number; size?: number }): Promise<PagedResponse<TaxProfileSummary>> // GET /compliance/tax-profiles
getComplianceTaxProfile(commercialPublicId: string): Promise<ComplianceTaxProfileResponse> // GET /compliance/tax-profiles/{id}
getComplianceTaxProfileVersion(commercialPublicId: string, n: number): Promise<TaxProfileVersionDetail> // GET /compliance/tax-profiles/{id}/versions/{n}
reviewTaxProfileSection(commercialPublicId: string, section: TaxProfileSection, body: SectionReviewRequest): Promise<ComplianceTaxProfileResponse>
  // POST /compliance/tax-profiles/{id}/sections/{section}/review
listCommercialDocuments(commercialPublicId: string): Promise<ExpedienteDocument[]> // GET /compliance/commercials/{id}/documents
reviewDocument(documentId: number, body: DocumentReviewRequest): Promise<ExpedienteDocument> // POST /compliance/commercial-documents/{documentId}/review
getDeclarationHistory(commercialPublicId: string): Promise<Manifestation[]> // GET /compliance/commercials/{id}/declarations/FORMALIZATION_COMMITMENT
listTaxCatalog(): Promise<CatalogEntry[]>                            // GET  /compliance/tax-catalog
createCatalogEntry(body: CatalogEntryRequest): Promise<CatalogEntry> // POST /compliance/tax-catalog
retireCatalogEntry(id: number, body: RetireCatalogEntryRequest): Promise<CatalogEntry> // POST /compliance/tax-catalog/{id}/retire

// api/compliance.informationRequests.api.ts  (COMPLIANCE_OFFICER)
searchInformationRequests(params: { status?: InformationRequestStatus; commercialPublicId?: string; page?: number; size?: number }): Promise<PagedResponse<InformationRequest>>
  // GET /compliance/information-requests
getInformationRequest(id: string): Promise<InformationRequest>       // GET  /compliance/information-requests/{id}
createInformationRequest(body: CreateInformationRequest): Promise<InformationRequest> // POST /compliance/information-requests
reviewInformationRequest(id: string, body: ReviewInformationRequest): Promise<InformationRequest> // POST /compliance/information-requests/{id}/review
cancelInformationRequest(id: string, body: CancelInformationRequest): Promise<InformationRequest> // POST /compliance/information-requests/{id}/cancel
recordFinding(body: FindingRequest): Promise<Finding>                // POST /compliance/findings
listFindings(commercialPublicId: string): Promise<Finding[]>         // GET  /compliance/commercials/{id}/findings
recordIncident(body: IncidentRequest): Promise<Incident>             // POST /compliance/incidents
listIncidents(commercialPublicId: string): Promise<Incident[]>       // GET  /compliance/commercials/{id}/incidents
listAlerts(params: { pendingOnly?: boolean; page?: number; size?: number }): Promise<PagedResponse<Alert>> // GET /compliance/alerts (pendingOnly por defecto true)
reviewAlert(id: number, body: AlertReviewRequest): Promise<Alert>    // POST /compliance/alerts/{id}/review
listAlertRules(): Promise<AlertRule[]>                               // GET  /compliance/alert-rules
createAlertRule(body: AlertRuleRequest): Promise<AlertRule>          // POST /compliance/alert-rules
deactivateAlertRule(id: number): Promise<AlertRule>                  // POST /compliance/alert-rules/{id}/deactivate
```

**Carga de archivos al expediente:** es el mismo patrón del onboarding.
1. `prepareExpedienteUpload` → `{ documentId, permission.uploadUrl }`.
2. `PUT` del archivo binario a `uploadUrl`, con `Content-Type` igual al declarado y sin el header `Authorization`.
3. `confirmExpedienteUpload(documentId)`.

Si el paso 3 falla, el archivo no cuenta: muestra el error y deja reintentar desde el paso 1. Extrae esto a un hook o servicio `useExpedienteUpload` reutilizable, porque lo usan el expediente y las respuestas a requerimientos.

---

## 3. Textos de estados (diccionario único)

Crea un diccionario `labels.ts` y úsalo en todas las insignias:

| Enum | Valor | Texto | Color |
|---|---|---|---|
| TaxProfileStatus | SUBMITTED | En revisión | azul |
| | OBSERVED | Con observaciones | naranja |
| | REVIEWED | Revisada | verde |
| SectionReviewResult | (sin revisión) | Pendiente de revisión | gris |
| | VERIFIED | Verificada | verde |
| | OBSERVED | Observada | naranja |
| | NOT_APPLICABLE | No aplica | gris |
| CommercialDocumentReviewStatus | NOT_REVIEWED / null | Sin revisar | gris |
| | ACCEPTED | Aceptado | verde |
| | OBSERVED | Con observaciones | naranja |
| | REJECTED | Rechazado | rojo |
| (documento) | expired = true | Vencido | rojo |
| ManifestationStatus | MANIFESTED | Manifestado | verde |
| | NOT_MANIFESTED | Sin manifestar — pendiente de aclaración | naranja |
| | EXPRESS_REFUSAL | No aceptado | rojo |
| | TECHNICAL_INCIDENT | Falla técnica — pendiente de aclaración | gris |
| InformationRequestStatus | OPEN | Pendiente de tu respuesta (empresario) / Esperando respuesta (compliance) | naranja |
| | RESPONDED | En revisión por VERYGANA / Por revisar | azul |
| | OBSERVED | Con observaciones | naranja |
| | CLOSED_SATISFIED | Resuelto | verde |
| | CLOSED_WITH_FINDING | Cerrado con hallazgo | gris |
| | CANCELLED | Anulado | gris |
| IncomeScope | EXTERNAL_ONLY | Solo operaciones fuera de VERYGANA | — |
| | PLATFORM_ONLY | Solo operaciones en VERYGANA | — |
| | CONSOLIDATED_INCLUDES_PLATFORM | Total que ya incluye VERYGANA | — |
| IncomeConcept | INGRESOS / VENTAS / RECAUDOS | Ingresos / Ventas / Recaudos | — |
| EstablishmentRelation | OWNED / LEASED / FRANCHISE / OTHER | Propio / Arrendado / Franquicia / Otro | — |
| ContractualMeasure | NONE / WARNING / PREVENTIVE_RESTRICTION / SUSPENSION / TERMINATION_REFERRAL | Sin medida / Advertencia / Restricción preventiva / Suspensión / Remisión a terminación | — |
| FindingClassification | SUBSANABLE_ERROR / LACK_OF_UPDATE / ISOLATED_INCONSISTENCY / VERIFIED_FALSE_INFO / EXPRESS_REFUSAL | Error subsanable / Falta de actualización / Inconsistencia aislada / Información falsa verificada / Negativa expresa | — |
| Secciones | A_RUT … H_ADVISORY | a) RUT · b) Régimen y actividades · c) IVA e INC · d) Facturación y retención · e) Ingresos o ventas · f) Establecimientos · g) Capacidad de crecimiento · h) Asesoría contable | — |
| Triggers | DOCUMENT_EXPIRED … | Documento vencido · Cambio de actividad · Cambio de establecimientos · Aumento de operación · Nuevo requisito · Inconsistencia · Incidente · Riesgo · Compromiso sin manifestar · Negativa al compromiso | — |

---

## 4. Pantallas del empresario (rol COMMERCIAL)

### 4.1 Onboarding — paso nuevo "Ficha tributaria" (`TaxProfileStep`)

**Cuándo se muestra:** cuando `currentStep === 'TAX_PROFILE_PENDING'`. Agrégalo al stepper entre "Documentos" y "Contrato".

**Cambio en el paso de documentos:** después de `POST /commercials/onboarding/documents/continue`, **no asumas** que el siguiente paso es el contrato. Vuelve a leer el estado del onboarding y navega según `currentStep`: puede ser `TAX_PROFILE_PENDING` o `CONTRACT_PENDING`, si el empresario ya había enviado la ficha antes.

**Carga inicial (en paralelo):**
- `getTaxProfile()`: si devuelve datos, precarga el formulario con `version` (edición).
- `getTaxCatalog()`: agrupa por `catalogType`.
- `listEstablishments()`.
- La lista de documentos del onboarding (endpoint actual `GET /commercials/onboarding/documents`), para elegir el RUT.

**Componente compartido `TaxProfileForm`** (se reutiliza en el perfil, 4.2). Formulario por secciones plegables, una por inciso.

| Sección | Campos | UI |
|---|---|---|
| a) RUT | `rutDocumentId`, `rutResponsibilityCodes` | Selector de documento con los de tipo `RUT` ya cargados y vigentes. Multiselect de códigos `RUT_RESPONSIBILITY` (muestra `code — label`). |
| b) Régimen y actividades | `regimeCode`, `activities[]` | Selector `REGIME`. Lista editable de actividades: CIIU (4 dígitos, solo números), descripción y un radio "Principal" (solo una). Botón "Agregar actividad". |
| c) IVA e INC | `vatConditionCode`, `incConditionCode`, `taxConditionScope` | Dos selectores y un textarea "¿A qué productos o servicios aplica?". |
| d) Facturación y retención | `invoicingObligated`, `invoicingMethodCode`, `invoicingNotes`, `withholdingAgent`, `withholdingNotes` | Dos interruptores Sí/No **sin valor por defecto** (el usuario debe elegir), selector `INVOICING_METHOD` y textareas. |
| e) Ingresos o ventas | `incomeReports[]` | Tabla editable: concepto, desde, hasta, monto en pesos, alcance, soporte opcional (documento del expediente) y notas. Botón "Agregar periodo". |
| f) Establecimientos | (no va en el body) | Resumen "Tienes N establecimientos activos" y enlace o modal para gestionarlos (componente de 4.3). |
| g) Capacidad de crecimiento | `currentCapacity`, `growthScenario`, `growthMeasures` | Tres textareas. Ayuda: "Describe lo que puedes atender hoy. El escenario de crecimiento es solo informativo". |
| h) Asesoría | `hasAccountingAdvisor`, `advisorScope` | Interruptor Sí/No sin valor por defecto. Si es Sí, textarea de alcance. Nota fija: "Esta información no le da acceso a tu asesor a la plataforma". |

**Selectores de catálogo:** si un `catalogType` no tiene entradas, muestra el selector deshabilitado con "Aún no disponible" y envía `null`. Todos los códigos son opcionales.

**Validación en el front** (antes de enviar):
- Al menos una actividad y exactamente una marcada como principal; CIIU `/^\d{4}$/` sin repetir.
- `invoicingObligated`, `withholdingAgent` y `hasAccountingAdvisor` elegidos explícitamente.
- `currentCapacity` no vacío.
- Al menos un ingreso, con `periodEnd >= periodStart`, `periodEnd <= hoy` y monto >= 0.
- Advertencia de solapamiento: para el mismo `concept` con periodos que se cruzan, no se permite `CONSOLIDATED_INCLUDES_PLATFORM` junto con otro valor, ni dos del mismo `scope`. Marca las filas en conflicto con el texto: "Si un total ya incluye las ventas de VERYGANA, no las informes otra vez por separado".

**Bloque informativo arriba del formulario** (texto fijo):

> Esta ficha recoge tu información tributaria tal como la declaras. Incluye también tus actividades e ingresos fuera de VERYGANA. VERYGANA puede revisarla y pedirte soportes; esa revisión no es asesoría contable o tributaria.

Debajo, un enlace "Leer el compromiso de formalización". Abre un modal que llama `getDeclaration('FORMALIZATION_COMMITMENT', 'TAX_PROFILE')` y muestra `fullText`, **solo lectura y sin casilla**. Pon esta nota en el modal: "Este compromiso se manifiesta al aprobar el contrato".

**Enviar:** botón "Guardar y continuar" → `submitTaxProfile(body)` (convierte pesos a centavos). Si responde 200, refresca el estado del onboarding y navega al paso siguiente (`CONTRACT_PENDING`). Si responde 400, muestra `message` y `details`.

### 4.2 Perfil → "Información tributaria" (`TaxProfilePage`)

**Ruta sugerida:** `/perfil/informacion-tributaria`. Visible después del onboarding.

- **Cabecera:**
  - Insignia de estado (`status`), versión actual y fecha.
  - Si `status === 'OBSERVED'`, banner naranja: "VERYGANA dejó observaciones en tu ficha. Corrígelas y envía una nueva versión".
- **Vista de lectura** de `version` por secciones. Junto a cada sección:
  - la insignia de `sectionReviews` correspondiente, o "Pendiente de revisión" si no hay;
  - si la sección está `OBSERVED`, la `notes` destacada.
- **Bloque "Compromiso de formalización"** (solo lectura, **sin casilla**), según `formalizationCommitment`:

  | Valor | Qué mostrar |
  |---|---|
  | `null` | "Aún no registrado. Se manifiesta al aprobar el contrato". |
  | `MANIFESTED` | "Manifestado el {occurredAt} por {actorLegalRepName} · versión {declarationVersion}". |
  | `NOT_MANIFESTED`, `EXPRESS_REFUSAL` o `TECHNICAL_INCIDENT` | La insignia, más un enlace a "Solicitudes de información" ("Tienes una aclaración pendiente"). |

  Además, el enlace "Leer texto" (modal de 4.1).
- **Botón "Editar ficha":** abre `TaxProfileForm` precargado y envía con `submitTaxProfile`. Antes de enviar, confirma: "Se creará la versión {currentVersion+1}. La anterior queda en el historial".
- **Pestaña "Historial":** `listTaxProfileVersions()` → lista de versiones. Al hacer clic, `getTaxProfileVersion(n)` en solo lectura.

### 4.3 Perfil → "Establecimientos" (`EstablishmentsPage`)

También se usa como modal desde la sección f) de la ficha.

- **Lista:** `listEstablishments()`, en dos grupos, "Activos" y "Cerrados" (plegado), con nombre, dirección, municipio y relación. En los cerrados, la fecha y el motivo.
- **Botón "Agregar sede":** formulario `EstablishmentRequest`.
  - Nombre, dirección, departamento → municipio (reutiliza el selector existente; envía `municipalityCode` DANE) y relación.
  - Ayuda: "Registra solo sedes físicas. Una página web, red social o correo no es un establecimiento".
  - Validación en el front: rechaza direcciones que contengan `http`, `www.` o `@`.
- **Acción "Cerrar sede"** (solo activos): modal con motivo obligatorio → `closeEstablishment`.
- **No hay editar ni eliminar.** Si el usuario quiere corregir, indícale: "Cierra esta sede y registra la correcta".
- Errores 400 frecuentes: dirección duplicada en el mismo municipio o canal digital. Muestra `message`.

### 4.4 Onboarding — aprobación del contrato (MODIFICAR la pantalla existente)

**Pantalla:** la que hoy muestra el contrato en `BUSINESS_REVIEW_PENDING` con el botón "Aprobar".

**Al montar:** llama `getDeclaration('FORMALIZATION_COMMITMENT', 'CONTRACT_SHEET')` y guarda `view`.

**Nuevo bloque**, debajo de la vista del contrato y encima del botón Aprobar:

```
┌──────────────────────────────────────────────────────────────┐
│ Hoja Final de Aceptación Electrónica                         │
│ Apartado 9. Compromiso de crecimiento y formalización        │
│ (versión {view.version})                                     │
│                                                              │
│ {view.fullText}   ← texto COMPLETO, literal, sin recortar    │
│                                                              │
│ [ ] Manifiesto expresamente este compromiso                  │
│                                                              │
│ No acepto este compromiso  ← enlace secundario               │
│                                                              │
│ ⓘ Aprobar el contrato no requiere marcar la casilla. Si no   │
│   la marcas, VERYGANA te pedirá una aclaración. Este         │
│   compromiso no cambia tu situación tributaria.              │
└──────────────────────────────────────────────────────────────┘
                       [ Aprobar contrato ]
```

**Estado local:** `decision: 'ACCEPT' | 'REFUSE' | null`, que empieza en `null`, y `refusalReason`.

- **La casilla:**
  - Arranca desmarcada **siempre**: no la inicialices desde datos guardados, `localStorage` ni el estado de otras casillas.
  - Marcarla pone `decision = 'ACCEPT'`; desmarcarla vuelve a `null`.
- **"No acepto este compromiso":** abre un modal "¿Confirmas que no aceptas este compromiso?" con un textarea opcional "Motivo". Al confirmar, `decision = 'REFUSE'` y la casilla se deshabilita. Muestra un chip "No aceptado — Deshacer"; "Deshacer" vuelve a `null`.
- **Si `view.manifestation?.status === 'MANIFESTED'`:** no muestres ni la casilla ni el enlace. Muestra en su lugar: "✓ Ya manifestaste este compromiso el {occurredAt} (versión {declarationVersion})". En ese caso envía `decision` en `null`.
- **Botón "Aprobar contrato":** siempre habilitado; la casilla no lo bloquea.
  - Genera `idempotencyKey = crypto.randomUUID()` **una vez por intento**, guárdala en estado y reutilízala si el usuario reintenta tras un error de red.
  - Llama `approveContract({ declarationVersion: view.version, decision, refusalReason }, idempotencyKey)`.
  - Si la respuesta es 200, sigue el flujo actual: el contrato pasa a revisión de VERYGANA.
- **Si el GET de la declaración falla:** muestra el error y deja aprobar igual sin enviar `decision`. El backend lo registra como no manifestado y luego se aclara. No bloquees la aprobación por eso.

**Prohibido:**
- Premarcar la casilla.
- Marcarla al pulsar "Aprobar".
- Resumir el texto dentro de la etiqueta de la casilla.
- Poner esta casilla en cualquier otra pantalla.

### 4.5 Perfil → "Mi expediente" (`ExpedientePage`)

**Ruta sugerida:** `/perfil/expediente`. Visible con el onboarding en `COMPLETED`; antes, muestra "Disponible al terminar tu registro".

- **Datos:** `listExpediente()`. Agrupa por `documentType`:
  - **Arriba, el vigente** (`status === 'VALIDATED'`) con nombre, fecha de carga, insignia de revisión y la de vencido si `expired`. Si tiene vencimiento, "Vence el {expiresAt}". Si hay `reviewNotes`, muéstralas. Botón "Ver" (abre `downloadUrl`) y botón "Reemplazar".
  - **Debajo, "Historial"** (`status === 'SUPERSEDED'`) plegado, con "Reemplazado el {supersededAt}" y "Ver".
- **"Reemplazar":** selector de archivo (PDF, JPG o PNG, con el límite de tamaño del onboarding) → `useExpedienteUpload` con `replacesDocumentId = vigente.id` y el mismo `documentType`. Al terminar, recarga la lista.
- **"Agregar documento":** para tipos sin vigente, por ejemplo `PERMISO_SECTORIAL`, `MARCA_REGISTRADA` u `OTRO`. Selector de tipo → `useExpedienteUpload` sin `replacesDocumentId`.
- **No hay eliminar.**
- Antes de reemplazar, muestra: "El documento anterior quedará en tu historial".
- Las URLs de descarga caducan a los 5 minutos: recarga la lista antes de abrir si pasó ese tiempo.

### 4.6 Perfil → "Solicitudes de información" (`InformationRequestsPage`)

**Ruta sugerida:** `/perfil/solicitudes`.
- **Menú:** insignia con la cantidad de solicitudes en `OPEN` u `OBSERVED`. Calcúlala con la lista: no hay un endpoint de conteo.
- **Notificaciones:** al hacer clic en las internas de "Solicitud de información de VERYGANA" o "Tu respuesta tiene observaciones", navega aquí.

**Lista:** `listMyInformationRequests()`, ordenada por fecha. Cada tarjeta muestra la insignia de estado, `reason`, el tipo (`trigger`), `issuedAt` y `dueDate` si la hay. Usa dos pestañas: "Pendientes" (`OPEN`, `OBSERVED`, `RESPONDED`) e "Historial" (las cerradas).

**Detalle** (`getMyInformationRequest(id)`):
1. **Qué te pedimos:** cada `item`, con su `description` y "Respuesta esperada: {acceptableResponse}".
   - Si es `DOCUMENT`, muestra el tipo y un botón "Cargar documento", que reemplaza el vigente de ese tipo en el expediente o lo agrega si no existe (reutiliza `useExpedienteUpload`).
   - Si trae `linkedDocumentId`, muestra: "Ya tenemos un documento de este tipo en tu expediente; VERYGANA revisará si es suficiente".
2. **Por qué:** `relationToOperation`.
3. **Conversación:** línea de tiempo con `responses` (lo que respondió el empresario, documentos adjuntos por id con enlace al expediente) y `reviews` (resultado y nota).
4. **Formulario de respuesta**, solo si `status` es `OPEN` u `OBSERVED`:
   - Textarea "Aclaración" (máx 2000) y la lista de documentos adjuntados en esta respuesta (los `documentId` devueltos por `confirmExpedienteUpload`).
   - El botón "Enviar respuesta" exige texto o al menos un documento → `respondInformationRequest(id, { message, documentIds })`.
   - Tras responder, muestra: "Recibimos tu respuesta. VERYGANA la revisará; la solicitud se cierra cuando termine la revisión".
5. **Banner de tono:** si `trigger` es `MISSING_MANIFESTATION`, `EXPRESS_REFUSAL` o `INCIDENT`:
   > Esta solicitud es una aclaración sobre el compromiso de formalización. No es una sanción.

---

## 5. Pantallas de cumplimiento (rol COMPLIANCE_OFFICER)

Agrégalas al menú de compliance existente: "Fichas tributarias", "Requerimientos", "Alertas", "Catálogo tributario" y "Reglas de alerta".

### 5.1 Cola de fichas (`TaxProfilesQueuePage`)

- `listTaxProfiles({ status, page, size })`. Filtro por estado, con `SUBMITTED` por defecto.
- Tabla con empresa, estado, versión y fecha de actualización. Al hacer clic se abre 5.2.

### 5.2 Revisión de ficha (`TaxProfileReviewPage`)

**Datos:** `getComplianceTaxProfile(commercialPublicId)`.

- **Cabecera:**
  - Empresa, NIT y estado.
  - Vocación (`commercialActivityType`) con la nota "Solo para contrastar: la ficha no modifica la vocación".
- **Columna izquierda:** las 8 secciones con los datos declarados de `profile.version`. Lo que hay que mostrar:
  - en a), el enlace al documento RUT (resuélvelo con `listCommercialDocuments`);
  - en e), la tabla de ingresos con su alcance;
  - en f), `establishmentsCount` y la lista de `establishmentHistory`.
- **Columna derecha, por sección:** último resultado y botones "Verificar", "Observar" (pide nota obligatoria) y "No aplica" → `reviewTaxProfileSection`. Refresca con la respuesta, que trae el estado general recalculado.
- **Selector "Comparar con versión…":** `getComplianceTaxProfileVersion(id, n)`, en vista lado a lado, resaltando los campos que cambian.
- **Accesos rápidos:** "Ver expediente documental" (5.3) y "Ver caso del empresario" (5.4).

### 5.3 Revisión de documentos (dentro de 5.4, pestaña "Documentos")

**Datos:** `listCommercialDocuments(commercialPublicId)`, agrupados como en 4.5. En los vigentes hay un botón "Revisar", que abre un modal con `DocumentReviewRequest`:
- **Resultado:** Aceptar, Observar o Rechazar.
- **"NIT o cédula del titular":** obligatorio al aceptar RUT, Cámara de Comercio, certificación bancaria o cédula del representante. Ayuda: "Escríbelo como figura en el documento; se compara con el empresario".
- **Fecha de expedición y fecha de vencimiento:** con un check "El documento tiene vigencia comprobable"; si no se marca, `expiresAt` no se envía.
- **Notas:** obligatorias si se observa o rechaza.

Un 400 de "titular no corresponde" se muestra tal cual: el revisor debe marcarlo como observado o rechazado.

### 5.4 Caso del empresario (`CommercialCasePage`)

**Ruta sugerida:** `/compliance/empresarios/{commercialPublicId}`. Es una página con pestañas que reúne todo:

| Pestaña | Contenido | Datos |
|---|---|---|
| Ficha tributaria | Resumen y enlace a 5.2 | `getComplianceTaxProfile` (si da 404, "Sin ficha enviada") |
| Documentos | Vista 5.3 | `listCommercialDocuments` |
| Compromiso | Línea de tiempo de manifestaciones (estado, contrato v{contractVersion}, fecha, actor, `detail`) | `getDeclarationHistory` |
| Requerimientos | Lista filtrada y botón "Nuevo requerimiento" (5.6) | `searchInformationRequests({ commercialPublicId })` |
| Hallazgos | Historial y botón "Registrar hallazgo" (5.7) | `listFindings` |
| Incidencias | Historial y botón "Registrar incidencia" (`IncidentRequest`: contexto, descripción, requerimiento opcional) | `listIncidents` / `recordIncident` |

Enlaza esta página desde la cola de fichas, la de requerimientos, la de alertas y el listado de comerciales que ya existe.

### 5.5 Cola de requerimientos (`InformationRequestsQueuePage`)

- `searchInformationRequests({ status, page, size })`. Filtros: estado (por defecto `RESPONDED`, "por revisar") y empresario opcional.
- Tabla con empresa, tipo, estado, emitido por (Sistema si `issuedBySystem`, si no Compliance), fecha y vencimiento. Al hacer clic se abre el detalle.

**Detalle** (`InformationRequestDetailPage`, `getInformationRequest(id)`):
- Motivo, relación, ítems, línea de tiempo de respuestas y revisiones.
- `communications`, con insignia `SENT` o `FAILED`. Si `FAILED`, muestra `errorDetail` y el aviso: "El empresario podría no haber recibido la notificación; no lo trates como falta de respuesta".
- **Si `status === 'RESPONDED'`:** botones "Suficiente" y "Insuficiente" (este último pide nota) → `reviewInformationRequest`.
- **Si está abierto** (`OPEN`, `RESPONDED` u `OBSERVED`): botón "Anular" con motivo obligatorio → `cancelInformationRequest`.
- **Botón "Registrar hallazgo":** abre 5.7 con `requestPublicId` ya cargado. El requerimiento se cierra con el hallazgo.

### 5.6 Crear requerimiento (`CreateInformationRequestForm`)

Es un modal o página que se abre desde 5.4, desde una alerta (5.8) o desde la cola.

- **Empresario:** fijo si viene del caso o de una alerta.
- **Tipo (`trigger`):** selector con los textos del diccionario.
- **Motivo:** obligatorio. Ayuda: "Explica el hecho concreto que origina la solicitud".
- **Relación con la vinculación o el funcionamiento:** obligatoria. Ayuda: "¿Qué requisito se evalúa con esta información?".
- **Plazo:** fecha opcional. Ayuda: "Solo si una regla aplicable fija un plazo".
- **Ítems:** al menos uno, como lista dinámica:
  - tipo (Documento, Campo o Aclaración);
  - si es Documento, el tipo de documento (obligatorio) y la sede opcional (selector de `establishmentHistory` activos);
  - si es Campo, el `fieldPath` (texto, por ejemplo `taxProfile.currentCapacity`);
  - descripción (obligatoria) y respuesta aceptable.
- **No ofrezcas** una opción tipo "Actualizar toda la información".
- Si viene de una alerta, envía `alertId`.
- **Errores:** si ya existe uno idéntico abierto, el backend responde 400 con el `publicId` existente en el mensaje. Muéstralo y ofrece abrir ese.

### 5.7 Registrar hallazgo (`FindingForm`)

Es la acción más sensible: puede restringir o suspender la cuenta.

**Campos:**
- Hecho observado (obligatorio).
- Evidencia.
- Clasificación.
- Gravedad.
- Afectación (obligatoria).
- "¿Es subsanable?" (Sí/No, obligatorio).
- Fundamento contractual (obligatorio).
- Medida.
- Alcance de la medida.

**Reglas de la UI:**
- **Medidas limitadas:** con `SUBSANABLE_ERROR` o `ISOLATED_INCONSISTENCY`, el selector de medida solo permite Sin medida o Advertencia. Deshabilita el resto con la nota "Una inconsistencia aislada o un error subsanable no justifican restringir ni suspender".
- **Negativa expresa:** `EXPRESS_REFUSAL` solo se habilita si el historial del compromiso (pestaña Compromiso) tiene algún `EXPRESS_REFUSAL`. Si no, deshabilítalo con "No hay una negativa expresa registrada".
- **Medidas que cambian la cuenta:** antes de enviar con `PREVENTIVE_RESTRICTION` o `SUSPENSION`, usa un `ConfirmDialog` rojo:
  > Esta medida cambiará de inmediato el estado de la cuenta de **{empresa}** a **{Restricción preventiva | Suspensión}**. Quedará registrada con su fundamento. ¿Confirmas?

  Pide que escriba el nombre de la empresa o marque un check para confirmar.
- **Remisión a terminación:** con `TERMINATION_REFERRAL`, muestra: "No cambia el estado de la cuenta; remite el caso al procedimiento de terminación".
- **Resultado:** muestra `accountStatusApplied` si cambió.

### 5.8 Alertas (`AlertsPage`)

- `listAlerts({ pendingOnly })`, con un interruptor "Solo pendientes" activado por defecto.
- Cada alerta muestra fecha, empresa (enlace a 5.4), tipo, regla y versión (o "Evento del expediente" si `ruleCode` es null) y `message` **completo**: incluye el alcance de la alerta y no se debe recortar.
- Botón "Ver datos": muestra `dataUsedJson` parseado como tabla clave/valor (centavos formateados en pesos).
- **Acciones** si no está revisada:
  - "Marcar revisada": nota obligatoria → `reviewAlert`.
  - "Abrir requerimiento": abre 5.6 con `alertId`, empresario y un `trigger` sugerido igual al de la alerta.
- Si ya está revisada, muestra la nota y, si hay `requestPublicId`, un enlace al requerimiento.

### 5.9 Reglas de alerta (`AlertRulesPage`)

- `listAlertRules()`. Agrupa por `code`: la versión activa arriba y las demás en el historial, cada una con sus parámetros (`paramsJson` parseado: ventana en días, % mínimo y ventas mínimas en pesos).
- **"Nueva regla / Nueva versión":** formulario `AlertRuleRequest`.
  - `ruleType` fijo en `PLATFORM_SALES_GROWTH`, con el texto "Crecimiento de ventas en VERYGANA entre dos ventanas iguales".
  - Fundamento obligatorio.
  - **Sin valores por defecto** en los umbrales.
  - Si el `code` ya existe, avisa: "Se creará la versión N+1 y la actual se desactivará".
  - Nota fija: "La regla solo ve operaciones en VERYGANA; sus alertas son informativas".
- **"Desactivar"** en la versión activa → `deactivateAlertRule`, con confirmación.

### 5.10 Catálogo tributario (`TaxCatalogPage`)

- `listTaxCatalog()`. Pestañas por `catalogType`; tabla con código, etiqueta, fundamento, vigencia e insignia Activo/Retirado.
- **"Agregar código":** `CatalogEntryRequest`, con fundamento obligatorio y "Vigente desde".
- **"Retirar":** pide la fecha "Vigente hasta" → `retireCatalogEntry`.
- No hay editar ni borrar. Nota fija: "Un código no se edita: se retira y se crea uno nuevo".

---

## 6. Permisos y navegación

| Ruta del front | Rol | Condición |
|---|---|---|
| Paso `TAX_PROFILE_PENDING` | COMMERCIAL | Según `currentStep` |
| `/perfil/informacion-tributaria` | COMMERCIAL | Ficha enviada; si no la hay (204), invita a completarla |
| `/perfil/establecimientos` | COMMERCIAL | Siempre |
| `/perfil/expediente` | COMMERCIAL | Onboarding `COMPLETED` |
| `/perfil/solicitudes` | COMMERCIAL | Siempre |
| `/compliance/fichas-tributarias` | COMPLIANCE_OFFICER | — |
| `/compliance/empresarios/:publicId` | COMPLIANCE_OFFICER | — |
| `/compliance/requerimientos` | COMPLIANCE_OFFICER | — |
| `/compliance/alertas` | COMPLIANCE_OFFICER | — |
| `/compliance/reglas-alerta` | COMPLIANCE_OFFICER | — |
| `/compliance/catalogo-tributario` | COMPLIANCE_OFFICER | — |

`ADMIN` **no** tiene acceso a ninguna pantalla de compliance de este documento: el backend responde 403.

---

## 7. Checklist de aceptación

**Empresario**
- [ ] El onboarding pasa por `TAX_PROFILE_PENDING` cuando corresponde y salta al contrato si la ficha ya existía.
- [ ] La ficha valida una sola actividad principal, CIIU de 4 dígitos, periodos no futuros y solapamientos de ingresos.
- [ ] Los catálogos vacíos no bloquean el envío.
- [ ] Al editar la ficha se informa que se crea una versión nueva; el historial se puede consultar.
- [ ] Los establecimientos no se editan ni se borran; se cierran con motivo.
- [ ] En la aprobación del contrato, la casilla aparece desmarcada con el texto completo, hay una opción separada para no aceptar y el botón Aprobar nunca está bloqueado por la casilla.
- [ ] `approveContract` envía `Idempotency-Key` y la reutiliza en reintentos.
- [ ] Si ya existe una manifestación `MANIFESTED`, se muestra como evidencia y no se vuelve a pedir.
- [ ] En la ficha y el perfil, el compromiso aparece solo en lectura.
- [ ] El expediente muestra vigentes e historial, permite reemplazar y no permite borrar.
- [ ] Las solicitudes muestran qué se pide y por qué. Se responde con texto y/o documentos, y queda claro que responder no cierra.

**Compliance**
- [ ] Revisión por sección con nota obligatoria al observar.
- [ ] Revisión de documentos con titular obligatorio al aceptar documentos de identidad.
- [ ] Los requerimientos no permiten ítems vacíos ni "actualizar todo".
- [ ] El hallazgo bloquea medidas fuertes para errores subsanables o inconsistencias aisladas, y pide confirmación explícita antes de restringir o suspender.
- [ ] Las alertas muestran el mensaje completo y los datos usados; desde una alerta se puede abrir un requerimiento.
- [ ] Las reglas y el catálogo no tienen valores por defecto ni edición; se versionan o se retiran.
- [ ] Todos los errores 400 muestran el `message` del backend.
