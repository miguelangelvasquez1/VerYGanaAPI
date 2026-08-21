INSERT INTO games (
		id,
    title,
    description,
    delivery_type,
    url,
    front_page_url,
    active,
    created_at,
    updated_at
) SELECT
		8,
    'Sudoku Classic',
    'Juego clásico de sudoku con múltiples niveles.',
    'QUERY',
    'Sudoku',
    'https://games.verygana.com/game_icons/cali/sudoku.png',
    true,
    NOW(),
    NOW()
WHERE NOT EXISTS (SELECT 1 FROM games WHERE id = 8);


INSERT INTO game_config_definitions (
    game_id,
    version,
    json_schema,
    ui_schema,
    active,
    is_latest,
    created_at,
    created_by,
    average_reward_per_session_cents,
    completion_reward_cents,
    max_reward_per_session_cents,
    score_reward_factor,
    average_duration_seconds
)
SELECT
    8,
    1,
    -- JSON SCHEMA (validation rules)
    '{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Sudoku Game Configuration",
  "type": "object",
  "required": ["meta", "game_config", "branding", "audio", "texts", "game", "rewards", "personalization"],
  "properties": {
    "meta": {
      "type": "object",
      "title": "Metadata",
      "description": "General metadata associated with the game configuration",
      "required": ["brand_id"],
      "properties": {
        "brand_id": {
          "type": "string",
          "title": "Brand ID",
          "description": "Unique identifier for the brand",
          "minLength": 1,
          "maxLength": 50,
          "default": "default"
        }
      }
    },
    "game_config": {
      "type": "object",
      "title": "Game Configuration",
      "description": "Core gameplay settings for Sudoku",
      "required": ["time_limit", "difficulty", "max_errors", "empty_cells", "warning_threshold", "use_countdown", "enable_powerups", "levels"],
      "properties": {
        "time_limit": {
          "type": "integer",
          "title": "Time Limit",
          "description": "Time limit in seconds (0 = unlimited)",
          "minimum": 0,
          "maximum": 3600,
          "default": 300
        },
        "difficulty": {
          "type": "string",
          "title": "Difficulty Level",
          "description": "Game difficulty setting",
          "enum": ["easy", "normal", "hard"],
          "default": "normal"
        },
        "max_errors": {
          "type": "integer",
          "title": "Maximum Errors",
          "description": "Number of errors allowed before losing (0 = unlimited)",
          "minimum": 0,
          "maximum": 10,
          "default": 3
        },
        "empty_cells": {
          "type": "integer",
          "title": "Empty Cells",
          "description": "Number of cells to leave empty at start (3-32, more = harder)",
          "minimum": 3,
          "maximum": 32,
          "default": 12
        },
        "warning_threshold": {
          "type": "number",
          "title": "Time Warning Threshold",
          "description": "Percentage of time remaining to show warning (0.15 = 15%)",
          "minimum": 0.0,
          "maximum": 1.0,
          "default": 0.15
        },
        "use_countdown": {
          "type": "boolean",
          "title": "Use Countdown Timer",
          "description": "Show countdown timer instead of count-up",
          "default": true
        },
        "enable_powerups": {
          "type": "boolean",
          "title": "Enable Power-ups",
          "description": "Whether power-ups (rocket, eraser, bomb) are available during gameplay",
          "default": true
        },
        "levels": {
          "type": "array",
          "title": "Levels",
          "description": "Per-level difficulty overrides applied as the player progresses",
          "minItems": 1,
          "items": {
            "type": "object",
            "title": "Level",
            "required": ["id", "empty_cells", "time_limit", "max_errors", "use_countdown", "enable_powerups"],
            "properties": {
              "id": {
                "type": "integer",
                "title": "Level ID",
                "minimum": 1,
                "maximum": 100
              },
              "empty_cells": {
                "type": "integer",
                "title": "Empty Cells",
                "description": "Number of cells to leave empty at start (3-32, more = harder)",
                "minimum": 3,
                "maximum": 32,
                "default": 12
              },
              "time_limit": {
                "type": "integer",
                "title": "Time Limit",
                "description": "Time limit in seconds (0 = unlimited)",
                "minimum": 0,
                "maximum": 3600,
                "default": 300
              },
              "max_errors": {
                "type": "integer",
                "title": "Maximum Errors",
                "description": "Number of errors allowed before losing (0 = unlimited)",
                "minimum": 0,
                "maximum": 10,
                "default": 3
              },
              "use_countdown": {
                "type": "boolean",
                "title": "Use Countdown Timer",
                "default": true
              },
              "enable_powerups": {
                "type": "boolean",
                "title": "Enable Power-ups",
                "default": true
              }
            }
          }
        }
      }
    },
    "branding": {
      "type": "object",
      "title": "Branding & Visual Settings",
      "required": ["images", "background_config", "colors"],
      "properties": {
        "images": {
          "type": "object",
          "title": "Image Assets",
          "required": ["main_logo_url", "main_logo_offset_y", "logo_watermark_url", "logo_watermark_offset_y", "background_url", "background_color_hex", "cell_background_url", "button_background_url", "bomb_url", "horizontal_url", "vertical_url"],
          "properties": {
            "main_logo_url": {
              "type": ["string", "null"],
              "title": "Main Logo URL",
              "format": "uri",
              "default": ""
            },
            "main_logo_offset_y": {
              "type": "number",
              "title": "Main Logo Y Offset",
              "description": "Vertical offset for main logo positioning",
              "minimum": -2.0,
              "maximum": 2.0,
              "default": 0.0
            },
            "logo_watermark_url": {
              "type": ["string", "null"],
              "title": "Watermark Logo URL",
              "format": "uri",
              "default": ""
            },
            "logo_watermark_offset_y": {
              "type": "number",
              "title": "Watermark Logo Y Offset",
              "description": "Vertical offset for watermark logo positioning",
              "minimum": -2.0,
              "maximum": 2.0,
              "default": 0.0
            },
            "background_url": {
              "type": ["string", "null"],
              "title": "Background Image URL",
              "description": "General background image",
              "format": "uri",
              "default": ""
            },
            "background_color_hex": {
              "type": "string",
              "title": "Background Color",
              "description": "Fallback background color if no image",
              "pattern": "^#[0-9A-Fa-f]{6}$",
              "default": "#FFFFFF"
            },
            "cell_background_url": {
              "type": ["string", "null"],
              "title": "Cell Background Image URL",
              "description": "Background image for each cell",
              "format": "uri",
              "default": ""
            },
            "button_background_url": {
              "type": ["string", "null"],
              "title": "Button Background Image URL",
              "description": "Background image for number buttons",
              "format": "uri",
              "default": ""
            },
            "bomb_url": {
              "type": ["string", "null"],
              "title": "Bomb Power-up Icon URL",
              "description": "Icon shown for the bomb (clear-cell) power-up",
              "format": "uri",
              "default": ""
            },
            "horizontal_url": {
              "type": ["string", "null"],
              "title": "Horizontal Clear Icon URL",
              "description": "Icon shown for the horizontal row-clear power-up",
              "format": "uri",
              "default": ""
            },
            "vertical_url": {
              "type": ["string", "null"],
              "title": "Vertical Clear Icon URL",
              "description": "Icon shown for the vertical column-clear power-up",
              "format": "uri",
              "default": ""
            }
          }
        },
        "background_config": {
          "type": "object",
          "title": "Background Layer Configuration",
          "description": "Configuration for the Front and Back background rendering layers",
          "required": ["Front", "Back"],
          "properties": {
            "Front": {
              "type": "object",
              "title": "Front Layer",
              "description": "Configuration for the foreground background layer",
              "required": ["SpriteUrl", "ColorHex", "Enabled", "Speed", "Rotation", "LayoutMode", "AspectRatio"],
              "properties": {
                "SpriteUrl": {
                  "type": ["string", "null"],
                  "title": "Sprite URL",
                  "description": "URL of the foreground layer sprite image",
                  "format": "uri",
                  "default": ""
                },
                "ColorHex": {
                  "type": "string",
                  "title": "Color Hex",
                  "description": "Hex color applied to the foreground layer sprite",
                  "minLength": 7,
                  "maxLength": 9,
                  "default": "#FFFFFF"
                },
                "Enabled": {
                  "type": "boolean",
                  "title": "Enabled",
                  "description": "Whether the foreground layer is active and visible",
                  "default": false
                },
                "Speed": {
                  "type": "number",
                  "title": "Speed",
                  "description": "Scrolling or animation speed of the foreground layer",
                  "minimum": 0.0,
                  "maximum": 10.0,
                  "multipleOf": 0.01,
                  "default": 0.2
                },
                "Rotation": {
                  "type": "number",
                  "title": "Rotation (degrees)",
                  "description": "Rotation angle in degrees for the foreground layer",
                  "minimum": -360.0,
                  "maximum": 360.0,
                  "multipleOf": 0.01,
                  "default": 0.0
                },
                "LayoutMode": {
                  "type": "string",
                  "title": "Layout Mode",
                  "description": "Rendering layout mode for the foreground layer sprite",
                  "enum": ["TiledSquare", "Stretched", "Fit", "Fill"],
                  "default": "TiledSquare"
                },
                "AspectRatio": {
                  "type": "number",
                  "title": "Aspect Ratio",
                  "description": "Aspect ratio used when rendering the foreground layer",
                  "minimum": 0.1,
                  "maximum": 10.0,
                  "multipleOf": 0.01,
                  "default": 1.0
                }
              }
            },
            "Back": {
              "type": "object",
              "title": "Back Layer",
              "description": "Configuration for the background rendering layer",
              "required": ["SpriteUrl", "ColorHex", "Enabled", "Speed", "Rotation", "LayoutMode", "AspectRatio"],
              "properties": {
                "SpriteUrl": {
                  "type": ["string", "null"],
                  "title": "Sprite URL",
                  "description": "URL of the background layer sprite image",
                  "format": "uri",
                  "default": "https://placehold.co/1024x512/000033/FFFFFF.png?text=BG"
                },
                "ColorHex": {
                  "type": "string",
                  "title": "Color Hex",
                  "description": "Hex color applied to the background layer sprite",
                  "minLength": 7,
                  "maxLength": 9,
                  "default": "#FFFFFF"
                },
                "Enabled": {
                  "type": "boolean",
                  "title": "Enabled",
                  "description": "Whether the background layer is active and visible",
                  "default": true
                },
                "Speed": {
                  "type": "number",
                  "title": "Speed",
                  "description": "Scrolling or animation speed of the background layer",
                  "minimum": 0.0,
                  "maximum": 10.0,
                  "multipleOf": 0.01,
                  "default": 0.05
                },
                "Rotation": {
                  "type": "number",
                  "title": "Rotation (degrees)",
                  "description": "Rotation angle in degrees for the background layer",
                  "minimum": -360.0,
                  "maximum": 360.0,
                  "multipleOf": 0.01,
                  "default": 0.0
                },
                "LayoutMode": {
                  "type": "string",
                  "title": "Layout Mode",
                  "description": "Rendering layout mode for the background layer sprite",
                  "enum": ["TiledSquare", "Stretched", "Fit", "Fill"],
                  "default": "Stretched"
                },
                "AspectRatio": {
                  "type": "number",
                  "title": "Aspect Ratio",
                  "description": "Aspect ratio used when rendering the background layer",
                  "minimum": 0.1,
                  "maximum": 10.0,
                  "multipleOf": 0.01,
                  "default": 1.77
                }
              }
            }
          }
        },
        "colors": {
          "type": "object",
          "title": "Board Colors",
          "description": "Color scheme for the Sudoku board",
          "required": ["selected_hex", "unselected_hex", "text_normal_hex", "text_fixed_hex", "grid_bg_hex", "cell_bg_hex", "btn_bg_hex"],
          "properties": {
            "selected_hex": {
              "type": "string",
              "title": "Selected Cell Color",
              "description": "Color of selected cell (golden by default)",
              "pattern": "^#[0-9A-Fa-f]{6}$",
              "default": "#FFD700"
            },
            "unselected_hex": {
              "type": "string",
              "title": "Unselected Cell Color",
              "pattern": "^#[0-9A-Fa-f]{6}$",
              "default": "#FFFFFF"
            },
            "text_normal_hex": {
              "type": "string",
              "title": "Player Text Color",
              "description": "Color for numbers entered by player",
              "pattern": "^#[0-9A-Fa-f]{6}$",
              "default": "#000000"
            },
            "text_fixed_hex": {
              "type": "string",
              "title": "Fixed Text Color",
              "description": "Color for pre-filled numbers",
              "pattern": "^#[0-9A-Fa-f]{6}$",
              "default": "#000000"
            },
            "grid_bg_hex": {
              "type": "string",
              "title": "Grid Background Color",
              "pattern": "^#[0-9A-Fa-f]{6}$",
              "default": "#FFFFFF"
            },
            "cell_bg_hex": {
              "type": "string",
              "title": "Cell Background Color",
              "pattern": "^#[0-9A-Fa-f]{6}$",
              "default": "#FFFFFF"
            },
            "btn_bg_hex": {
              "type": "string",
              "title": "Button Background Color",
              "pattern": "^#[0-9A-Fa-f]{6}$",
              "default": "#FFFFFF"
            }
          }
        }
      }
    },
    "audio": {
      "type": "object",
      "title": "Audio Configuration",
      "required": ["music_url", "click_url", "error_url", "rocket_url", "whoosh_url", "bomb_url", "victory_url", "lose_url", "win_game_url"],
      "properties": {
        "music_url": {
          "type": ["string", "null"],
          "title": "Background Music URL",
          "format": "uri",
          "default": ""
        },
        "click_url": {
          "type": ["string", "null"],
          "title": "Click Sound URL",
          "description": "Sound when selecting cell or number",
          "format": "uri",
          "default": ""
        },
        "error_url": {
          "type": ["string", "null"],
          "title": "Error Sound URL",
          "description": "Sound when placing incorrect number",
          "format": "uri",
          "default": ""
        },
        "rocket_url": {
          "type": ["string", "null"],
          "title": "Rocket Power-up Sound URL",
          "format": "uri",
          "default": ""
        },
        "whoosh_url": {
          "type": ["string", "null"],
          "title": "Eraser Power-up Sound URL",
          "format": "uri",
          "default": ""
        },
        "bomb_url": {
          "type": ["string", "null"],
          "title": "Bomb Power-up Sound URL",
          "format": "uri",
          "default": ""
        },
        "victory_url": {
          "type": ["string", "null"],
          "title": "Victory Sound URL",
          "format": "uri",
          "default": ""
        },
        "lose_url": {
          "type": ["string", "null"],
          "title": "Defeat Sound URL",
          "format": "uri",
          "default": ""
        },
        "win_game_url": {
          "type": ["string", "null"],
          "title": "Win Game Sound URL",
          "description": "Sound when completing entire game",
          "format": "uri",
          "default": ""
        }
      }
    },
    "texts": {
      "type": "object",
      "title": "UI Texts & Labels",
      "required": ["victory_title", "victory_phrase", "defeat_title", "defeat_phrase", "label_difficulty", "label_time", "label_errors", "label_score"],
      "properties": {
        "victory_title": {
          "type": "string",
          "title": "Victory Title",
          "default": "¡VICTORIA!",
          "minLength": 1,
          "maxLength": 50
        },
        "victory_phrase": {
          "type": "string",
          "title": "Victory Phrase",
          "default": "Nivel Completado",
          "minLength": 1,
          "maxLength": 100
        },
        "defeat_title": {
          "type": "string",
          "title": "Defeat Title",
          "default": "DERROTA",
          "minLength": 1,
          "maxLength": 50
        },
        "defeat_phrase": {
          "type": "string",
          "title": "Defeat Phrase",
          "default": "Inténtalo de nuevo",
          "minLength": 1,
          "maxLength": 100
        },
        "label_difficulty": {
          "type": "string",
          "title": "Difficulty Label",
          "default": "Dificultad",
          "minLength": 1,
          "maxLength": 30
        },
        "label_time": {
          "type": "string",
          "title": "Time Label",
          "default": "Tiempo",
          "minLength": 1,
          "maxLength": 30
        },
        "label_errors": {
          "type": "string",
          "title": "Errors Label",
          "default": "Errores",
          "minLength": 1,
          "maxLength": 30
        },
        "label_score": {
          "type": "string",
          "title": "Score Label",
          "default": "Llaves",
          "minLength": 1,
          "maxLength": 30
        }
      }
    },
    "rewards": {
      "type": "object",
      "title": "Rewards",
      "description": "Coin reward values for player actions",
      "required": ["coins_per_action", "coins_on_completion"],
      "properties": {
        "coins_per_action": {
          "type": "integer",
          "title": "Coins Per Action",
          "description": "Number of coins awarded for each number placed correctly",
          "minimum": 0,
          "maximum": 10000,
          "default": 20
        },
        "coins_on_completion": {
          "type": "integer",
          "title": "Coins On Completion",
          "description": "Number of coins awarded upon successfully completing the puzzle",
          "minimum": 0,
          "maximum": 100000,
          "default": 200
        }
      }
    },
    "personalization": {
      "type": "object",
      "title": "Personalization",
      "description": "Custom visual asset URLs for personalized game UI elements",
      "required": ["coin_url", "coin_count_url"],
      "properties": {
        "coin_url": {
          "type": ["string", "null"],
          "title": "Coin Image URL",
          "description": "URL of the custom coin image displayed in-game",
          "format": "uri",
          "default": "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COIN"
        },
        "coin_count_url": {
          "type": ["string", "null"],
          "title": "Coin Counter Image URL",
          "description": "URL of the custom coin counter UI image",
          "format": "uri",
          "default": "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COUNT"
        }
      }
    },
    "game": {
      "type": "object",
      "title": "Game Assets",
      "description": "Visual assets for game elements",
      "required": ["tiles"],
      "properties": {
        "tiles": {
          "type": "array",
          "title": "Number Tile Images",
          "description": "9 images to replace numbers 1-9",
          "minItems": 9,
          "maxItems": 9,
          "items": {
            "type": "object",
            "required": ["url"],
            "properties": {
              "url": {
                "type": ["string", "null"],
                "title": "Tile Image URL",
                "format": "uri",
                "default": ""
              }
            }
          }
        }
      }
    }
  }
}',
    -- UI SCHEMA (layout and widgets)
    '{
  "ui:order": ["meta", "game_config", "branding", "audio", "texts", "rewards", "personalization", "game"],
  "meta": {
    "ui:title": "Metadata",
    "ui:description": "General brand and configuration metadata",
    "brand_id": {
      "ui:widget": "textInput",
      "ui:title": "Brand ID",
      "ui:placeholder": "e.g. default",
      "ui:help": "Unique identifier assigned to this brand"
    }
  },
  "game_config": {
    "ui:title": "⚙️ Game Configuration",
    "ui:description": "Configure gameplay mechanics and difficulty",
    "ui:color": "blue",
    
    "time_limit": {
      "ui:widget": "numberInput",
      "ui:placeholder": "Tiempo en segundos",
      "ui:help": "0 = sin límite de tiempo"
    },
    "difficulty": {
      "ui:widget": "radio",
      "ui:options": {
        "inline": true
      }
    },
    "max_errors": {
      "ui:widget": "numberInput",
      "ui:placeholder": "Errores permitidos",
      "ui:help": "0 = errores ilimitados"
    },
    "empty_cells": {
      "ui:widget": "numberInput",
      "ui:placeholder": "Celdas vacías",
      "ui:help": "3-32 celdas (más = más difícil)"
    },
    "warning_threshold": {
      "ui:widget": "decimalInput",
      "ui:placeholder": "0.15",
      "ui:options": {
        "decimalPlaces": 2,
        "suffix": ""
      },
      "ui:help": "0.15 = advertencia al 15% del tiempo restante"
    },
    
    "use_countdown": {
      "ui:widget": "checkbox"
    },
    "enable_powerups": {
      "ui:widget": "checkbox"
    },
    "levels": {
      "ui:title": "Levels",
      "ui:description": "Configure per-level difficulty overrides",
      "items": {
        "ui:order": ["id", "empty_cells", "time_limit", "max_errors", "use_countdown", "enable_powerups"],
        "id": {
          "ui:widget": "numberInput",
          "ui:placeholder": "1"
        },
        "empty_cells": {
          "ui:widget": "numberInput",
          "ui:placeholder": "12"
        },
        "time_limit": {
          "ui:widget": "numberInput",
          "ui:placeholder": "300"
        },
        "max_errors": {
          "ui:widget": "numberInput",
          "ui:placeholder": "3"
        },
        "use_countdown": {
          "ui:widget": "checkbox"
        },
        "enable_powerups": {
          "ui:widget": "checkbox"
        }
      }
    }
  },
  "branding": {
    "ui:title": "🎨 Branding & Visuals",
    "ui:color": "purple",
    
    "images": {
      "ui:title": "Images",
      
      "main_logo_url": {
        "ui:widget": "assetUpload",
        "ui:options": {
          "assetType": "image"
        }
      },
      "main_logo_offset_y": {
        "ui:widget": "decimalInput",
        "ui:placeholder": "0.0",
        "ui:options": {
          "decimalPlaces": 1,
          "suffix": ""
        }
      },
      "logo_watermark_url": {
        "ui:widget": "assetUpload",
        "ui:options": {
          "assetType": "image"
        }
      },
      "logo_watermark_offset_y": {
        "ui:widget": "decimalInput",
        "ui:placeholder": "0.0",
        "ui:options": {
          "decimalPlaces": 1,
          "suffix": ""
        }
      },
      "background_url": {
        "ui:widget": "assetUpload",
        "ui:options": {
          "assetType": "image"
        }
      },
      "background_color_hex": {
        "ui:widget": "colorPicker"
      },
      "cell_background_url": {
        "ui:widget": "assetUpload",
        "ui:options": {
          "assetType": "image"
        }
      },
      "button_background_url": {
        "ui:widget": "assetUpload",
        "ui:options": {
          "assetType": "image"
        }
      },
      "bomb_url": {
        "ui:widget": "assetUpload",
        "ui:title": "Bomb Power-up Icon",
        "ui:help": "Icon shown for the bomb (clear-cell) power-up",
        "ui:options": {
          "assetType": "image"
        }
      },
      "horizontal_url": {
        "ui:widget": "assetUpload",
        "ui:title": "Horizontal Clear Icon",
        "ui:help": "Icon shown for the horizontal row-clear power-up",
        "ui:options": {
          "assetType": "image"
        }
      },
      "vertical_url": {
        "ui:widget": "assetUpload",
        "ui:title": "Vertical Clear Icon",
        "ui:help": "Icon shown for the vertical column-clear power-up",
        "ui:options": {
          "assetType": "image"
        }
      }
    },
    "background_config": {
      "ui:title": "Background Layers",
      "ui:description": "Configure the Front and Back background rendering layers",
      "Front": {
        "ui:title": "Front Layer",
        "SpriteUrl": {
          "ui:widget": "assetUpload",
          "ui:title": "Front Layer Sprite",
          "ui:options": {
            "assetType": "image"
          }
        },
        "ColorHex": {
          "ui:widget": "colorPicker",
          "ui:title": "Front Layer Color (Hex)"
        },
        "Enabled": {
          "ui:widget": "checkbox",
          "ui:title": "Enable Front Layer"
        },
        "Speed": {
          "ui:widget": "decimalInput",
          "ui:title": "Speed",
          "ui:placeholder": "0.2"
        },
        "Rotation": {
          "ui:widget": "decimalInput",
          "ui:title": "Rotation (degrees)",
          "ui:placeholder": "0.0"
        },
        "LayoutMode": {
          "ui:widget": "radio",
          "ui:title": "Layout Mode"
        },
        "AspectRatio": {
          "ui:widget": "decimalInput",
          "ui:title": "Aspect Ratio",
          "ui:placeholder": "1.0"
        }
      },
      "Back": {
        "ui:title": "Back Layer",
        "SpriteUrl": {
          "ui:widget": "assetUpload",
          "ui:title": "Back Layer Sprite",
          "ui:options": {
            "assetType": "image"
          }
        },
        "ColorHex": {
          "ui:widget": "colorPicker",
          "ui:title": "Back Layer Color (Hex)"
        },
        "Enabled": {
          "ui:widget": "checkbox",
          "ui:title": "Enable Back Layer"
        },
        "Speed": {
          "ui:widget": "decimalInput",
          "ui:title": "Speed",
          "ui:placeholder": "0.05"
        },
        "Rotation": {
          "ui:widget": "decimalInput",
          "ui:title": "Rotation (degrees)",
          "ui:placeholder": "0.0"
        },
        "LayoutMode": {
          "ui:widget": "radio",
          "ui:title": "Layout Mode"
        },
        "AspectRatio": {
          "ui:widget": "decimalInput",
          "ui:title": "Aspect Ratio",
          "ui:placeholder": "1.77"
        }
      }
    },
    "colors": {
      "ui:title": "Board Colors",
      "selected_hex": {
        "ui:widget": "colorPicker"
      },
      "unselected_hex": {
        "ui:widget": "colorPicker"
      },
      "text_normal_hex": {
        "ui:widget": "colorPicker"
      },
      "text_fixed_hex": {
        "ui:widget": "colorPicker"
      },
      "grid_bg_hex": {
        "ui:widget": "colorPicker"
      },
      "cell_bg_hex": {
        "ui:widget": "colorPicker"
      },
      "btn_bg_hex": {
        "ui:widget": "colorPicker"
      }
    }
  },
  "audio": {
    "ui:title": "🔊 Audio",
    "ui:color": "green",
    "music_url": {
      "ui:widget": "assetUpload",
      "ui:options": {
        "assetType": "audio"
      }
    },
    "click_url": {
      "ui:widget": "assetUpload",
      "ui:options": {
        "assetType": "audio"
      }
    },
    "error_url": {
      "ui:widget": "assetUpload",
      "ui:options": {
        "assetType": "audio"
      }
    },
    "rocket_url": {
      "ui:widget": "assetUpload",
      "ui:options": {
        "assetType": "audio"
      },
      "ui:help": "Sound for Rocket power-up"
    },
    "whoosh_url": {
      "ui:widget": "assetUpload",
      "ui:options": {
        "assetType": "audio"
      },
      "ui:help": "Sound for Eraser power-up"
    },
    "bomb_url": {
      "ui:widget": "assetUpload",
      "ui:options": {
        "assetType": "audio"
      },
      "ui:help": "Sound for Bomb power-up"
    },
    "victory_url": {
      "ui:widget": "assetUpload",
      "ui:options": {
        "assetType": "audio"
      }
    },
    "lose_url": {
      "ui:widget": "assetUpload",
      "ui:options": {
        "assetType": "audio"
      }
    },
    "win_game_url": {
      "ui:widget": "assetUpload",
      "ui:options": {
        "assetType": "audio"
      },
      "ui:help": "Sound when completing entire game"
    }
  },
  "texts": {
    "ui:title": "📝 UI Texts",
    "ui:description": "Customize all text labels and messages",
    "ui:color": "orange",
    
    "victory_title": {
      "ui:widget": "textInput",
      "ui:placeholder": "¡VICTORIA!"
    },
    "victory_phrase": {
      "ui:widget": "textInput",
      "ui:placeholder": "Nivel Completado"
    },
    "defeat_title": {
      "ui:widget": "textInput",
      "ui:placeholder": "DERROTA"
    },
    "defeat_phrase": {
      "ui:widget": "textInput",
      "ui:placeholder": "Inténtalo de nuevo"
    },
    "label_difficulty": {
      "ui:widget": "textInput",
      "ui:placeholder": "Dificultad"
    },
    "label_time": {
      "ui:widget": "textInput",
      "ui:placeholder": "Tiempo"
    },
    "label_errors": {
      "ui:widget": "textInput",
      "ui:placeholder": "Errores"
    },
    "label_score": {
      "ui:widget": "textInput",
      "ui:placeholder": "Llaves"
    }
  },
  "rewards": {
    "ui:title": "🏆 Rewards",
    "ui:description": "Coin reward values for player actions",
    "ui:color": "yellow",
    "coins_per_action": {
      "ui:widget": "numberInput",
      "ui:title": "Coins Per Action",
      "ui:placeholder": "e.g. 20",
      "ui:help": "Coins awarded for each number placed correctly"
    },
    "coins_on_completion": {
      "ui:widget": "numberInput",
      "ui:title": "Coins On Completion",
      "ui:placeholder": "e.g. 200",
      "ui:help": "Coins awarded upon successfully completing the puzzle"
    }
  },
  "personalization": {
    "ui:title": "✨ Personalization",
    "ui:description": "Custom visual assets for personalized game UI elements",
    "coin_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Coin Image",
      "ui:help": "Upload or provide URL for the custom coin image displayed in-game",
      "ui:options": { "assetType": "image" }
    },
    "coin_count_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Coin Counter Image",
      "ui:help": "Upload or provide URL for the custom coin counter UI image",
      "ui:options": { "assetType": "image" }
    }
  },
  "game": {
    "ui:title": "🎮 Game Assets",
    "ui:color": "indigo",
    
    "tiles": {
      "ui:title": "Number Tiles (1-9)",
      "ui:description": "Upload 9 images to replace numbers 1-9 on the board",
      "ui:options": {
        "orderable": false
      },
      "items": {
        "url": {
          "ui:widget": "assetUpload",
          "ui:options": {
            "assetType": "image"
          }
        }
      }
    }
  }
}',
    true,  -- active
    true,  -- is_latest
    NOW(),
    'system',
    15000,
    5000,
    20000,
    1,
    60
WHERE NOT EXISTS (
    SELECT 1 FROM game_config_definitions 
    WHERE game_id = 8 AND version = 1
);