package com.verygana2.config.pqrs;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.verygana2.models.enums.pqrs.PqrsType;

import lombok.Data;

@Component
@ConfigurationProperties(prefix = "pqrs")
@Data
public class PqrsSlaProperties {

    // Días hábiles de plazo legal por tipo de PQRS (Ley 1755 de 2015).
    private Map<PqrsType, Integer> slaDays = new EnumMap<>(PqrsType.class);

    public int getSlaDaysFor(PqrsType type) {
        return slaDays.getOrDefault(type, 15);
    }
}
