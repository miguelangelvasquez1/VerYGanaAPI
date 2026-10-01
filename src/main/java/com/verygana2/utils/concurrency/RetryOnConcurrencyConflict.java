package com.verygana2.utils.concurrency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Reintenta el método cuando falla con un conflicto de concurrencia transitorio
 * de la base de datos:
 *
 * <ul>
 *   <li>Deadlock de InnoDB — MySQL {@code SQLState 40001} / error {@code 1213},
 *       Spring {@code CannotAcquireLockException}.</li>
 *   <li>Lock-wait timeout — MySQL error {@code 1205}.</li>
 *   <li>Fallo de bloqueo optimista ({@code @Version}) — Spring
 *       {@code ObjectOptimisticLockingFailureException}.</li>
 * </ul>
 *
 * En un deadlock MySQL revierte una de las transacciones en conflicto (la
 * "víctima") y deja continuar a la otra; reintentar la víctima con un backoff
 * corto es la recuperación estándar y evita que el error llegue al cliente como
 * un 500. {@link ConcurrencyRetryAspect} lo implementa.
 *
 * <p>Para que el reintento sea correcto el método anotado debe:
 * <ul>
 *   <li>Ser el límite transaccional más externo (lo invoca un controller u otro
 *       bean sin transacción abierta). Si ya hay una transacción activa el
 *       aspecto NO reintenta —esa transacción ya quedó rollback-only— y deja
 *       propagar la excepción para que la reintente el llamador externo.</li>
 *   <li>Poder re-ejecutarse completo: vuelve a leer del repositorio las
 *       entidades que modifica y no depende de trabajo hecho en un intento
 *       previo.</li>
 *   <li>Tener sus efectos colaterales no transaccionales (correos, llamadas
 *       HTTP, escrituras {@code REQUIRES_NEW}) al final o ser idempotente en
 *       ellos: un intento fallido pudo haberlos disparado ya.</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RetryOnConcurrencyConflict {

    /** Número máximo de ejecuciones del método, incluida la primera. */
    int maxAttempts() default 4;

    /** Espera base en ms del backoff exponencial con jitter entre intentos. */
    long baseDelayMillis() default 40L;
}
