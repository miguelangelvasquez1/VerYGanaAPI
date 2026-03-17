package com.verygana2.utils.validators.games;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.verygana2.utils.validators.games.ValidationPipeline.ErrorType;
import com.verygana2.utils.validators.games.ValidationPipeline.ValidationError;

import lombok.extern.slf4j.Slf4j;

/**
 * Semantic validator for game-specific business rules
 * Rules that cannot be expressed in JSON Schema
 */
@Component
@Slf4j
public class SemanticValidator {
    
    public List<ValidationError> validate(Map<String, Object> configData, String gameCode) {
        log.debug("Validating semantic rules for game: {}", gameCode);
        
        return switch (gameCode.toLowerCase()) {
            case "sudoku" -> validateSudoku(configData);
            case "match3" -> validateMatch3(configData);
            case "bubble_shooter" -> validateBubbleShooter(configData);
            default -> new ArrayList<>();
        };
    }
    
    private List<ValidationError> validateSudoku(Map<String, Object> configData) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Example: Sudoku must have exactly 9 tiles per row/column
        Object gameConfigObj = configData.get("game_config");
        if (gameConfigObj instanceof Map) {
            Map<String, Object> gameConfig = (Map<String, Object>) gameConfigObj;
            Object tilesObj = gameConfig.get("tiles");
            
            if (tilesObj instanceof List) {
                List<?> tiles = (List<?>) tilesObj;
                if (tiles.size() != 81) { // 9x9 grid
                    errors.add(new ValidationError(
                        "game_config.tiles",
                        "Sudoku must have exactly 81 tiles (9x9 grid), found: " + tiles.size(),
                        ErrorType.SEMANTIC
                    ));
                }
            }
        }
        
        // Validate difficulty levels exist and are valid
        Object gameObj = configData.get("game");
        if (gameObj instanceof Map) {
            Map<String, Object> game = (Map<String, Object>) gameObj;
            Object levelsObj = game.get("levels");
            
            if (levelsObj instanceof List) {
                List<?> levels = (List<?>) levelsObj;
                if (levels.isEmpty()) {
                    errors.add(new ValidationError(
                        "game.levels",
                        "At least one difficulty level must be defined",
                        ErrorType.SEMANTIC
                    ));
                }
                
                // Validate each level has required difficulty
                for (int i = 0; i < levels.size(); i++) {
                    if (levels.get(i) instanceof Map) {
                        Map<String, Object> level = (Map<String, Object>) levels.get(i);
                        Object difficulty = level.get("difficulty");
                        
                        if (difficulty != null) {
                            String diffStr = difficulty.toString();
                            if (!List.of("easy", "medium", "hard", "expert").contains(diffStr)) {
                                errors.add(new ValidationError(
                                    "game.levels[" + i + "].difficulty",
                                    "Invalid difficulty: " + diffStr + ". Must be: easy, medium, hard, or expert",
                                    ErrorType.SEMANTIC
                                ));
                            }
                        }
                    }
                }
            }
        }
        
        return errors;
    }
    
    private List<ValidationError> validateMatch3(Map<String, Object> configData) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Example: Match3 must have at least 5 gem types
        Object gameConfigObj = configData.get("game_config");
        if (gameConfigObj instanceof Map) {
            Map<String, Object> gameConfig = (Map<String, Object>) gameConfigObj;
            Object gemsObj = gameConfig.get("gems");
            
            if (gemsObj instanceof List) {
                List<?> gems = (List<?>) gemsObj;
                if (gems.size() < 5) {
                    errors.add(new ValidationError(
                        "game_config.gems",
                        "Match3 requires at least 5 gem types, found: " + gems.size(),
                        ErrorType.SEMANTIC
                    ));
                }
                if (gems.size() > 8) {
                    errors.add(new ValidationError(
                        "game_config.gems",
                        "Match3 supports maximum 8 gem types, found: " + gems.size(),
                        ErrorType.SEMANTIC
                    ));
                }
            }
        }
        
        // Validate powerups have valid costs
        Object gameObj = configData.get("game");
        if (gameObj instanceof Map) {
            Map<String, Object> game = (Map<String, Object>) gameObj;
            Object powerupsObj = game.get("powerups");
            
            if (powerupsObj instanceof List) {
                List<?> powerups = (List<?>) powerupsObj;
                
                for (int i = 0; i < powerups.size(); i++) {
                    if (powerups.get(i) instanceof Map) {
                        Map<String, Object> powerup = (Map<String, Object>) powerups.get(i);
                        Object cost = powerup.get("cost");
                        
                        if (cost instanceof Number) {
                            int costValue = ((Number) cost).intValue();
                            if (costValue < 0) {
                                errors.add(new ValidationError(
                                    "game.powerups[" + i + "].cost",
                                    "Powerup cost cannot be negative",
                                    ErrorType.SEMANTIC
                                ));
                            }
                        }
                    }
                }
            }
        }
        
        return errors;
    }
    
    private List<ValidationError> validateBubbleShooter(Map<String, Object> configData) {
        List<ValidationError> errors = new ArrayList<>();
        
        // Example: Bubble colors must be distinct
        Object gameConfigObj = configData.get("game_config");
        if (gameConfigObj instanceof Map) {
            Map<String, Object> gameConfig = (Map<String, Object>) gameConfigObj;
            Object bubblesObj = gameConfig.get("bubble_colors");
            
            if (bubblesObj instanceof List) {
                List<?> bubbles = (List<?>) bubblesObj;
                if (bubbles.size() < 3) {
                    errors.add(new ValidationError(
                        "game_config.bubble_colors",
                        "Bubble Shooter requires at least 3 distinct colors",
                        ErrorType.SEMANTIC
                    ));
                }
            }
        }
        
        return errors;
    }
}
