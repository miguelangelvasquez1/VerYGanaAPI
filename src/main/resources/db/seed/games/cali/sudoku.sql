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
    created_by
)
SELECT
    8,
    1,
    -- JSON SCHEMA (validation rules)
    '{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Sudoku Game Configuration",
  "type": "object",
  "required": ["game_config", "branding", "audio", "texts", "game"],
  "properties": {
    "game_config": {
      "type": "object",
      "title": "Game Configuration",
      "description": "Core gameplay settings for Sudoku",
      "required": ["time_limit", "difficulty", "max_errors", "empty_cells", "warning_threshold", "use_countdown"],
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
          "required": ["main_logo_url", "main_logo_offset_y", "logo_watermark_url", "logo_watermark_offset_y", "background_url", "background_color_hex", "cell_background_url", "button_background_url"],
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
            }
          }
        },
        "background_config": {
          "type": "object",
          "title": "Background Configuration",
          "required": ["Front", "Back"],
          "properties": {
            "Front": {
              "type": "object",
              "title": "Front Layer",
              "required": ["url", "offset_x", "offset_y", "scale_x", "scale_y", "rotation"],
              "properties": {
                "url": {
                  "type": ["string", "null"],
                  "title": "Front Layer URL",
                  "format": "uri",
                  "default": ""
                },
                "offset_x": {
                  "type": "number",
                  "title": "X Offset",
                  "minimum": -5.0,
                  "maximum": 5.0,
                  "default": 0.0
                },
                "offset_y": {
                  "type": "number",
                  "title": "Y Offset",
                  "minimum": -5.0,
                  "maximum": 5.0,
                  "default": 0.0
                },
                "scale_x": {
                  "type": "number",
                  "title": "X Scale",
                  "minimum": 0.1,
                  "maximum": 10.0,
                  "multipleOf": 0.1,
                  "default": 1.0
                },
                "scale_y": {
                  "type": "number",
                  "title": "Y Scale",
                  "minimum": 0.1,
                  "maximum": 10.0,
                  "multipleOf": 0.1,
                  "default": 1.0
                },
                "rotation": {
                  "type": "integer",
                  "title": "Rotation",
                  "description": "Rotation in degrees",
                  "minimum": 0,
                  "maximum": 360,
                  "default": 0
                }
              }
            },
            "Back": {
              "type": "object",
              "title": "Back Layer",
              "required": ["url", "offset_x", "offset_y", "scale_x", "scale_y", "rotation"],
              "properties": {
                "url": {
                  "type": ["string", "null"],
                  "title": "Back Layer URL",
                  "format": "uri",
                  "default": ""
                },
                "offset_x": {
                  "type": "number",
                  "title": "X Offset",
                  "minimum": -5.0,
                  "maximum": 5.0,
                  "default": 0.0
                },
                "offset_y": {
                  "type": "number",
                  "title": "Y Offset",
                  "minimum": -5.0,
                  "maximum": 5.0,
                  "default": 0.0
                },
                "scale_x": {
                  "type": "number",
                  "title": "X Scale",
                  "minimum": 0.1,
                  "maximum": 10.0,
                  "multipleOf": 0.1,
                  "default": 1.0
                },
                "scale_y": {
                  "type": "number",
                  "title": "Y Scale",
                  "minimum": 0.1,
                  "maximum": 10.0,
                  "multipleOf": 0.1,
                  "default": 1.0
                },
                "rotation": {
                  "type": "number",
                  "title": "Rotation",
                  "description": "Rotation in degrees",
                  "minimum": 0,
                  "maximum": 360,
                  "default": 0
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
  "ui:order": ["game_config", "branding", "audio", "texts", "game"],
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
      }
    },
    "background_config": {
      "ui:title": "Background Layers",
      "Front": {
        "ui:title": "Front Layer",
        "url": {
          "ui:widget": "assetUpload",
          "ui:options": {
            "assetType": "image"
          }
        },
        "offset_x": {
          "ui:widget": "decimalInput",
          "ui:placeholder": "0.0",
          "ui:options": {
            "decimalPlaces": 1,
            "suffix": ""
          }
        },
        "offset_y": {
          "ui:widget": "decimalInput",
          "ui:placeholder": "0.0",
          "ui:options": {
            "decimalPlaces": 1,
            "suffix": ""
          }
        },
        "scale_x": {
          "ui:widget": "decimalInput",
          "ui:placeholder": "1.0",
          "ui:options": {
            "decimalPlaces": 1,
            "suffix": "x"
          }
        },
        "scale_y": {
          "ui:widget": "decimalInput",
          "ui:placeholder": "1.0",
          "ui:options": {
            "decimalPlaces": 1,
            "suffix": "x"
          }
        },
        "rotation": {
          "ui:widget": "numberInput",
          "ui:placeholder": "0",
          "ui:help": "Rotación en grados (0-360)"
        }
      },
      "Back": {
        "ui:title": "Back Layer",
        "url": {
          "ui:widget": "assetUpload",
          "ui:options": {
            "assetType": "image"
          }
        },
        "offset_x": {
          "ui:widget": "decimalInput",
          "ui:placeholder": "0.0",
          "ui:options": {
            "decimalPlaces": 1,
            "suffix": ""
          }
        },
        "offset_y": {
          "ui:widget": "decimalInput",
          "ui:placeholder": "0.0",
          "ui:options": {
            "decimalPlaces": 1,
            "suffix": ""
          }
        },
        "scale_x": {
          "ui:widget": "decimalInput",
          "ui:placeholder": "1.0",
          "ui:options": {
            "decimalPlaces": 1,
            "suffix": "x"
          }
        },
        "scale_y": {
          "ui:widget": "decimalInput",
          "ui:placeholder": "1.0",
          "ui:options": {
            "decimalPlaces": 1,
            "suffix": "x"
          }
        },
        "rotation": {
          "ui:widget": "numberInput",
          "ui:placeholder": "0",
          "ui:help": "Rotación en grados (0-360)"
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
    'system'
WHERE NOT EXISTS (
    SELECT 1 FROM game_config_definitions 
    WHERE game_id = 8 AND version = 1
);