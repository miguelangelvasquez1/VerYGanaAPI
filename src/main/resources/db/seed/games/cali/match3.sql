INSERT INTO games (id, title, description, delivery_type, url, front_page_url, active, created_at, updated_at)
SELECT
		 6,
    'Match 3',
    'Intercambia piezas para formar grupos de 3 o más del mismo tipo. Completa el objetivo antes de que se acabe el tiempo.',
    'QUERY',
    'Match%203',
    'https://games.verygana.com/game_icons/cali/match_3.png',
     true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM games WHERE id = 6);

 
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
    6,
    1,
    '{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "title": "Game Configuration",
  "required": ["meta", "game_config", "branding", "audio", "texts", "rewards", "personalization", "game"],
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
      "description": "Core gameplay rules, timing, and level progression parameters",
      "required": ["time_limit", "difficulty", "max_attempts", "target_tiles", "total_levels"],
      "properties": {
        "time_limit": {
          "type": "integer",
          "title": "Time Limit (seconds)",
          "description": "Maximum time allowed per game session in seconds",
          "minimum": 10,
          "maximum": 600,
          "default": 60
        },
        "difficulty": {
          "type": "string",
          "title": "Difficulty",
          "description": "Game difficulty level",
          "enum": ["easy", "normal", "hard"],
          "default": "normal"
        },
        "max_attempts": {
          "type": "integer",
          "title": "Max Attempts",
          "description": "Maximum number of failed attempts allowed before game over",
          "minimum": 1,
          "maximum": 20,
          "default": 3
        },
        "target_tiles": {
          "type": "integer",
          "title": "Target Tiles",
          "description": "Number of tiles the player must match to complete a level",
          "minimum": 1,
          "maximum": 1000,
          "default": 10
        },
        "total_levels": {
          "type": "integer",
          "title": "Total Levels",
          "description": "Total number of levels in the game session",
          "minimum": 1,
          "maximum": 100,
          "default": 5
        }
      }
    },
    "branding": {
      "type": "object",
      "title": "Branding",
      "description": "Visual branding assets, background images, and background layer configuration",
      "required": ["images", "background_config"],
      "properties": {
        "images": {
          "type": "object",
          "title": "Branding Images",
          "description": "Logo, watermark, background, and grid background image assets",
          "required": [
            "main_logo_url",
            "main_logo_offset_y",
            "logo_watermark_url",
            "logo_watermark_offset_y",
            "background_url",
            "background_color_hex",
            "grid_background_url",
            "grid_background_color_hex"
          ],
          "properties": {
            "main_logo_url": {
              "type": ["string", "null"],
              "title": "Main Logo URL",
              "description": "URL of the main branding logo image",
              "format": "uri",
              "default": "https://placehold.co/400x200/FF5733/FFFFFF.png?text=LOGO"
            },
            "main_logo_offset_y": {
              "type": "number",
              "title": "Main Logo Offset Y",
              "description": "Vertical offset for positioning the main logo",
              "minimum": -1000.0,
              "maximum": 1000.0,
              "multipleOf": 0.01,
              "default": 0.0
            },
            "logo_watermark_url": {
              "type": ["string", "null"],
              "title": "Logo Watermark URL",
              "description": "URL of the watermark logo image",
              "format": "uri",
              "default": "https://placehold.co/150x50/333333/FFFFFF.png?text=WATERMARK"
            },
            "logo_watermark_offset_y": {
              "type": "number",
              "title": "Logo Watermark Offset Y",
              "description": "Vertical offset for positioning the watermark logo",
              "minimum": -1000.0,
              "maximum": 1000.0,
              "multipleOf": 0.01,
              "default": 0.0
            },
            "background_url": {
              "type": ["string", "null"],
              "title": "Background URL",
              "description": "URL of the full-screen background image",
              "format": "uri",
              "default": ""
            },
            "background_color_hex": {
              "type": "string",
              "title": "Background Color Hex",
              "description": "Fallback hex color for the background when no image is provided",
              "minLength": 7,
              "maxLength": 9,
              "default": "#FFFFFF"
            },
            "grid_background_url": {
              "type": ["string", "null"],
              "title": "Grid Background URL",
              "description": "URL of the background image displayed behind the game grid",
              "format": "uri",
              "default": "https://placehold.co/400x400/222222/444444.png?text=Grid+BG"
            },
            "grid_background_color_hex": {
              "type": "string",
              "title": "Grid Background Color Hex",
              "description": "Fallback hex color for the grid background when no image is provided",
              "minLength": 7,
              "maxLength": 9,
              "default": "#FFFFFF"
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
                  "default": true
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
        }
      }
    },
    "audio": {
      "type": "object",
      "title": "Audio",
      "description": "Audio asset URLs for background music and all sound effects",
      "required": ["music_url", "victory_url", "lose_url", "match_url", "swap_url"],
      "properties": {
        "music_url": {
          "type": ["string", "null"],
          "title": "Background Music URL",
          "description": "URL of the background music audio file",
          "format": "uri",
          "default": "https://rtainor.com/verygana/musicmemory/nokia.mp3"
        },
        "victory_url": {
          "type": ["string", "null"],
          "title": "Victory Sound URL",
          "description": "URL of the sound effect played on victory",
          "format": "uri",
          "default": "https://rtainor.com/verygana/musicmemory/nokia.mp3"
        },
        "lose_url": {
          "type": ["string", "null"],
          "title": "Defeat Sound URL",
          "description": "URL of the sound effect played on defeat",
          "format": "uri",
          "default": "https://rtainor.com/verygana/musicmemory/nokia.mp3"
        },
        "match_url": {
          "type": ["string", "null"],
          "title": "Match Sound URL",
          "description": "URL of the sound effect played when tiles are successfully matched",
          "format": "uri",
          "default": "https://cdn.aframe.io/360-image-gallery-boilerplate/audio/click.ogg"
        },
        "swap_url": {
          "type": ["string", "null"],
          "title": "Swap Sound URL",
          "description": "URL of the sound effect played when tiles are swapped",
          "format": "uri",
          "default": "https://cdn.aframe.io/360-image-gallery-boilerplate/audio/click.ogg"
        }
      }
    },
    "texts": {
      "type": "object",
      "title": "Texts",
      "description": "Display texts for victory and defeat screens",
      "required": ["victory_title", "victory_phrase", "defeat_title", "defeat_phrase"],
      "properties": {
        "victory_title": {
          "type": "string",
          "title": "Victory Title",
          "description": "Title text displayed on the victory screen",
          "minLength": 1,
          "maxLength": 100,
          "default": "¡VICTORIA!"
        },
        "victory_phrase": {
          "type": "string",
          "title": "Victory Phrase",
          "description": "Motivational phrase displayed on the victory screen",
          "minLength": 1,
          "maxLength": 300,
          "default": "¡Nivel Completado!"
        },
        "defeat_title": {
          "type": "string",
          "title": "Defeat Title",
          "description": "Title text displayed on the defeat screen",
          "minLength": 1,
          "maxLength": 100,
          "default": "¡INTÉNTALO DE NUEVO!"
        },
        "defeat_phrase": {
          "type": "string",
          "title": "Defeat Phrase",
          "description": "Message displayed on the defeat screen",
          "minLength": 1,
          "maxLength": 300,
          "default": "Se acabó el tiempo."
        }
      }
    },
    "rewards": {
      "type": "object",
      "title": "Rewards",
      "description": "Coin reward configuration including combo multiplier",
      "required": ["coins_per_action", "coins_on_completion", "combo_multiplier"],
      "properties": {
        "coins_per_action": {
          "type": "integer",
          "title": "Coins Per Action",
          "description": "Number of coins awarded for each successful tile match",
          "minimum": 0,
          "maximum": 10000,
          "default": 10
        },
        "coins_on_completion": {
          "type": "integer",
          "title": "Coins On Completion",
          "description": "Number of coins awarded upon successfully completing the game",
          "minimum": 0,
          "maximum": 100000,
          "default": 100
        },
        "combo_multiplier": {
          "type": "number",
          "title": "Combo Multiplier",
          "description": "Fractional multiplier applied to rewards for consecutive combo matches",
          "minimum": 0.0,
          "maximum": 10.0,
          "multipleOf": 0.01,
          "default": 0.1
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
      "title": "Game",
      "description": "Tile definitions and initial tile type count for the match game",
      "required": ["initial_types", "tiles"],
      "properties": {
        "initial_types": {
          "type": "integer",
          "title": "Initial Tile Types",
          "description": "Number of distinct tile types available at the start of the game",
          "minimum": 1,
          "maximum": 50,
          "default": 6
        },
        "tiles": {
          "type": "array",
          "title": "Tiles",
          "description": "List of tile definitions used in the match game",
          "minItems": 1,
          "items": {
            "type": "object",
            "title": "Tile",
            "description": "Definition of a single tile type with its sprite and score value",
            "required": ["id", "sprite_url", "score"],
            "properties": {
              "id": {
                "type": "integer",
                "title": "Tile ID",
                "description": "Unique numeric identifier for this tile type",
                "minimum": 1,
                "maximum": 10000
              },
              "sprite_url": {
                "type": ["string", "null"],
                "title": "Sprite URL",
                "description": "URL of the sprite image for this tile type",
                "format": "uri"
              },
              "score": {
                "type": "integer",
                "title": "Score",
                "description": "Points awarded when this tile is successfully matched",
                "minimum": 0,
                "maximum": 100000,
                "default": 10
              }
            }
          },
          "default": [
            { "id": 1, "sprite_url": "https://placehold.co/128x128/FF0000/FFFFFF.png?text=1", "score": 10 },
            { "id": 2, "sprite_url": "https://placehold.co/128x128/00FF00/FFFFFF.png?text=2", "score": 10 },
            { "id": 3, "sprite_url": "https://placehold.co/128x128/0000FF/FFFFFF.png?text=3", "score": 10 },
            { "id": 4, "sprite_url": "https://placehold.co/128x128/FFFF00/000000.png?text=4", "score": 10 },
            { "id": 5, "sprite_url": "https://placehold.co/128x128/FF00FF/FFFFFF.png?text=5+Star", "score": 15 },
            { "id": 6, "sprite_url": "https://placehold.co/128x128/00FFFF/000000.png?text=6+Star", "score": 15 }
          ]
        }
      }
    }
  }
}',
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
    "ui:title": "Game Configuration",
    "ui:description": "Core gameplay rules, timing, and level progression parameters",
    "time_limit": {
      "ui:widget": "numberInput",
      "ui:title": "Time Limit (seconds)",
      "ui:placeholder": "e.g. 60",
      "ui:help": "Maximum time allowed per game session in seconds"
    },
    "difficulty": {
      "ui:widget": "radio",
      "ui:title": "Difficulty",
      "ui:help": "Select the difficulty level for this game configuration"
    },
    "max_attempts": {
      "ui:widget": "numberInput",
      "ui:title": "Max Attempts",
      "ui:placeholder": "e.g. 3",
      "ui:help": "Maximum number of failed attempts allowed before game over"
    },
    "target_tiles": {
      "ui:widget": "numberInput",
      "ui:title": "Target Tiles",
      "ui:placeholder": "e.g. 10",
      "ui:help": "Number of tiles the player must match to complete a level"
    },
    "total_levels": {
      "ui:widget": "numberInput",
      "ui:title": "Total Levels",
      "ui:placeholder": "e.g. 5",
      "ui:help": "Total number of levels in the game session"
    }
  },
  "branding": {
    "ui:title": "Branding",
    "ui:description": "Logo assets, background images, and background layer configuration",
    "images": {
      "ui:title": "Branding Images",
      "main_logo_url": {
        "ui:widget": "assetUpload",
        "ui:title": "Main Logo",
        "ui:help": "Upload or provide URL for the main branding logo image",
        "ui:options": { "assetType": "image" }
      },
      "main_logo_offset_y": {
        "ui:widget": "decimalInput",
        "ui:title": "Main Logo Offset Y",
        "ui:placeholder": "e.g. 0.0",
        "ui:help": "Vertical offset for positioning the main logo"
      },
      "logo_watermark_url": {
        "ui:widget": "assetUpload",
        "ui:title": "Watermark Logo",
        "ui:help": "Upload or provide URL for the watermark logo image",
        "ui:options": { "assetType": "image" }
      },
      "logo_watermark_offset_y": {
        "ui:widget": "decimalInput",
        "ui:title": "Watermark Logo Offset Y",
        "ui:placeholder": "e.g. 0.0",
        "ui:help": "Vertical offset for positioning the watermark logo"
      },
      "background_url": {
        "ui:widget": "assetUpload",
        "ui:title": "Background Image",
        "ui:help": "Upload or provide URL for the full-screen background image",
        "ui:options": { "assetType": "image" }
      },
      "background_color_hex": {
        "ui:widget": "colorPicker",
        "ui:title": "Background Color (Hex)",
        "ui:help": "Fallback background color when no background image is provided"
      },
      "grid_background_url": {
        "ui:widget": "assetUpload",
        "ui:title": "Grid Background Image",
        "ui:help": "Upload or provide URL for the background image behind the game grid",
        "ui:options": { "assetType": "image" }
      },
      "grid_background_color_hex": {
        "ui:widget": "colorPicker",
        "ui:title": "Grid Background Color (Hex)",
        "ui:help": "Fallback color for the grid background when no image is provided"
      }
    },
    "background_config": {
      "ui:title": "Background Layer Configuration",
      "ui:description": "Configure the Front and Back background rendering layers",
      "Front": {
        "ui:title": "Front Layer",
        "ui:description": "Configuration for the foreground background layer",
        "SpriteUrl": {
          "ui:widget": "assetUpload",
          "ui:title": "Front Layer Sprite",
          "ui:help": "Upload or provide URL for the foreground layer sprite image",
          "ui:options": { "assetType": "image" }
        },
        "ColorHex": {
          "ui:widget": "colorPicker",
          "ui:title": "Front Layer Color (Hex)",
          "ui:help": "Hex color to tint the front layer sprite"
        },
        "Enabled": {
          "ui:widget": "checkbox",
          "ui:title": "Enable Front Layer",
          "ui:help": "Toggle visibility of the front background layer"
        },
        "Speed": {
          "ui:widget": "decimalInput",
          "ui:title": "Speed",
          "ui:placeholder": "e.g. 0.2",
          "ui:help": "Scrolling or animation speed of the front layer"
        },
        "Rotation": {
          "ui:widget": "decimalInput",
          "ui:title": "Rotation (degrees)",
          "ui:placeholder": "e.g. 0.0",
          "ui:help": "Rotation angle in degrees for the front layer"
        },
        "LayoutMode": {
          "ui:widget": "radio",
          "ui:title": "Layout Mode",
          "ui:help": "Rendering layout mode for the front layer sprite"
        },
        "AspectRatio": {
          "ui:widget": "decimalInput",
          "ui:title": "Aspect Ratio",
          "ui:placeholder": "e.g. 1.0",
          "ui:help": "Aspect ratio used when rendering the front layer"
        }
      },
      "Back": {
        "ui:title": "Back Layer",
        "ui:description": "Configuration for the background rendering layer",
        "SpriteUrl": {
          "ui:widget": "assetUpload",
          "ui:title": "Back Layer Sprite",
          "ui:help": "Upload or provide URL for the background layer sprite image",
          "ui:options": { "assetType": "image" }
        },
        "ColorHex": {
          "ui:widget": "colorPicker",
          "ui:title": "Back Layer Color (Hex)",
          "ui:help": "Hex color to tint the back layer sprite"
        },
        "Enabled": {
          "ui:widget": "checkbox",
          "ui:title": "Enable Back Layer",
          "ui:help": "Toggle visibility of the back background layer"
        },
        "Speed": {
          "ui:widget": "decimalInput",
          "ui:title": "Speed",
          "ui:placeholder": "e.g. 0.05",
          "ui:help": "Scrolling or animation speed of the back layer"
        },
        "Rotation": {
          "ui:widget": "decimalInput",
          "ui:title": "Rotation (degrees)",
          "ui:placeholder": "e.g. 0.0",
          "ui:help": "Rotation angle in degrees for the back layer"
        },
        "LayoutMode": {
          "ui:widget": "radio",
          "ui:title": "Layout Mode",
          "ui:help": "Rendering layout mode for the back layer sprite"
        },
        "AspectRatio": {
          "ui:widget": "decimalInput",
          "ui:title": "Aspect Ratio",
          "ui:placeholder": "e.g. 1.77",
          "ui:help": "Aspect ratio used when rendering the back layer"
        }
      }
    }
  },
  "audio": {
    "ui:title": "Audio",
    "ui:description": "Audio asset URLs for background music and all sound effects",
    "music_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Background Music",
      "ui:help": "Upload or provide URL for the background music track",
      "ui:options": { "assetType": "audio" }
    },
    "victory_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Victory Sound",
      "ui:help": "Upload or provide URL for the victory sound effect",
      "ui:options": { "assetType": "audio" }
    },
    "lose_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Defeat Sound",
      "ui:help": "Upload or provide URL for the defeat sound effect",
      "ui:options": { "assetType": "audio" }
    },
    "match_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Match Sound",
      "ui:help": "Upload or provide URL for the tile match success sound effect",
      "ui:options": { "assetType": "audio" }
    },
    "swap_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Swap Sound",
      "ui:help": "Upload or provide URL for the tile swap sound effect",
      "ui:options": { "assetType": "audio" }
    }
  },
  "texts": {
    "ui:title": "Texts",
    "ui:description": "Display texts for game result screens",
    "victory_title": {
      "ui:widget": "textInput",
      "ui:title": "Victory Title",
      "ui:placeholder": "e.g. ¡VICTORIA!",
      "ui:help": "Title shown on the victory screen"
    },
    "victory_phrase": {
      "ui:widget": "textInput",
      "ui:title": "Victory Phrase",
      "ui:placeholder": "e.g. ¡Nivel Completado!",
      "ui:help": "Motivational phrase shown on the victory screen"
    },
    "defeat_title": {
      "ui:widget": "textInput",
      "ui:title": "Defeat Title",
      "ui:placeholder": "e.g. ¡INTÉNTALO DE NUEVO!",
      "ui:help": "Title shown on the defeat screen"
    },
    "defeat_phrase": {
      "ui:widget": "textInput",
      "ui:title": "Defeat Phrase",
      "ui:placeholder": "e.g. Se acabó el tiempo.",
      "ui:help": "Message shown on the defeat screen"
    }
  },
  "rewards": {
    "ui:title": "Rewards",
    "ui:description": "Coin reward values and combo multiplier configuration",
    "coins_per_action": {
      "ui:widget": "numberInput",
      "ui:title": "Coins Per Action",
      "ui:placeholder": "e.g. 10",
      "ui:help": "Coins awarded for each successful tile match"
    },
    "coins_on_completion": {
      "ui:widget": "numberInput",
      "ui:title": "Coins On Completion",
      "ui:placeholder": "e.g. 100",
      "ui:help": "Coins awarded upon successfully completing the game"
    },
    "combo_multiplier": {
      "ui:widget": "decimalInput",
      "ui:title": "Combo Multiplier",
      "ui:placeholder": "e.g. 0.1",
      "ui:help": "Fractional multiplier applied to rewards for consecutive combo matches"
    }
  },
  "personalization": {
    "ui:title": "Personalization",
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
    "ui:title": "Game",
    "ui:description": "Tile definitions and initial tile type count for the match game",
    "initial_types": {
      "ui:widget": "numberInput",
      "ui:title": "Initial Tile Types",
      "ui:placeholder": "e.g. 6",
      "ui:help": "Number of distinct tile types available at the start of the game"
    },
    "tiles": {
      "ui:title": "Tiles",
      "ui:description": "Define all tile types used in the match game",
      "ui:help": "Add one entry per tile type. Each tile requires a unique ID, a sprite image, and a score value.",
      "items": {
        "ui:order": ["id", "sprite_url", "score"],
        "id": {
          "ui:widget": "numberInput",
          "ui:title": "Tile ID",
          "ui:placeholder": "e.g. 1",
          "ui:help": "Unique numeric identifier for this tile type"
        },
        "sprite_url": {
          "ui:widget": "assetUpload",
          "ui:title": "Tile Sprite",
          "ui:help": "Upload or provide URL for this tile type sprite image",
          "ui:options": { "assetType": "image" }
        },
        "score": {
          "ui:widget": "numberInput",
          "ui:title": "Score",
          "ui:placeholder": "e.g. 10",
          "ui:help": "Points awarded when this tile is successfully matched"
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
    WHERE game_id = 6 AND version = 1
);