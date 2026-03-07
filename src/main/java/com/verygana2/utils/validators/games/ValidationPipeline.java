package com.verygana2.utils.validators.games;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.verygana2.models.games.Campaign;
import com.verygana2.models.games.Game;
import com.verygana2.models.games.GameConfigDefinition;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationPipeline {
    
    private final SchemaValidator schemaValidator;
    private final SemanticValidator semanticValidator;
    private final AssetValidator assetValidator;
    
    /**
     * Full validation pipeline for campaign config
     * ORDER MATTERS:
     * 1. Schema validation (structure)
     * 2. Semantic validation (business rules)
     * 3. Asset validation (references)
     */
    public ValidationResult validate(
        Map<String, Object> configData,
        GameConfigDefinition configDefinition,
        Game game,
        Campaign campaign
    ) {
        log.info("Starting validation pipeline for game: {}", game.getTitle());
        
        List<ValidationError> errors = new ArrayList<>();
        
        // STEP 1: Schema Validation
        log.debug("Step 1: Schema validation");
        List<ValidationError> schemaErrors = schemaValidator.validate(
            configData, 
            configDefinition.getJsonSchema()
        );
        errors.addAll(schemaErrors);
        
        // If schema validation fails, stop here (no point in semantic validation)
        if (!schemaErrors.isEmpty()) {
            log.warn("Schema validation failed with {} errors", schemaErrors.size());
            return ValidationResult.failure(errors);
        }
        
        // STEP 2: Semantic Validation (game-specific rules)
        log.debug("Step 2: Semantic validation for game: {}", game.getTitle());
        List<ValidationError> semanticErrors = semanticValidator.validate(
            configData,
            game.getTitle()
        );
        errors.addAll(semanticErrors);
        
        if (!semanticErrors.isEmpty()) {
            log.warn("Semantic validation failed with {} errors", semanticErrors.size());
            return ValidationResult.failure(errors);
        }
        
        // STEP 3: Asset Validation
        log.debug("Step 3: Asset validation");
        List<ValidationError> assetErrors = assetValidator.validate(
            configData,
            campaign
        );
        errors.addAll(assetErrors);
        
        if (!assetErrors.isEmpty()) {
            log.warn("Asset validation failed with {} errors", assetErrors.size());
            return ValidationResult.failure(errors);
        }
        
        log.info("Validation pipeline completed successfully");
        return ValidationResult.success();
    }
    
    public static class ValidationResult {
        private final boolean valid;
        private final List<ValidationError> errors;
        
        private ValidationResult(boolean valid, List<ValidationError> errors) {
            this.valid = valid;
            this.errors = errors != null ? errors : new ArrayList<>();
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, new ArrayList<>());
        }
        
        public static ValidationResult failure(List<ValidationError> errors) {
            return new ValidationResult(false, errors);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public List<ValidationError> getErrors() {
            return errors;
        }
    }
    
    public static class ValidationError {
        private final String field;
        private final String message;
        private final ErrorType type;
        
        public ValidationError(String field, String message, ErrorType type) {
            this.field = field;
            this.message = message;
            this.type = type;
        }
        
        public String getField() {
            return field;
        }
        
        public String getMessage() {
            return message;
        }
        
        public ErrorType getType() {
            return type;
        }
    }
    
    public enum ErrorType {
        SCHEMA,
        SEMANTIC,
        ASSET
    }
}
