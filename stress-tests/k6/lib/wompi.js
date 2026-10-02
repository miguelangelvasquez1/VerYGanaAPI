// Webhook transaction.updated de Wompi, armado y firmado como el real.
//
// Firma: SHA256(properties concatenadas + timestamp + eventsKey), con
// properties = [transaction.id, transaction.status, transaction.amount_in_cents].
// El vector de prueba está fijado en WompiWebhookSignatureVectorTest (Java) y en selftest.js.
import { sha256 } from 'k6/crypto';

const PROPERTIES = ['transaction.id', 'transaction.status', 'transaction.amount_in_cents'];

export function checksum(id, status, amountInCents, timestamp, eventsKey) {
  return sha256(`${id}${status}${amountInCents}${timestamp}${eventsKey}`, 'hex');
}

/**
 * Devuelve { body, checksum } con el cuerpo JSON del evento tal cual se envía (la firma de la API
 * se valida sobre el cuerpo crudo) y el valor del header x-event-checksum.
 */
export function buildEvent({ id, reference, status, amountInCents, eventsKey, timestamp }) {
  const ts = timestamp || Math.floor(Date.now() / 1000);
  const sum = checksum(id, status, amountInCents, ts, eventsKey);
  const body = JSON.stringify({
    event: 'transaction.updated',
    data: {
      transaction: {
        id,
        status,
        reference,
        amount_in_cents: amountInCents,
        currency: 'COP',
        payment_method_type: 'NEQUI',
        created_at: new Date(ts * 1000).toISOString(),
      },
    },
    environment: 'test',
    signature: { properties: PROPERTIES, checksum: sum },
    timestamp: ts,
    sent_at: new Date(ts * 1000).toISOString(),
  });
  return { body, checksum: sum };
}
