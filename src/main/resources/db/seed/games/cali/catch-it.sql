INSERT INTO games (id, title, description, delivery_type, url, front_page_url, active, created_at, updated_at)
SELECT
		 4,
    'Catch It',
    'Atrapa los objetos de tu lista de compras antes de que caigan. Evita los obstáculos o perderás vidas.',
    'QUERY',
    'Catch%20It',
    'https://games.verygana.com/game_icons/cali/catch_it.png',
     true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM games WHERE id = 4);

 
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
    4,
    1,
    '{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "title": "Game Configuration",
  "required": ["meta", "branding", "texts", "game_config", "game", "audio", "rewards", "personalization"],
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
    "branding": {
      "type": "object",
      "title": "Branding",
      "description": "Visual branding assets and shader background configuration",
      "required": ["images", "shader_background_config"],
      "properties": {
        "images": {
          "type": "object",
          "title": "Branding Images",
          "description": "Main and watermark logo image assets",
          "required": ["main_image_url", "logo_watermark_url"],
          "properties": {
            "main_image_url": {
              "type": ["string", "null"],
              "title": "Main Image URL",
              "description": "URL of the main branding image or logo",
              "format": "uri",
              "default": "https://placehold.co/400x200/FF5733/FFFFFF.png?text=LOGO"
            },
            "logo_watermark_url": {
              "type": ["string", "null"],
              "title": "Logo Watermark URL",
              "description": "URL of the watermark logo image",
              "format": "uri",
              "default": "https://placehold.co/150x50/333333/FFFFFF.png?text=WATERMARK"
            }
          }
        },
        "shader_background_config": {
          "type": "object",
          "title": "Shader Background Configuration",
          "description": "Configuration for layered shader background rendering",
          "required": ["Front", "Back"],
          "properties": {
            "Front": {
              "type": "object",
              "title": "Front Layer",
              "description": "Configuration for the foreground shader layer",
              "required": ["SpriteUrl", "Enabled", "ColorHex", "Speed", "Alpha", "Tiling", "Direction", "Rotation"],
              "properties": {
                "SpriteUrl": {
                  "type": ["string", "null"],
                  "title": "Sprite URL",
                  "description": "URL of the foreground sprite image",
                  "format": "uri",
                  "default": ""
                },
                "Enabled": {
                  "type": "boolean",
                  "title": "Enabled",
                  "description": "Whether this foreground layer is active",
                  "default": false
                },
                "ColorHex": {
                  "type": "string",
                  "title": "Color Hex",
                  "description": "Hex color applied to the foreground sprite",
                  "minLength": 7,
                  "maxLength": 9,
                  "default": "#FFFFFF"
                },
                "Speed": {
                  "type": "number",
                  "title": "Speed",
                  "description": "Scrolling speed of the foreground layer",
                  "minimum": 0.0,
                  "maximum": 10.0,
                  "multipleOf": 0.01,
                  "default": 0.2
                },
                "Alpha": {
                  "type": "number",
                  "title": "Alpha",
                  "description": "Opacity of the foreground layer (0.0 transparent to 1.0 opaque)",
                  "minimum": 0.0,
                  "maximum": 1.0,
                  "multipleOf": 0.01,
                  "default": 0.0
                },
                "Rotation": {
                  "type": "number",
                  "title": "Rotation",
                  "description": "Rotation angle in degrees for the foreground layer",
                  "minimum": -360.0,
                  "maximum": 360.0,
                  "multipleOf": 0.01,
                  "default": 0.0
                },
                "Tiling": {
                  "type": "object",
                  "title": "Tiling",
                  "description": "Tiling repetition on X and Y axes",
                  "required": ["x", "y"],
                  "properties": {
                    "x": {
                      "type": "number",
                      "title": "Tiling X",
                      "description": "Horizontal tiling factor",
                      "minimum": 0.0,
                      "maximum": 100.0,
                      "multipleOf": 0.01,
                      "default": 1.0
                    },
                    "y": {
                      "type": "number",
                      "title": "Tiling Y",
                      "description": "Vertical tiling factor",
                      "minimum": 0.0,
                      "maximum": 100.0,
                      "multipleOf": 0.01,
                      "default": 1.0
                    }
                  }
                },
                "Direction": {
                  "type": "object",
                  "title": "Direction",
                  "description": "Scrolling direction vector for the foreground layer",
                  "required": ["x", "y"],
                  "properties": {
                    "x": {
                      "type": "number",
                      "title": "Direction X",
                      "description": "Horizontal component of scrolling direction",
                      "minimum": -1.0,
                      "maximum": 1.0,
                      "multipleOf": 0.01,
                      "default": 1.0
                    },
                    "y": {
                      "type": "number",
                      "title": "Direction Y",
                      "description": "Vertical component of scrolling direction",
                      "minimum": -1.0,
                      "maximum": 1.0,
                      "multipleOf": 0.01,
                      "default": 0.0
                    }
                  }
                }
              }
            },
            "Back": {
              "type": "object",
              "title": "Back Layer",
              "description": "Configuration for the background shader layer",
              "required": ["SpriteUrl", "ColorHex", "Speed", "Alpha", "Tiling", "Direction", "Rotation"],
              "properties": {
                "SpriteUrl": {
                  "type": ["string", "null"],
                  "title": "Sprite URL",
                  "description": "URL of the background sprite image",
                  "format": "uri",
                  "default": "https://placehold.co/1024x512/000033/FFFFFF.png?text=BG"
                },
                "ColorHex": {
                  "type": "string",
                  "title": "Color Hex",
                  "description": "Hex color applied to the background sprite",
                  "minLength": 7,
                  "maxLength": 9,
                  "default": "#FFFFFF"
                },
                "Speed": {
                  "type": "number",
                  "title": "Speed",
                  "description": "Scrolling speed of the background layer",
                  "minimum": 0.0,
                  "maximum": 10.0,
                  "multipleOf": 0.01,
                  "default": 0.0
                },
                "Alpha": {
                  "type": "number",
                  "title": "Alpha",
                  "description": "Opacity of the background layer (0.0 transparent to 1.0 opaque)",
                  "minimum": 0.0,
                  "maximum": 1.0,
                  "multipleOf": 0.01,
                  "default": 1.0
                },
                "Rotation": {
                  "type": "number",
                  "title": "Rotation",
                  "description": "Rotation angle in degrees for the background layer",
                  "minimum": -360.0,
                  "maximum": 360.0,
                  "multipleOf": 0.01,
                  "default": 0.0
                },
                "Tiling": {
                  "type": "object",
                  "title": "Tiling",
                  "description": "Tiling repetition on X and Y axes",
                  "required": ["x", "y"],
                  "properties": {
                    "x": {
                      "type": "number",
                      "title": "Tiling X",
                      "description": "Horizontal tiling factor",
                      "minimum": 0.0,
                      "maximum": 100.0,
                      "multipleOf": 0.01,
                      "default": 1.0
                    },
                    "y": {
                      "type": "number",
                      "title": "Tiling Y",
                      "description": "Vertical tiling factor",
                      "minimum": 0.0,
                      "maximum": 100.0,
                      "multipleOf": 0.01,
                      "default": 1.0
                    }
                  }
                },
                "Direction": {
                  "type": "object",
                  "title": "Direction",
                  "description": "Scrolling direction vector for the background layer",
                  "required": ["x", "y"],
                  "properties": {
                    "x": {
                      "type": "number",
                      "title": "Direction X",
                      "description": "Horizontal component of scrolling direction",
                      "minimum": -1.0,
                      "maximum": 1.0,
                      "multipleOf": 0.01,
                      "default": 0.0
                    },
                    "y": {
                      "type": "number",
                      "title": "Direction Y",
                      "description": "Vertical component of scrolling direction",
                      "minimum": -1.0,
                      "maximum": 1.0,
                      "multipleOf": 0.01,
                      "default": 0.0
                    }
                  }
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
      "description": "Display texts for victory and defeat screens",
      "required": ["victory_title", "victory_phrase", "defeat_title", "defeat_phrase"],
      "properties": {
        "victory_title": {
          "type": "string",
          "title": "Victory Title",
          "description": "Title text displayed on the victory screen",
          "minLength": 1,
          "maxLength": 100,
          "default": "¡HAS GANADO!"
        },
        "victory_phrase": {
          "type": "string",
          "title": "Victory Phrase",
          "description": "Motivational phrase displayed on the victory screen",
          "minLength": 1,
          "maxLength": 300,
          "default": "¡Has recogido todos los objetos de la lista!"
        },
        "defeat_title": {
          "type": "string",
          "title": "Defeat Title",
          "description": "Title text displayed on the defeat screen",
          "minLength": 1,
          "maxLength": 100,
          "default": "HAS PERDIDO"
        },
        "defeat_phrase": {
          "type": "string",
          "title": "Defeat Phrase",
          "description": "Motivational phrase displayed on the defeat screen",
          "minLength": 1,
          "maxLength": 300,
          "default": "Inténtalo de nuevo."
        }
      }
    },
    "game_config": {
      "type": "object",
      "title": "Game Configuration",
      "description": "Core gameplay timing and session parameters",
      "required": ["duration", "spawn_rate", "lives"],
      "properties": {
        "duration": {
          "type": "number",
          "title": "Duration (seconds)",
          "description": "Total duration of a game session in seconds",
          "minimum": 10.0,
          "maximum": 600.0,
          "multipleOf": 0.1,
          "default": 60.0
        },
        "spawn_rate": {
          "type": "number",
          "title": "Spawn Rate (seconds)",
          "description": "Time interval in seconds between object spawns",
          "minimum": 0.1,
          "maximum": 30.0,
          "multipleOf": 0.01,
          "default": 1.2
        },
        "lives": {
          "type": "integer",
          "title": "Lives",
          "description": "Number of lives the player starts with",
          "minimum": 1,
          "maximum": 10,
          "default": 3
        }
      }
    },
    "game": {
      "type": "object",
      "title": "Game",
      "description": "Gameplay mechanics, object definitions, and asset configuration",
      "required": [
        "fall_speed_min",
        "fall_speed_max",
        "basket_speed",
        "shopping_list_size",
        "min_quantity",
        "max_quantity",
        "list_bg_image_url",
        "basket_sprite_url",
        "objects"
      ],
      "properties": {
        "fall_speed_min": {
          "type": "number",
          "title": "Fall Speed Min",
          "description": "Minimum falling speed of spawned objects",
          "minimum": 0.0,
          "maximum": 50.0,
          "multipleOf": 0.01,
          "default": 2.0
        },
        "fall_speed_max": {
          "type": "number",
          "title": "Fall Speed Max",
          "description": "Maximum falling speed of spawned objects",
          "minimum": 0.0,
          "maximum": 50.0,
          "multipleOf": 0.01,
          "default": 4.0
        },
        "basket_speed": {
          "type": "number",
          "title": "Basket Speed",
          "description": "Horizontal movement speed of the player basket",
          "minimum": 0.0,
          "maximum": 100.0,
          "multipleOf": 0.01,
          "default": 15.0
        },
        "shopping_list_size": {
          "type": "integer",
          "title": "Shopping List Size",
          "description": "Number of distinct items shown in the shopping list per round",
          "minimum": 1,
          "maximum": 20,
          "default": 3
        },
        "min_quantity": {
          "type": "integer",
          "title": "Min Quantity",
          "description": "Minimum quantity required per item in the shopping list",
          "minimum": 1,
          "maximum": 100,
          "default": 2
        },
        "max_quantity": {
          "type": "integer",
          "title": "Max Quantity",
          "description": "Maximum quantity required per item in the shopping list",
          "minimum": 1,
          "maximum": 100,
          "default": 5
        },
        "list_bg_image_url": {
          "type": ["string", "null"],
          "title": "Shopping List Background Image URL",
          "description": "URL of the background image displayed behind the shopping list UI",
          "format": "uri",
          "default": "https://placehold.co/256x512/FFFFFF/000000.png?text=LIST"
        },
        "basket_sprite_url": {
          "type": ["string", "null"],
          "title": "Basket Sprite URL",
          "description": "URL of the sprite image for the player basket",
          "format": "uri",
          "default": "https://placehold.co/256x128/00FF00/000000.png?text=BASKET"
        },
        "objects": {
          "type": "array",
          "title": "Objects",
          "description": "List of collectible and obstacle object definitions used in the game",
          "minItems": 1,
          "items": {
            "type": "object",
            "title": "Object",
            "description": "Definition of a single collectible or obstacle object",
            "required": ["id", "sprite_url", "score", "scale"],
            "properties": {
              "id": {
                "type": "string",
                "title": "Object ID",
                "description": "Unique identifier for this object type",
                "minLength": 1,
                "maxLength": 100
              },
              "sprite_url": {
                "type": ["string", "null"],
                "title": "Sprite URL",
                "description": "URL of the sprite image for this object",
                "format": "uri"
              },
              "score": {
                "type": "integer",
                "title": "Score",
                "description": "Score value awarded or deducted when this object is collected",
                "minimum": -1000,
                "maximum": 1000,
                "default": 0
              },
              "scale": {
                "type": "number",
                "title": "Scale",
                "description": "Visual scale multiplier for this object sprite",
                "minimum": 0.1,
                "maximum": 10.0,
                "multipleOf": 0.01,
                "default": 1.0
              },
              "is_obstacle": {
                "type": "boolean",
                "title": "Is Obstacle",
                "description": "Whether this object acts as an obstacle that penalizes the player",
                "default": false
              }
            }
          },
          "default": [
            {
              "id": "item_1",
              "sprite_url": "https://placehold.co/128x128/FF0000/FFFFFF.png?text=ITEM_1",
              "score": 10,
              "scale": 1.0
            },
            {
              "id": "item_2",
              "sprite_url": "https://placehold.co/128x128/00FF00/FFFFFF.png?text=ITEM_2",
              "score": 5,
              "scale": 0.8
            },
            {
              "id": "item_3",
              "sprite_url": "https://placehold.co/128x128/0000FF/FFFFFF.png?text=ITEM_3",
              "score": 15,
              "scale": 1.2
            },
            {
              "id": "trash",
              "sprite_url": "https://placehold.co/128x128/333333/FFFFFF.png?text=BOMB",
              "score": 0,
              "scale": 1.0,
              "is_obstacle": true
            }
          ]
        }
      }
    },
    "audio": {
      "type": "object",
      "title": "Audio",
      "description": "Audio asset URLs for music and sound effects",
      "required": ["music_url", "positive_url", "negative_url", "spawn_url"],
      "properties": {
        "music_url": {
          "type": ["string", "null"],
          "title": "Background Music URL",
          "description": "URL of the background music audio file",
          "format": "uri",
          "default": "https://rtainor.com/verygana/musicmemory/nokia.mp3"
        },
        "positive_url": {
          "type": ["string", "null"],
          "title": "Positive Sound URL",
          "description": "URL of the sound effect played on a successful collection",
          "format": "uri",
          "default": "https://rtainor.com/verygana/musicmemory/nokia.mp3"
        },
        "negative_url": {
          "type": ["string", "null"],
          "title": "Negative Sound URL",
          "description": "URL of the sound effect played on a failed action or obstacle hit",
          "format": "uri",
          "default": "https://rtainor.com/verygana/musicmemory/nokia.mp3"
        },
        "spawn_url": {
          "type": ["string", "null"],
          "title": "Spawn Sound URL",
          "description": "URL of the sound effect played when an object spawns",
          "format": "uri",
          "default": "https://cdn.aframe.io/360-image-gallery-boilerplate/audio/click.ogg"
        }
      }
    },
    "rewards": {
      "type": "object",
      "title": "Rewards",
      "description": "Coin reward configuration for player actions and game completion",
      "required": ["coins_per_action", "coins_on_completion"],
      "properties": {
        "coins_per_action": {
          "type": "integer",
          "title": "Coins Per Action",
          "description": "Number of coins awarded for each successful in-game action",
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
    }
  }
}',
    '{
  "ui:order": ["meta", "branding", "texts", "game_config", "game", "audio", "rewards", "personalization"],
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
  "branding": {
    "ui:title": "Branding",
    "ui:description": "Logo assets and background shader layer configuration",
    "images": {
      "ui:title": "Branding Images",
      "main_image_url": {
        "ui:widget": "assetUpload",
        "ui:title": "Main Branding Image",
        "ui:help": "Upload or provide URL for the main branding image or logo",
        "ui:options": { "assetType": "image" }
      },
      "logo_watermark_url": {
        "ui:widget": "assetUpload",
        "ui:title": "Watermark Logo",
        "ui:help": "Upload or provide URL for the watermark logo image",
        "ui:options": { "assetType": "image" }
      }
    },
    "shader_background_config": {
      "ui:title": "Shader Background Configuration",
      "ui:description": "Configure the layered scrolling shader background",
      "Front": {
        "ui:title": "Front Layer",
        "ui:description": "Configuration for the foreground shader layer",
        "SpriteUrl": {
          "ui:widget": "assetUpload",
          "ui:title": "Front Layer Sprite",
          "ui:help": "Upload or provide URL for the foreground sprite image",
          "ui:options": { "assetType": "image" }
        },
        "Enabled": {
          "ui:widget": "checkbox",
          "ui:title": "Enable Front Layer",
          "ui:help": "Toggle visibility of the front shader layer"
        },
        "ColorHex": {
          "ui:widget": "colorPicker",
          "ui:title": "Front Layer Color (Hex)",
          "ui:help": "Hex color to tint the front layer sprite"
        },
        "Speed": {
          "ui:widget": "decimalInput",
          "ui:title": "Scroll Speed",
          "ui:placeholder": "e.g. 0.2",
          "ui:help": "Scrolling speed of the front layer"
        },
        "Alpha": {
          "ui:widget": "decimalInput",
          "ui:title": "Alpha (Opacity)",
          "ui:placeholder": "0.0 to 1.0",
          "ui:help": "Opacity of the front layer"
        },
        "Rotation": {
          "ui:widget": "decimalInput",
          "ui:title": "Rotation (degrees)",
          "ui:placeholder": "e.g. 0.0",
          "ui:help": "Rotation angle of the front layer sprite in degrees"
        },
        "Tiling": {
          "ui:title": "Tiling",
          "x": {
            "ui:widget": "decimalInput",
            "ui:title": "Tiling X",
            "ui:placeholder": "e.g. 1.0"
          },
          "y": {
            "ui:widget": "decimalInput",
            "ui:title": "Tiling Y",
            "ui:placeholder": "e.g. 1.0"
          }
        },
        "Direction": {
          "ui:title": "Scroll Direction",
          "x": {
            "ui:widget": "decimalInput",
            "ui:title": "Direction X",
            "ui:placeholder": "e.g. 1.0"
          },
          "y": {
            "ui:widget": "decimalInput",
            "ui:title": "Direction Y",
            "ui:placeholder": "e.g. 0.0"
          }
        }
      },
      "Back": {
        "ui:title": "Back Layer",
        "ui:description": "Configuration for the background shader layer",
        "SpriteUrl": {
          "ui:widget": "assetUpload",
          "ui:title": "Back Layer Sprite",
          "ui:help": "Upload or provide URL for the background sprite image",
          "ui:options": { "assetType": "image" }
        },
        "ColorHex": {
          "ui:widget": "colorPicker",
          "ui:title": "Back Layer Color (Hex)",
          "ui:help": "Hex color to tint the back layer sprite"
        },
        "Speed": {
          "ui:widget": "decimalInput",
          "ui:title": "Scroll Speed",
          "ui:placeholder": "e.g. 0.0",
          "ui:help": "Scrolling speed of the back layer"
        },
        "Alpha": {
          "ui:widget": "decimalInput",
          "ui:title": "Alpha (Opacity)",
          "ui:placeholder": "0.0 to 1.0",
          "ui:help": "Opacity of the back layer"
        },
        "Rotation": {
          "ui:widget": "decimalInput",
          "ui:title": "Rotation (degrees)",
          "ui:placeholder": "e.g. 0.0",
          "ui:help": "Rotation angle of the back layer sprite in degrees"
        },
        "Tiling": {
          "ui:title": "Tiling",
          "x": {
            "ui:widget": "decimalInput",
            "ui:title": "Tiling X",
            "ui:placeholder": "e.g. 1.0"
          },
          "y": {
            "ui:widget": "decimalInput",
            "ui:title": "Tiling Y",
            "ui:placeholder": "e.g. 1.0"
          }
        },
        "Direction": {
          "ui:title": "Scroll Direction",
          "x": {
            "ui:widget": "decimalInput",
            "ui:title": "Direction X",
            "ui:placeholder": "e.g. 0.0"
          },
          "y": {
            "ui:widget": "decimalInput",
            "ui:title": "Direction Y",
            "ui:placeholder": "e.g. 0.0"
          }
        }
      }
    }
  },
  "texts": {
    "ui:title": "Texts",
    "ui:description": "Display texts for game result screens",
    "victory_title": {
      "ui:widget": "textInput",
      "ui:title": "Victory Title",
      "ui:placeholder": "e.g. ¡HAS GANADO!",
      "ui:help": "Title shown on the victory screen"
    },
    "victory_phrase": {
      "ui:widget": "textInput",
      "ui:title": "Victory Phrase",
      "ui:placeholder": "e.g. ¡Has recogido todos los objetos de la lista!",
      "ui:help": "Motivational phrase shown on the victory screen"
    },
    "defeat_title": {
      "ui:widget": "textInput",
      "ui:title": "Defeat Title",
      "ui:placeholder": "e.g. HAS PERDIDO",
      "ui:help": "Title shown on the defeat screen"
    },
    "defeat_phrase": {
      "ui:widget": "textInput",
      "ui:title": "Defeat Phrase",
      "ui:placeholder": "e.g. Inténtalo de nuevo.",
      "ui:help": "Motivational phrase shown on the defeat screen"
    }
  },
  "game_config": {
    "ui:title": "Game Configuration",
    "ui:description": "Core session timing and player life settings",
    "duration": {
      "ui:widget": "decimalInput",
      "ui:title": "Duration (seconds)",
      "ui:placeholder": "e.g. 60.0",
      "ui:help": "Total duration of a game session in seconds"
    },
    "spawn_rate": {
      "ui:widget": "decimalInput",
      "ui:title": "Spawn Rate (seconds)",
      "ui:placeholder": "e.g. 1.2",
      "ui:help": "Time interval in seconds between each object spawn"
    },
    "lives": {
      "ui:widget": "numberInput",
      "ui:title": "Lives",
      "ui:placeholder": "e.g. 3",
      "ui:help": "Number of lives the player starts with each session"
    }
  },
  "game": {
    "ui:title": "Game",
    "ui:description": "Gameplay mechanics, collectible objects, and visual assets",
    "fall_speed_min": {
      "ui:widget": "decimalInput",
      "ui:title": "Fall Speed Min",
      "ui:placeholder": "e.g. 2.0",
      "ui:help": "Minimum falling speed of spawned objects"
    },
    "fall_speed_max": {
      "ui:widget": "decimalInput",
      "ui:title": "Fall Speed Max",
      "ui:placeholder": "e.g. 4.0",
      "ui:help": "Maximum falling speed of spawned objects"
    },
    "basket_speed": {
      "ui:widget": "decimalInput",
      "ui:title": "Basket Speed",
      "ui:placeholder": "e.g. 15.0",
      "ui:help": "Horizontal movement speed of the player basket"
    },
    "shopping_list_size": {
      "ui:widget": "numberInput",
      "ui:title": "Shopping List Size",
      "ui:placeholder": "e.g. 3",
      "ui:help": "Number of distinct items shown in the shopping list per round"
    },
    "min_quantity": {
      "ui:widget": "numberInput",
      "ui:title": "Min Quantity",
      "ui:placeholder": "e.g. 2",
      "ui:help": "Minimum quantity required per item in the shopping list"
    },
    "max_quantity": {
      "ui:widget": "numberInput",
      "ui:title": "Max Quantity",
      "ui:placeholder": "e.g. 5",
      "ui:help": "Maximum quantity required per item in the shopping list"
    },
    "list_bg_image_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Shopping List Background Image",
      "ui:help": "Upload or provide URL for the shopping list UI background image",
      "ui:options": { "assetType": "image" }
    },
    "basket_sprite_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Basket Sprite",
      "ui:help": "Upload or provide URL for the player basket sprite image",
      "ui:options": { "assetType": "image" }
    },
    "objects": {
      "ui:title": "Objects",
      "ui:description": "Define all collectible items and obstacles used in the game",
      "ui:help": "Add one entry per object type. Mark obstacles with the Is Obstacle flag.",
      "items": {
        "ui:order": ["id", "sprite_url", "score", "scale", "is_obstacle"],
        "id": {
          "ui:widget": "textInput",
          "ui:title": "Object ID",
          "ui:placeholder": "e.g. item_1",
          "ui:help": "Unique identifier for this object type"
        },
        "sprite_url": {
          "ui:widget": "assetUpload",
          "ui:title": "Object Sprite",
          "ui:help": "Upload or provide URL for this object sprite image",
          "ui:options": { "assetType": "image" }
        },
        "score": {
          "ui:widget": "numberInput",
          "ui:title": "Score Value",
          "ui:placeholder": "e.g. 10",
          "ui:help": "Score awarded or deducted when this object is collected"
        },
        "scale": {
          "ui:widget": "decimalInput",
          "ui:title": "Scale",
          "ui:placeholder": "e.g. 1.0",
          "ui:help": "Visual scale multiplier for this object sprite"
        },
        "is_obstacle": {
          "ui:widget": "checkbox",
          "ui:title": "Is Obstacle",
          "ui:help": "Enable if this object should penalize the player on collection"
        }
      }
    }
  },
  "audio": {
    "ui:title": "Audio",
    "ui:description": "Audio asset URLs for background music and sound effects",
    "music_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Background Music",
      "ui:help": "Upload or provide URL for the background music track",
      "ui:options": { "assetType": "audio" }
    },
    "positive_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Positive Sound",
      "ui:help": "Upload or provide URL for the successful collection sound effect",
      "ui:options": { "assetType": "audio" }
    },
    "negative_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Negative Sound",
      "ui:help": "Upload or provide URL for the failure or obstacle hit sound effect",
      "ui:options": { "assetType": "audio" }
    },
    "spawn_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Spawn Sound",
      "ui:help": "Upload or provide URL for the object spawn sound effect",
      "ui:options": { "assetType": "audio" }
    }
  },
  "rewards": {
    "ui:title": "Rewards",
    "ui:description": "Coin reward values for player actions and level completion",
    "coins_per_action": {
      "ui:widget": "numberInput",
      "ui:title": "Coins Per Action",
      "ui:placeholder": "e.g. 10",
      "ui:help": "Coins awarded to the player for each successful in-game action"
    },
    "coins_on_completion": {
      "ui:widget": "numberInput",
      "ui:title": "Coins On Completion",
      "ui:placeholder": "e.g. 100",
      "ui:help": "Coins awarded to the player upon successfully completing the game"
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
  }
}',
    true,
    true,
    NOW(),
    'system',
    15000,
    5000,
    20000,
    1,
    60
WHERE NOT EXISTS (
    SELECT 1 FROM game_config_definitions 
    WHERE game_id = 4 AND version = 1
);