package com.verygana2.dtos.raffle.responses;

import java.util.List;

/**
 * Resultado interno del servicio Random.org:
 * los índices generados y la evidencia asociada al request.
 */
public record RandomOrgDrawResult(List<Integer> indices, RandomOrgDrawMetadata metadata) {}
