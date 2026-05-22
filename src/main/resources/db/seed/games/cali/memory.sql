INSERT INTO games (id, title, description, delivery_type, url, front_page_url, active, created_at, updated_at)
SELECT
		 7,
    'Memory',
    'El objetivo es recordar la ubicación de las fichas para emparejarlas y acumular la mayor cantidad de pares posible.',
    'QUERY',
    'Memory',
    'https://games.verygana.com/game_icons/cali/memory.png',
     true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM games WHERE id = 7);


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
    7,
    1,
    '{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "title": "Game Configuration",
  "description": "Full configuration for a memory card matching game",
  "required": ["meta", "game_config", "branding", "audio", "texts", "rewards", "personalization", "game"],
  "properties": {
    "meta": {
      "type": "object",
      "title": "Meta",
      "description": "Metadata about the game brand",
      "required": ["brand_id"],
      "properties": {
        "brand_id": {
          "type": "string",
          "title": "Brand ID",
          "description": "Identifier for the brand configuration",
          "default": "default",
          "minLength": 1,
          "maxLength": 64
        }
      }
    },
    "game_config": {
      "type": "object",
      "title": "Game Config",
      "description": "Core gameplay parameters",
      "required": ["start_pairs", "final_pairs", "game_duration", "bonus_per_pair", "preview_duration", "enable_lives", "max_lives", "enable_powerups", "power_up_chance", "bonus_time_amount", "levels"],
      "properties": {
        "start_pairs": {
          "type": "integer",
          "title": "Start Pairs",
          "description": "Number of card pairs at the beginning of the game",
          "default": 2,
          "minimum": 1,
          "maximum": 20
        },
        "final_pairs": {
          "type": "integer",
          "title": "Final Pairs",
          "description": "Maximum number of card pairs in the final stage",
          "default": 10,
          "minimum": 1,
          "maximum": 50
        },
        "game_duration": {
          "type": "integer",
          "title": "Game Duration (seconds)",
          "description": "Total time allowed for the game in seconds",
          "default": 120,
          "minimum": 10,
          "maximum": 600
        },
        "bonus_per_pair": {
          "type": "integer",
          "title": "Bonus Per Pair",
          "description": "Bonus seconds awarded for each matched pair",
          "default": 5,
          "minimum": 0,
          "maximum": 60
        },
        "preview_duration": {
          "type": "integer",
          "title": "Preview Duration (seconds)",
          "description": "Seconds cards are shown face-up before game starts",
          "default": 2,
          "minimum": 0,
          "maximum": 30
        },
        "enable_lives": {
          "type": "boolean",
          "title": "Enable Lives",
          "description": "Whether the lives system is active",
          "default": true
        },
        "max_lives": {
          "type": "integer",
          "title": "Max Lives",
          "description": "Maximum number of lives the player starts with",
          "default": 3,
          "minimum": 1,
          "maximum": 10
        },
        "enable_powerups": {
          "type": "boolean",
          "title": "Enable Power-ups",
          "description": "Whether power-ups can appear during the game",
          "default": true
        },
        "power_up_chance": {
          "type": "number",
          "title": "Power-up Chance",
          "description": "Global probability of a power-up appearing (0.0 to 1.0)",
          "default": 0.3,
          "minimum": 0.0,
          "maximum": 1.0,
          "multipleOf": 0.01
        },
        "bonus_time_amount": {
          "type": "integer",
          "title": "Bonus Time Amount (seconds)",
          "description": "Seconds added when a time power-up is collected",
          "default": 15,
          "minimum": 1,
          "maximum": 120
        },
        "levels": {
          "type": "array",
          "title": "Levels",
          "description": "List of level configurations",
          "items": {
            "type": "object",
            "title": "Level",
            "required": ["id", "stage_name", "pair_count", "power_up_chance", "weight_life", "weight_time", "weight_hint"],
            "properties": {
              "id": {
                "type": "integer",
                "title": "Level ID",
                "description": "Unique identifier for the level",
                "minimum": 1
              },
              "stage_name": {
                "type": "string",
                "title": "Stage Name",
                "description": "Display name of the level",
                "minLength": 1,
                "maxLength": 64
              },
              "pair_count": {
                "type": "integer",
                "title": "Pair Count",
                "description": "Number of card pairs in this level",
                "minimum": 1,
                "maximum": 50
              },
              "power_up_chance": {
                "type": "number",
                "title": "Power-up Chance",
                "description": "Probability of a power-up appearing in this level (0.0 to 1.0)",
                "minimum": 0.0,
                "maximum": 1.0,
                "multipleOf": 0.01
              },
              "weight_life": {
                "type": "number",
                "title": "Weight: Life Power-up",
                "description": "Relative weight for life power-up spawn probability",
                "minimum": 0.0,
                "maximum": 10.0,
                "multipleOf": 0.1
              },
              "weight_time": {
                "type": "number",
                "title": "Weight: Time Power-up",
                "description": "Relative weight for time power-up spawn probability",
                "minimum": 0.0,
                "maximum": 10.0,
                "multipleOf": 0.1
              },
              "weight_hint": {
                "type": "number",
                "title": "Weight: Hint Power-up",
                "description": "Relative weight for hint power-up spawn probability",
                "minimum": 0.0,
                "maximum": 10.0,
                "multipleOf": 0.1
              }
            }
          }
        }
      }
    },
    "branding": {
      "type": "object",
      "title": "Branding",
      "description": "Visual identity assets and background configuration",
      "required": ["images", "background_config"],
      "properties": {
        "images": {
          "type": "object",
          "title": "Images",
          "description": "URLs for all branding images",
          "required": ["main_logo_url", "main_logo_offset_y", "logo_watermark_url", "logo_watermark_offset_y", "card_back_url", "icon_life_url", "icon_hint_url", "icon_time_url"],
          "properties": {
            "main_logo_url": {
              "type": ["string", "null"],
              "title": "Main Logo URL",
              "description": "URL of the main game logo image",
              "format": "uri",
              "default": ""
            },
            "main_logo_offset_y": {
              "type": "number",
              "title": "Main Logo Offset Y",
              "description": "Vertical offset for the main logo",
              "default": 0.0,
              "minimum": -500.0,
              "maximum": 500.0,
              "multipleOf": 0.1
            },
            "logo_watermark_url": {
              "type": ["string", "null"],
              "title": "Watermark Logo URL",
              "description": "URL of the watermark logo image",
              "format": "uri",
              "default": ""
            },
            "logo_watermark_offset_y": {
              "type": "number",
              "title": "Watermark Logo Offset Y",
              "description": "Vertical offset for the watermark logo",
              "default": 0.0,
              "minimum": -500.0,
              "maximum": 500.0,
              "multipleOf": 0.1
            },
            "card_back_url": {
              "type": ["string", "null"],
              "title": "Card Back URL",
              "description": "URL of the card back image",
              "format": "uri",
              "default": ""
            },
            "icon_life_url": {
              "type": ["string", "null"],
              "title": "Life Icon URL",
              "description": "URL of the life power-up icon",
              "format": "uri",
              "default": ""
            },
            "icon_hint_url": {
              "type": ["string", "null"],
              "title": "Hint Icon URL",
              "description": "URL of the hint power-up icon",
              "format": "uri",
              "default": ""
            },
            "icon_time_url": {
              "type": ["string", "null"],
              "title": "Time Icon URL",
              "description": "URL of the time power-up icon",
              "format": "uri",
              "default": ""
            }
          }
        },
        "background_config": {
          "type": "object",
          "title": "Background Config",
          "description": "Front and back background layer settings",
          "required": ["Front", "Back"],
          "properties": {
            "Front": {
              "type": "object",
              "title": "Front Layer",
              "description": "Foreground background layer configuration",
              "required": ["SpriteUrl", "ColorHex", "Enabled", "Speed", "Rotation", "LayoutMode", "AspectRatio"],
              "properties": {
                "SpriteUrl": {
                  "type": ["string", "null"],
                  "title": "Sprite URL",
                  "description": "URL of the front background sprite",
                  "format": "uri",
                  "default": ""
                },
                "ColorHex": {
                  "type": "string",
                  "title": "Color (Hex)",
                  "description": "Tint color in hexadecimal format",
                  "default": "#FFFFFF",
                  "minLength": 4,
                  "maxLength": 9
                },
                "Enabled": {
                  "type": "boolean",
                  "title": "Enabled",
                  "description": "Whether this background layer is active",
                  "default": true
                },
                "Speed": {
                  "type": "number",
                  "title": "Scroll Speed",
                  "description": "Scrolling speed of the background layer",
                  "default": 0.2,
                  "minimum": 0.0,
                  "maximum": 10.0,
                  "multipleOf": 0.01
                },
                "Rotation": {
                  "type": "number",
                  "title": "Rotation",
                  "description": "Rotation angle of the background sprite in degrees",
                  "default": 0.0,
                  "minimum": -360.0,
                  "maximum": 360.0,
                  "multipleOf": 0.1
                },
                "LayoutMode": {
                  "type": "string",
                  "title": "Layout Mode",
                  "description": "How the background sprite is laid out",
                  "default": "TiledSquare",
                  "enum": ["TiledSquare", "Stretched", "Centered", "TiledFit"]
                },
                "AspectRatio": {
                  "type": "number",
                  "title": "Aspect Ratio",
                  "description": "Width-to-height aspect ratio of the sprite",
                  "default": 1.0,
                  "minimum": 0.1,
                  "maximum": 10.0,
                  "multipleOf": 0.01
                }
              }
            },
            "Back": {
              "type": "object",
              "title": "Back Layer",
              "description": "Background background layer configuration",
              "required": ["SpriteUrl", "ColorHex", "Enabled", "Speed", "Rotation", "LayoutMode", "AspectRatio"],
              "properties": {
                "SpriteUrl": {
                  "type": ["string", "null"],
                  "title": "Sprite URL",
                  "description": "URL of the back background sprite",
                  "format": "uri",
                  "default": ""
                },
                "ColorHex": {
                  "type": "string",
                  "title": "Color (Hex)",
                  "description": "Tint color in hexadecimal format",
                  "default": "#FFFFFF",
                  "minLength": 4,
                  "maxLength": 9
                },
                "Enabled": {
                  "type": "boolean",
                  "title": "Enabled",
                  "description": "Whether this background layer is active",
                  "default": true
                },
                "Speed": {
                  "type": "number",
                  "title": "Scroll Speed",
                  "description": "Scrolling speed of the background layer",
                  "default": 0.05,
                  "minimum": 0.0,
                  "maximum": 10.0,
                  "multipleOf": 0.01
                },
                "Rotation": {
                  "type": "number",
                  "title": "Rotation",
                  "description": "Rotation angle of the background sprite in degrees",
                  "default": 0.0,
                  "minimum": -360.0,
                  "maximum": 360.0,
                  "multipleOf": 0.1
                },
                "LayoutMode": {
                  "type": "string",
                  "title": "Layout Mode",
                  "description": "How the background sprite is laid out",
                  "default": "Stretched",
                  "enum": ["TiledSquare", "Stretched", "Centered", "TiledFit"]
                },
                "AspectRatio": {
                  "type": "number",
                  "title": "Aspect Ratio",
                  "description": "Width-to-height aspect ratio of the sprite",
                  "default": 1.77,
                  "minimum": 0.1,
                  "maximum": 10.0,
                  "multipleOf": 0.01
                }
              }
            }
          }
        }
      }
    },
    "audio": {
      "type": "object",
      "title": "Audio",
      "description": "URLs for all game audio assets",
      "required": ["music_url", "flip_url", "success_url", "error_url", "powerup_url", "victory_url", "lose_url"],
      "properties": {
        "music_url": {
          "type": ["string", "null"],
          "title": "Background Music URL",
          "description": "URL of the background music track",
          "format": "uri",
          "default": ""
        },
        "flip_url": {
          "type": ["string", "null"],
          "title": "Card Flip Sound URL",
          "description": "URL of the card flip sound effect",
          "format": "uri",
          "default": ""
        },
        "success_url": {
          "type": ["string", "null"],
          "title": "Match Success Sound URL",
          "description": "URL of the sound played on a successful match",
          "format": "uri",
          "default": ""
        },
        "error_url": {
          "type": ["string", "null"],
          "title": "Match Error Sound URL",
          "description": "URL of the sound played on a failed match",
          "format": "uri",
          "default": ""
        },
        "powerup_url": {
          "type": ["string", "null"],
          "title": "Power-up Sound URL",
          "description": "URL of the sound played when a power-up is collected",
          "format": "uri",
          "default": ""
        },
        "victory_url": {
          "type": ["string", "null"],
          "title": "Victory Sound URL",
          "description": "URL of the sound played on game victory",
          "format": "uri",
          "default": ""
        },
        "lose_url": {
          "type": ["string", "null"],
          "title": "Lose Sound URL",
          "description": "URL of the sound played on game over",
          "format": "uri",
          "default": ""
        }
      }
    },
    "texts": {
      "type": "object",
      "title": "Texts",
      "description": "Display text and labels used throughout the game UI",
      "required": ["victory_title", "victory_phrase", "defeat_title", "defeat_phrase", "label_time", "label_attempts", "label_score", "label_level"],
      "properties": {
        "victory_title": {
          "type": "string",
          "title": "Victory Title",
          "description": "Headline shown on the victory screen",
          "default": "¡EXCELENTE!",
          "minLength": 1,
          "maxLength": 64
        },
        "victory_phrase": {
          "type": "string",
          "title": "Victory Phrase",
          "description": "Subtitle shown on the victory screen",
          "default": "Has completado todos los niveles.",
          "minLength": 1,
          "maxLength": 256
        },
        "defeat_title": {
          "type": "string",
          "title": "Defeat Title",
          "description": "Headline shown on the game over screen",
          "default": "GAME OVER",
          "minLength": 1,
          "maxLength": 64
        },
        "defeat_phrase": {
          "type": "string",
          "title": "Defeat Phrase",
          "description": "Subtitle shown on the game over screen",
          "default": "¡Inténtalo de nuevo!",
          "minLength": 1,
          "maxLength": 256
        },
        "label_time": {
          "type": "string",
          "title": "Time Label",
          "description": "Label for the time counter in the HUD",
          "default": "Tiempo",
          "minLength": 1,
          "maxLength": 32
        },
        "label_attempts": {
          "type": "string",
          "title": "Attempts Label",
          "description": "Label for the attempts counter in the HUD",
          "default": "Intentos",
          "minLength": 1,
          "maxLength": 32
        },
        "label_score": {
          "type": "string",
          "title": "Score Label",
          "description": "Label for the score counter in the HUD",
          "default": "Llaves",
          "minLength": 1,
          "maxLength": 32
        },
        "label_level": {
          "type": "string",
          "title": "Level Label",
          "description": "Label for the level indicator in the HUD",
          "default": "Nivel",
          "minLength": 1,
          "maxLength": 32
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
          "description": "Coins awarded for each successful in-game action",
          "default": 10,
          "minimum": 0,
          "maximum": 10000
        },
        "coins_on_completion": {
          "type": "integer",
          "title": "Coins on Completion",
          "description": "Coins awarded upon completing the game",
          "default": 100,
          "minimum": 0,
          "maximum": 100000
        }
      }
    },
    "personalization": {
      "type": "object",
      "title": "Personalization",
      "description": "Coin icon image assets",
      "required": ["coin_url", "coin_count_url"],
      "properties": {
        "coin_url": {
          "type": ["string", "null"],
          "title": "Coin Icon URL",
          "description": "URL of the coin icon image",
          "format": "uri",
          "default": ""
        },
        "coin_count_url": {
          "type": ["string", "null"],
          "title": "Coin Count Icon URL",
          "description": "URL of the coin count display icon",
          "format": "uri",
          "default": ""
        }
      }
    },
    "game": {
      "type": "object",
      "title": "Game",
      "description": "Card image assets used in gameplay",
      "required": ["card_images"],
      "properties": {
        "card_images": {
          "type": "array",
          "title": "Card Images",
          "description": "List of card face images used in the game",
          "items": {
            "type": "object",
            "title": "Card Image",
            "required": ["url"],
            "properties": {
              "url": {
                "type": ["string", "null"],
                "title": "Image URL",
                "description": "URL of the card face image",
                "format": "uri"
              }
            }
          }
        }
      }
    }
  }
}',
    '{
  "ui:order": ["meta", "game_config", "branding", "audio", "texts", "rewards", "personalization", "game"],
  "meta": {
    "ui:title": "Meta",
    "ui:description": "Brand identity metadata",
    "brand_id": {
      "ui:widget": "textInput",
      "ui:placeholder": "e.g. default",
      "ui:help": "Unique identifier for this brand configuration"
    }
  },
  "game_config": {
    "ui:title": "Game Configuration",
    "ui:description": "Core gameplay parameters and level definitions",
    "ui:order": ["start_pairs", "final_pairs", "game_duration", "bonus_per_pair", "preview_duration", "enable_lives", "max_lives", "enable_powerups", "power_up_chance", "bonus_time_amount", "levels"],
    "start_pairs": {
      "ui:widget": "numberInput",
      "ui:placeholder": "2",
      "ui:help": "Number of card pairs shown at game start"
    },
    "final_pairs": {
      "ui:widget": "numberInput",
      "ui:placeholder": "10",
      "ui:help": "Maximum card pairs reached in the final level"
    },
    "game_duration": {
      "ui:widget": "numberInput",
      "ui:placeholder": "120",
      "ui:help": "Total game time in seconds"
    },
    "bonus_per_pair": {
      "ui:widget": "numberInput",
      "ui:placeholder": "5",
      "ui:help": "Extra seconds granted for each matched pair"
    },
    "preview_duration": {
      "ui:widget": "numberInput",
      "ui:placeholder": "2",
      "ui:help": "How long cards are shown face-up before the round begins"
    },
    "enable_lives": {
      "ui:widget": "checkbox",
      "ui:help": "Enable or disable the lives system"
    },
    "max_lives": {
      "ui:widget": "numberInput",
      "ui:placeholder": "3",
      "ui:help": "Starting number of lives for the player"
    },
    "enable_powerups": {
      "ui:widget": "checkbox",
      "ui:help": "Allow power-ups to spawn during gameplay"
    },
    "power_up_chance": {
      "ui:widget": "decimalInput",
      "ui:placeholder": "0.3",
      "ui:help": "Global probability for a power-up to appear (0.0 = never, 1.0 = always)"
    },
    "bonus_time_amount": {
      "ui:widget": "numberInput",
      "ui:placeholder": "15",
      "ui:help": "Seconds added to the timer when a time power-up is collected"
    },
    "levels": {
      "ui:title": "Levels",
      "ui:description": "Define each game level and its specific parameters",
      "items": {
        "ui:order": ["id", "stage_name", "pair_count", "power_up_chance", "weight_life", "weight_time", "weight_hint"],
        "id": {
          "ui:widget": "numberInput",
          "ui:placeholder": "1"
        },
        "stage_name": {
          "ui:widget": "textInput",
          "ui:placeholder": "e.g. Nivel 1"
        },
        "pair_count": {
          "ui:widget": "numberInput",
          "ui:placeholder": "3",
          "ui:help": "Card pairs to display in this level"
        },
        "power_up_chance": {
          "ui:widget": "decimalInput",
          "ui:placeholder": "0.3",
          "ui:help": "Power-up spawn probability for this level (0.0–1.0)"
        },
        "weight_life": {
          "ui:widget": "decimalInput",
          "ui:placeholder": "1.0",
          "ui:help": "Relative probability of a life power-up spawning"
        },
        "weight_time": {
          "ui:widget": "decimalInput",
          "ui:placeholder": "1.0",
          "ui:help": "Relative probability of a time power-up spawning"
        },
        "weight_hint": {
          "ui:widget": "decimalInput",
          "ui:placeholder": "1.0",
          "ui:help": "Relative probability of a hint power-up spawning"
        }
      }
    }
  },
  "branding": {
    "ui:title": "Branding",
    "ui:description": "Visual assets and background layer configuration",
    "images": {
      "ui:title": "Brand Images",
      "ui:order": ["main_logo_url", "main_logo_offset_y", "logo_watermark_url", "logo_watermark_offset_y", "card_back_url", "icon_life_url", "icon_hint_url", "icon_time_url"],
      "main_logo_url": {
        "ui:widget": "assetUpload",
        "ui:options": { "assetType": "image" },
        "ui:help": "Main logo displayed on the game screen"
      },
      "main_logo_offset_y": {
        "ui:widget": "decimalInput",
        "ui:placeholder": "0.0",
        "ui:help": "Vertical position offset for the main logo"
      },
      "logo_watermark_url": {
        "ui:widget": "assetUpload",
        "ui:options": { "assetType": "image" },
        "ui:help": "Small watermark logo overlaid on the game"
      },
      "logo_watermark_offset_y": {
        "ui:widget": "decimalInput",
        "ui:placeholder": "0.0",
        "ui:help": "Vertical position offset for the watermark logo"
      },
      "card_back_url": {
        "ui:widget": "assetUpload",
        "ui:options": { "assetType": "image" },
        "ui:help": "Image shown on the back of all cards"
      },
      "icon_life_url": {
        "ui:widget": "assetUpload",
        "ui:options": { "assetType": "image" },
        "ui:help": "Icon for the life power-up"
      },
      "icon_hint_url": {
        "ui:widget": "assetUpload",
        "ui:options": { "assetType": "image" },
        "ui:help": "Icon for the hint power-up"
      },
      "icon_time_url": {
        "ui:widget": "assetUpload",
        "ui:options": { "assetType": "image" },
        "ui:help": "Icon for the time power-up"
      }
    },
    "background_config": {
      "ui:title": "Background Layers",
      "Front": {
        "ui:title": "Front Layer",
        "ui:order": ["SpriteUrl", "ColorHex", "Enabled", "Speed", "Rotation", "LayoutMode", "AspectRatio"],
        "SpriteUrl": {
          "ui:widget": "assetUpload",
          "ui:options": { "assetType": "image" },
          "ui:help": "Sprite for the foreground background layer (leave empty for none)"
        },
        "ColorHex": {
          "ui:widget": "colorPicker",
          "ui:help": "Tint color applied to the front layer sprite"
        },
        "Enabled": {
          "ui:widget": "checkbox"
        },
        "Speed": {
          "ui:widget": "decimalInput",
          "ui:placeholder": "0.2"
        },
        "Rotation": {
          "ui:widget": "decimalInput",
          "ui:placeholder": "0.0"
        },
        "LayoutMode": {
          "ui:widget": "radio",
          "ui:help": "How the sprite fills the background area"
        },
        "AspectRatio": {
          "ui:widget": "decimalInput",
          "ui:placeholder": "1.0"
        }
      },
      "Back": {
        "ui:title": "Back Layer",
        "ui:order": ["SpriteUrl", "ColorHex", "Enabled", "Speed", "Rotation", "LayoutMode", "AspectRatio"],
        "SpriteUrl": {
          "ui:widget": "assetUpload",
          "ui:options": { "assetType": "image" },
          "ui:help": "Sprite for the main background layer"
        },
        "ColorHex": {
          "ui:widget": "colorPicker",
          "ui:help": "Tint color applied to the back layer sprite"
        },
        "Enabled": {
          "ui:widget": "checkbox"
        },
        "Speed": {
          "ui:widget": "decimalInput",
          "ui:placeholder": "0.05"
        },
        "Rotation": {
          "ui:widget": "decimalInput",
          "ui:placeholder": "0.0"
        },
        "LayoutMode": {
          "ui:widget": "radio",
          "ui:help": "How the sprite fills the background area"
        },
        "AspectRatio": {
          "ui:widget": "decimalInput",
          "ui:placeholder": "1.77"
        }
      }
    }
  },
  "audio": {
    "ui:title": "Audio",
    "ui:description": "Sound effects and music tracks for the game",
    "ui:order": ["music_url", "flip_url", "success_url", "error_url", "powerup_url", "victory_url", "lose_url"],
    "music_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:help": "Looping background music during gameplay"
    },
    "flip_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:help": "Sound played when a card is flipped"
    },
    "success_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:help": "Sound played on a successful card match"
    },
    "error_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:help": "Sound played on a failed card match"
    },
    "powerup_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:help": "Sound played when a power-up is collected"
    },
    "victory_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:help": "Sound played when the player wins"
    },
    "lose_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:help": "Sound played on game over"
    }
  },
  "texts": {
    "ui:title": "UI Texts",
    "ui:description": "Text strings displayed throughout the game interface",
    "ui:order": ["victory_title", "victory_phrase", "defeat_title", "defeat_phrase", "label_time", "label_attempts", "label_score", "label_level"],
    "victory_title": {
      "ui:widget": "textInput",
      "ui:placeholder": "¡EXCELENTE!",
      "ui:help": "Large headline on the victory screen"
    },
    "victory_phrase": {
      "ui:widget": "textInput",
      "ui:placeholder": "Has completado todos los niveles.",
      "ui:help": "Subtitle message on the victory screen"
    },
    "defeat_title": {
      "ui:widget": "textInput",
      "ui:placeholder": "GAME OVER",
      "ui:help": "Large headline on the game over screen"
    },
    "defeat_phrase": {
      "ui:widget": "textInput",
      "ui:placeholder": "¡Inténtalo de nuevo!",
      "ui:help": "Subtitle message on the game over screen"
    },
    "label_time": {
      "ui:widget": "textInput",
      "ui:placeholder": "Tiempo",
      "ui:help": "HUD label for the countdown timer"
    },
    "label_attempts": {
      "ui:widget": "textInput",
      "ui:placeholder": "Intentos",
      "ui:help": "HUD label for the attempt counter"
    },
    "label_score": {
      "ui:widget": "textInput",
      "ui:placeholder": "Llaves",
      "ui:help": "HUD label for the score/keys counter"
    },
    "label_level": {
      "ui:widget": "textInput",
      "ui:placeholder": "Nivel",
      "ui:help": "HUD label for the current level indicator"
    }
  },
  "rewards": {
    "ui:title": "Rewards",
    "ui:description": "Coin reward values for player progression",
    "coins_per_action": {
      "ui:widget": "numberInput",
      "ui:placeholder": "10",
      "ui:help": "Coins granted for each successful in-game action"
    },
    "coins_on_completion": {
      "ui:widget": "numberInput",
      "ui:placeholder": "100",
      "ui:help": "Bonus coins granted upon completing the full game"
    }
  },
  "personalization": {
    "ui:title": "Personalization",
    "ui:description": "Custom coin icons for the rewards UI",
    "coin_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "image" },
      "ui:help": "Icon representing a single coin"
    },
    "coin_count_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "image" },
      "ui:help": "Icon used for the coin count display"
    }
  },
  "game": {
    "ui:title": "Game Assets",
    "ui:description": "Card face images used during gameplay",
    "card_images": {
      "ui:title": "Card Images",
      "ui:description": "Upload one image per unique card face. The game will use these as matching pairs.",
      "items": {
        "url": {
          "ui:widget": "assetUpload",
          "ui:options": { "assetType": "image" },
          "ui:help": "Card face image"
        }
      }
    }
  }
}',
    true,
    true,
    NOW(),
    'system'
WHERE NOT EXISTS (
    SELECT 1 FROM game_config_definitions 
    WHERE game_id = 7 AND version = 1
);