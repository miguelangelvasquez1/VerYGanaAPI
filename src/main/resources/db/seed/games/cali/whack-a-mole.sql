INSERT INTO games (id, title, description, delivery_type, url, front_page_url, active, created_at, updated_at)
SELECT
		 10,
    'Whack A Mole',
    'Golpea los topos (o latas) que aparecen en la pantalla antes de que se escondan. ¡Cuántos más, mejor!',
    'QUERY',
    'Music%20Game',
    'https://games.verygana.com/game_icons/cali/whack_a_mole.png',
     true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM games WHERE id = 10);

 
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
    10,
    1,
    '{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "title": "Whack-a-Mole Game Configuration",
  "description": "Full configuration for a whack-a-mole style game",
  "required": ["meta", "branding", "texts", "game_config", "personalization", "game", "audio", "rewards"],
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
    "branding": {
      "type": "object",
      "title": "Branding",
      "description": "Visual identity assets and background shader configuration",
      "required": ["images", "shader_background_config"],
      "properties": {
        "images": {
          "type": "object",
          "title": "Brand Images",
          "description": "Main image and watermark logo assets",
          "required": ["main_image_url", "logo_watermark_url"],
          "properties": {
            "main_image_url": {
              "type": ["string", "null"],
              "title": "Main Image URL",
              "description": "URL of the main game header or logo image",
              "format": "uri",
              "default": ""
            },
            "logo_watermark_url": {
              "type": ["string", "null"],
              "title": "Watermark Logo URL",
              "description": "URL of the small watermark logo overlaid on the game",
              "format": "uri",
              "default": ""
            }
          }
        },
        "shader_background_config": {
          "type": "object",
          "title": "Shader Background Config",
          "description": "Front and back shader-driven background layer settings",
          "required": ["Front", "Back"],
          "properties": {
            "Front": {
              "type": "object",
              "title": "Front Layer",
              "description": "Foreground shader background layer",
              "required": ["Enabled", "SpriteUrl"],
              "properties": {
                "Enabled": {
                  "type": "boolean",
                  "title": "Enabled",
                  "description": "Whether the front shader layer is active",
                  "default": false
                },
                "SpriteUrl": {
                  "type": ["string", "null"],
                  "title": "Sprite URL",
                  "description": "URL of the front background sprite (leave empty to disable)",
                  "format": "uri",
                  "default": ""
                }
              }
            },
            "Back": {
              "type": "object",
              "title": "Back Layer",
              "description": "Main shader background layer",
              "required": ["Alpha", "ColorHex", "SpriteUrl"],
              "properties": {
                "Alpha": {
                  "type": "number",
                  "title": "Alpha",
                  "description": "Opacity of the back background layer (0.0 = transparent, 1.0 = opaque)",
                  "default": 1.0,
                  "minimum": 0.0,
                  "maximum": 1.0,
                  "multipleOf": 0.01
                },
                "ColorHex": {
                  "type": "string",
                  "title": "Color (Hex)",
                  "description": "Tint color applied to the back background layer",
                  "default": "#FFFFFF",
                  "minLength": 4,
                  "maxLength": 9
                },
                "SpriteUrl": {
                  "type": ["string", "null"],
                  "title": "Sprite URL",
                  "description": "URL of the back background image",
                  "format": "uri",
                  "default": ""
                }
              }
            }
          }
        }
      }
    },
    "texts": {
      "type": "object",
      "title": "Texts",
      "description": "Display text and labels used throughout the game UI",
      "required": ["victory_title", "victory_phrase", "defeat_title", "defeat_phrase"],
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
          "default": "¡Has machacado a todos los topos!",
          "minLength": 1,
          "maxLength": 256
        },
        "defeat_title": {
          "type": "string",
          "title": "Defeat Title",
          "description": "Headline shown on the game over screen",
          "default": "INTÉNTALO DE NUEVO",
          "minLength": 1,
          "maxLength": 64
        },
        "defeat_phrase": {
          "type": "string",
          "title": "Defeat Phrase",
          "description": "Subtitle shown on the game over screen",
          "default": "¡No te rindas, sigue practicando!",
          "minLength": 1,
          "maxLength": 256
        }
      }
    },
    "game_config": {
      "type": "object",
      "title": "Game Config",
      "description": "Core gameplay parameters for the whack-a-mole session",
      "required": ["duration", "gridRows", "gridCols", "audioDivisions", "spawnInterval", "moleLifetime", "pointsPerHit", "maxLives"],
      "properties": {
        "duration": {
          "type": "number",
          "title": "Duration (seconds)",
          "description": "Total game session length in seconds",
          "default": 60.0,
          "minimum": 5.0,
          "maximum": 600.0,
          "multipleOf": 0.5
        },
        "gridRows": {
          "type": "integer",
          "title": "Grid Rows",
          "description": "Number of rows in the mole hole grid",
          "default": 3,
          "minimum": 1,
          "maximum": 10
        },
        "gridCols": {
          "type": "integer",
          "title": "Grid Columns",
          "description": "Number of columns in the mole hole grid",
          "default": 3,
          "minimum": 1,
          "maximum": 10
        },
        "audioDivisions": {
          "type": "integer",
          "title": "Audio Divisions",
          "description": "Number of segments the main audio track is divided into for sync",
          "default": 3,
          "minimum": 1,
          "maximum": 32
        },
        "spawnInterval": {
          "type": "number",
          "title": "Spawn Interval (seconds)",
          "description": "Time in seconds between each mole spawn",
          "default": 0.8,
          "minimum": 0.1,
          "maximum": 10.0,
          "multipleOf": 0.1
        },
        "moleLifetime": {
          "type": "number",
          "title": "Mole Lifetime (seconds)",
          "description": "How long a mole stays visible before retreating",
          "default": 2.0,
          "minimum": 0.1,
          "maximum": 30.0,
          "multipleOf": 0.1
        },
        "pointsPerHit": {
          "type": "integer",
          "title": "Points Per Hit",
          "description": "Score points awarded for each successful mole hit",
          "default": 10,
          "minimum": 1,
          "maximum": 10000
        },
        "maxLives": {
          "type": "integer",
          "title": "Max Lives",
          "description": "Number of lives the player starts with",
          "default": 3,
          "minimum": 1,
          "maximum": 10
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
      "title": "Game Assets",
      "description": "Sprites, tint colors and UI settings for in-game objects",
      "required": [
        "holeSpriteUrl", "moleSpriteUrl", "hitSpriteUrl", "errorSpriteUrl",
        "holeColorHex", "moleColorHex", "hitColorHex", "errorColorHex",
        "showPreviewButtons", "previewButtonSpriteUrl", "previewButtonColorHex"
      ],
      "properties": {
        "holeSpriteUrl": {
          "type": ["string", "null"],
          "title": "Hole Sprite URL",
          "description": "URL of the empty hole sprite",
          "format": "uri",
          "default": ""
        },
        "moleSpriteUrl": {
          "type": ["string", "null"],
          "title": "Mole Sprite URL",
          "description": "URL of the mole character sprite",
          "format": "uri",
          "default": ""
        },
        "hitSpriteUrl": {
          "type": ["string", "null"],
          "title": "Hit Sprite URL",
          "description": "URL of the sprite shown on a successful hit",
          "format": "uri",
          "default": ""
        },
        "errorSpriteUrl": {
          "type": ["string", "null"],
          "title": "Error Sprite URL",
          "description": "URL of the sprite shown when a mole escapes or a miss occurs",
          "format": "uri",
          "default": ""
        },
        "holeColorHex": {
          "type": "string",
          "title": "Hole Tint Color",
          "description": "Tint color applied to the hole sprite",
          "default": "#FFFFFF",
          "minLength": 4,
          "maxLength": 9
        },
        "moleColorHex": {
          "type": "string",
          "title": "Mole Tint Color",
          "description": "Tint color applied to the mole sprite",
          "default": "#FFFFFF",
          "minLength": 4,
          "maxLength": 9
        },
        "hitColorHex": {
          "type": "string",
          "title": "Hit Tint Color",
          "description": "Tint color applied to the hit feedback sprite",
          "default": "#FFFFFF",
          "minLength": 4,
          "maxLength": 9
        },
        "errorColorHex": {
          "type": "string",
          "title": "Error Tint Color",
          "description": "Tint color applied to the error feedback sprite",
          "default": "#FFFFFF",
          "minLength": 4,
          "maxLength": 9
        },
        "showPreviewButtons": {
          "type": "boolean",
          "title": "Show Preview Buttons",
          "description": "Whether play/preview buttons are shown before the game starts",
          "default": true
        },
        "previewButtonSpriteUrl": {
          "type": ["string", "null"],
          "title": "Preview Button Sprite URL",
          "description": "URL of the sprite used for preview/play buttons",
          "format": "uri",
          "default": ""
        },
        "previewButtonColorHex": {
          "type": "string",
          "title": "Preview Button Tint Color",
          "description": "Tint color applied to the preview button sprite",
          "default": "#FFFFFF",
          "minLength": 4,
          "maxLength": 9
        }
      }
    },
    "audio": {
      "type": "object",
      "title": "Audio",
      "description": "URLs for all game audio assets",
      "required": ["music_url", "main_audio_url", "hit_sfx_url", "miss_sfx_url", "win_url", "lose_url"],
      "properties": {
        "music_url": {
          "type": ["string", "null"],
          "title": "Background Music URL",
          "description": "URL of the looping background music track",
          "format": "uri",
          "default": ""
        },
        "main_audio_url": {
          "type": ["string", "null"],
          "title": "Main Audio URL",
          "description": "URL of the primary game audio track used for audio-sync divisions",
          "format": "uri",
          "default": ""
        },
        "hit_sfx_url": {
          "type": ["string", "null"],
          "title": "Hit SFX URL",
          "description": "URL of the sound effect played on a successful mole hit",
          "format": "uri",
          "default": ""
        },
        "miss_sfx_url": {
          "type": ["string", "null"],
          "title": "Miss SFX URL",
          "description": "URL of the sound effect played when a mole escapes",
          "format": "uri",
          "default": ""
        },
        "win_url": {
          "type": ["string", "null"],
          "title": "Win Sound URL",
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
    }
  }
}',
    '{
  "ui:order": ["meta", "branding", "texts", "game_config", "personalization", "game", "audio", "rewards"],
  "meta": {
    "ui:title": "Meta",
    "ui:description": "Brand identity metadata",
    "brand_id": {
      "ui:widget": "textInput",
      "ui:placeholder": "e.g. default",
      "ui:help": "Unique identifier for this brand configuration"
    }
  },
  "branding": {
    "ui:title": "Branding",
    "ui:description": "Visual identity assets and background shader configuration",
    "images": {
      "ui:title": "Brand Images",
      "ui:order": ["main_image_url", "logo_watermark_url"],
      "main_image_url": {
        "ui:widget": "assetUpload",
        "ui:options": { "assetType": "image" },
        "ui:help": "Main header or logo image shown in the game"
      },
      "logo_watermark_url": {
        "ui:widget": "assetUpload",
        "ui:options": { "assetType": "image" },
        "ui:help": "Small watermark logo overlaid on the game screen"
      }
    },
    "shader_background_config": {
      "ui:title": "Shader Background Layers",
      "Front": {
        "ui:title": "Front Layer",
        "ui:order": ["Enabled", "SpriteUrl"],
        "Enabled": {
          "ui:widget": "checkbox",
          "ui:help": "Toggle the front shader layer on or off"
        },
        "SpriteUrl": {
          "ui:widget": "assetUpload",
          "ui:options": { "assetType": "image" },
          "ui:help": "Sprite for the foreground background layer (leave empty to disable)"
        }
      },
      "Back": {
        "ui:title": "Back Layer",
        "ui:order": ["SpriteUrl", "ColorHex", "Alpha"],
        "SpriteUrl": {
          "ui:widget": "assetUpload",
          "ui:options": { "assetType": "image" },
          "ui:help": "Main background image"
        },
        "ColorHex": {
          "ui:widget": "colorPicker",
          "ui:help": "Tint color applied to the background image"
        },
        "Alpha": {
          "ui:widget": "decimalInput",
          "ui:placeholder": "1.0",
          "ui:help": "Opacity of the back layer (0.0 = invisible, 1.0 = fully visible)"
        }
      }
    }
  },
  "texts": {
    "ui:title": "UI Texts",
    "ui:description": "Text strings displayed on result screens",
    "ui:order": ["victory_title", "victory_phrase", "defeat_title", "defeat_phrase"],
    "victory_title": {
      "ui:widget": "textInput",
      "ui:placeholder": "¡EXCELENTE!",
      "ui:help": "Large headline on the victory screen"
    },
    "victory_phrase": {
      "ui:widget": "textInput",
      "ui:placeholder": "¡Has machacado a todos los topos!",
      "ui:help": "Subtitle message on the victory screen"
    },
    "defeat_title": {
      "ui:widget": "textInput",
      "ui:placeholder": "INTÉNTALO DE NUEVO",
      "ui:help": "Large headline on the game over screen"
    },
    "defeat_phrase": {
      "ui:widget": "textInput",
      "ui:placeholder": "¡No te rindas, sigue practicando!",
      "ui:help": "Subtitle message on the game over screen"
    }
  },
  "game_config": {
    "ui:title": "Game Configuration",
    "ui:description": "Grid layout, timing and scoring parameters",
    "ui:order": ["duration", "gridRows", "gridCols", "audioDivisions", "spawnInterval", "moleLifetime", "pointsPerHit", "maxLives"],
    "duration": {
      "ui:widget": "decimalInput",
      "ui:placeholder": "60.0",
      "ui:help": "Total game session length in seconds"
    },
    "gridRows": {
      "ui:widget": "numberInput",
      "ui:placeholder": "3",
      "ui:help": "Number of rows in the mole hole grid"
    },
    "gridCols": {
      "ui:widget": "numberInput",
      "ui:placeholder": "3",
      "ui:help": "Number of columns in the mole hole grid"
    },
    "audioDivisions": {
      "ui:widget": "numberInput",
      "ui:placeholder": "3",
      "ui:help": "Number of segments the main audio is split into for sync events"
    },
    "spawnInterval": {
      "ui:widget": "decimalInput",
      "ui:placeholder": "0.8",
      "ui:help": "Seconds between each new mole appearance"
    },
    "moleLifetime": {
      "ui:widget": "decimalInput",
      "ui:placeholder": "2.0",
      "ui:help": "How long a mole stays visible before retreating"
    },
    "pointsPerHit": {
      "ui:widget": "numberInput",
      "ui:placeholder": "10",
      "ui:help": "Score points awarded for each successful hit"
    },
    "maxLives": {
      "ui:widget": "numberInput",
      "ui:placeholder": "3",
      "ui:help": "Starting number of lives for the player"
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
    "ui:description": "Sprites, tint colors and preview button settings for in-game objects",
    "ui:order": [
      "holeSpriteUrl", "holeColorHex",
      "moleSpriteUrl", "moleColorHex",
      "hitSpriteUrl", "hitColorHex",
      "errorSpriteUrl", "errorColorHex",
      "showPreviewButtons", "previewButtonSpriteUrl", "previewButtonColorHex"
    ],
    "holeSpriteUrl": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "image" },
      "ui:help": "Sprite for the empty mole hole"
    },
    "holeColorHex": {
      "ui:widget": "colorPicker",
      "ui:help": "Tint color applied to the hole sprite"
    },
    "moleSpriteUrl": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "image" },
      "ui:help": "Sprite for the mole character"
    },
    "moleColorHex": {
      "ui:widget": "colorPicker",
      "ui:help": "Tint color applied to the mole sprite"
    },
    "hitSpriteUrl": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "image" },
      "ui:help": "Sprite shown briefly on a successful hit"
    },
    "hitColorHex": {
      "ui:widget": "colorPicker",
      "ui:help": "Tint color applied to the hit feedback sprite"
    },
    "errorSpriteUrl": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "image" },
      "ui:help": "Sprite shown when a mole escapes unhit"
    },
    "errorColorHex": {
      "ui:widget": "colorPicker",
      "ui:help": "Tint color applied to the error feedback sprite"
    },
    "showPreviewButtons": {
      "ui:widget": "checkbox",
      "ui:help": "Show play/preview buttons on the pre-game screen"
    },
    "previewButtonSpriteUrl": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "image" },
      "ui:help": "Sprite used for the preview/play button"
    },
    "previewButtonColorHex": {
      "ui:widget": "colorPicker",
      "ui:help": "Tint color applied to the preview button sprite"
    }
  },
  "audio": {
    "ui:title": "Audio",
    "ui:description": "Sound effects and music tracks for the game",
    "ui:order": ["music_url", "main_audio_url", "hit_sfx_url", "miss_sfx_url", "win_url", "lose_url"],
    "music_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:help": "Looping background music during gameplay"
    },
    "main_audio_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:help": "Primary audio track used for audioDivisions sync events"
    },
    "hit_sfx_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:help": "Sound effect played on a successful mole hit"
    },
    "miss_sfx_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:help": "Sound effect played when a mole escapes"
    },
    "win_url": {
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
      "ui:help": "Bonus coins granted upon completing the game"
    }
  }
}',
    true,
    true,
    NOW(),
    'system'
WHERE NOT EXISTS (
    SELECT 1 FROM game_config_definitions 
    WHERE game_id = 10 AND version = 1
);