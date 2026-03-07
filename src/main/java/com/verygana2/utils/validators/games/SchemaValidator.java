package com.verygana2.utils.validators.games;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.utils.validators.games.ValidationPipeline.ErrorType;
import com.verygana2.utils.validators.games.ValidationPipeline.ValidationError;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Schema validator using networknt json-schema-validator
 * Validates JSON data against JSON Schema (Draft 7+)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SchemaValidator {
    
    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    
    /**
     * Validate config data against JSON schema
     * NO manual field-by-field validation - let the library do its job
     */
    public List<ValidationError> validate(Map<String, Object> configData, Map<String, Object> schemaDefinition) {
        List<ValidationError> errors = new ArrayList<>();
        
        try {
            // Convert schema map to JsonNode
            JsonNode schemaNode = objectMapper.valueToTree(schemaDefinition);
            JsonSchema schema = schemaFactory.getSchema(schemaNode);
            
            // Convert config data to JsonNode
            JsonNode dataNode = objectMapper.valueToTree(configData);
            
            // Validate
            Set<ValidationMessage> validationMessages = schema.validate(dataNode);
            
            // Convert validation messages to our error format
            for (ValidationMessage msg : validationMessages) {
                errors.add(new ValidationError(
                    msg.getCode(),
                    msg.getMessage(),
                    ErrorType.SCHEMA
                ));
            }
            
            if (!errors.isEmpty()) {
                log.warn("Schema validation failed with {} errors", errors.size());
            }
            
        } catch (Exception e) {
            log.error("Error during schema validation", e);
            errors.add(new ValidationError(
                "$root",
                "Schema validation error: " + e.getMessage(),
                ErrorType.SCHEMA
            ));
        }
        
        return errors;
    }
}
