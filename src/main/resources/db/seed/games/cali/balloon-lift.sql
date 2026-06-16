INSERT INTO games (id, title, description, delivery_type, url, front_page_url, active, created_at, updated_at)
SELECT
		 3,
    'Balloon Lift',
    'Controla tu vehículo esquivando obstáculos en la carretera. Sobrevive el mayor tiempo posible.',
    'QUERY',
    'Balloon%20Lift',
    'https://games.verygana.com/game_icons/cali/balloon_lift.png',
     true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM games WHERE id = 3);
 
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
    3,
    1,
    '{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "title": "Game Configuration",
  "required": ["meta", "game_config", "branding", "game", "audio", "texts", "rewards", "personalization"],
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
          "default": "0001"
        }
      }
    },
    "game_config": {
      "type": "object",
      "title": "Game Configuration",
      "description": "Core gameplay configuration parameters",
      "required": ["time_limit", "difficulty", "max_attempts"],
      "properties": {
        "time_limit": {
          "type": "integer",
          "title": "Time Limit",
          "description": "Maximum time allowed per game session in seconds",
          "minimum": 10,
          "maximum": 600,
          "default": 150
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
          "description": "Maximum number of attempts allowed per player",
          "minimum": 1,
          "maximum": 10,
          "default": 3
        }
      }
    },
    "branding": {
      "type": "object",
      "title": "Branding",
      "description": "Visual branding assets and configuration",
      "required": ["images", "shader_background_config"],
      "properties": {
        "images": {
          "type": "object",
          "title": "Branding Images",
          "description": "Logo and watermark image assets",
          "required": ["main_logo_url", "main_logo_offset_y", "logo_watermark_url", "logo_watermark_offset_y"],
          "properties": {
            "main_logo_url": {
              "type": ["string", "null"],
              "title": "Main Logo URL",
              "description": "URL of the main logo image",
              "format": "uri",
              "default": "https://placehold.co/400x200/FF5733/FFFFFF.png?text=LOGO"
            },
            "main_logo_offset_y": {
              "type": "integer",
              "title": "Main Logo Offset Y",
              "description": "Vertical offset in pixels for the main logo positioning",
              "minimum": -1000,
              "maximum": 1000,
              "default": 100
            },
            "logo_watermark_url": {
              "type": ["string", "null"],
              "title": "Logo Watermark URL",
              "description": "URL of the watermark logo image",
              "format": "uri",
              "default": "https://placehold.co/150x50/333333/FFFFFF.png?text=WATERMARK"
            },
            "logo_watermark_offset_y": {
              "type": "integer",
              "title": "Logo Watermark Offset Y",
              "description": "Vertical offset in pixels for the watermark logo positioning",
              "minimum": -1000,
              "maximum": 1000,
              "default": -200
            }
          }
        },
        "shader_background_config": {
          "type": "object",
          "title": "Shader Background Configuration",
          "description": "Configuration for layered shader background rendering",
          "required": ["Back", "Front"],
          "properties": {
            "Back": {
              "type": "object",
              "title": "Back Layer",
              "description": "Configuration for the background shader layer",
              "required": ["SpriteUrl", "Enabled", "ColorHex", "Speed", "Alpha", "Tiling", "Direction", "Rotation"],
              "properties": {
                "SpriteUrl": {
                  "type": ["string", "null"],
                  "title": "Sprite URL",
                  "description": "URL of the background sprite image",
                  "format": "uri",
                  "default": "https://placehold.co/1024x512/000033/FFFFFF.png?text=BG"
                },
                "Enabled": {
                  "type": "boolean",
                  "title": "Enabled",
                  "description": "Whether this background layer is active",
                  "default": true
                },
                "ColorHex": {
                  "type": "string",
                  "title": "Color Hex",
                  "description": "RGBA hex color applied to the sprite",
                  "minLength": 7,
                  "maxLength": 9,
                  "default": "#FFFFFFFF"
                },
                "Speed": {
                  "type": "number",
                  "title": "Speed",
                  "description": "Scrolling speed of the background layer",
                  "minimum": 0.0,
                  "maximum": 10.0,
                  "multipleOf": 0.01,
                  "default": 0.05
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
                      "default": 1
                    },
                    "y": {
                      "type": "number",
                      "title": "Tiling Y",
                      "description": "Vertical tiling factor",
                      "minimum": 0.0,
                      "maximum": 100.0,
                      "multipleOf": 0.01,
                      "default": 1
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
                      "default": 1
                    },
                    "y": {
                      "type": "number",
                      "title": "Direction Y",
                      "description": "Vertical component of scrolling direction",
                      "minimum": -1.0,
                      "maximum": 1.0,
                      "multipleOf": 0.01,
                      "default": 0
                    }
                  }
                },
                "Rotation": {
                  "type": "number",
                  "title": "Rotation",
                  "description": "Rotation angle in degrees for the background layer",
                  "minimum": -360.0,
                  "maximum": 360.0,
                  "multipleOf": 0.01,
                  "default": 0
                }
              }
            },
            "Front": {
              "type": "object",
              "title": "Front Layer",
              "description": "Configuration for the foreground shader layer",
              "required": ["SpriteUrl", "Enabled", "ColorHex", "Speed", "Alpha"],
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
                  "description": "RGBA hex color applied to the foreground sprite",
                  "minLength": 7,
                  "maxLength": 9,
                  "default": "#FFFFFF00"
                },
                "Speed": {
                  "type": "number",
                  "title": "Speed",
                  "description": "Scrolling speed of the foreground layer",
                  "minimum": 0.0,
                  "maximum": 10.0,
                  "multipleOf": 0.01,
                  "default": 0.0
                },
                "Alpha": {
                  "type": "number",
                  "title": "Alpha",
                  "description": "Opacity of the foreground layer (0.0 transparent to 1.0 opaque)",
                  "minimum": 0.0,
                  "maximum": 1.0,
                  "multipleOf": 0.01,
                  "default": 0.0
                }
              }
            }
          }
        }
      }
    },
    "game": {
      "type": "object",
      "title": "Game",
      "description": "Main game assets, stages, obstacles, and physics attributes",
      "required": ["images", "stages", "obstacle_models_urls", "obstacle_sprite_urls", "coin_model_url", "power_ups_config", "attributes"],
      "properties": {
        "images": {
          "type": "object",
          "title": "Game Images",
          "description": "Primary and parallax game image assets",
          "required": ["main_image_url", "parallax_image_url"],
          "properties": {
            "main_image_url": {
              "type": ["string", "null"],
              "title": "Main Image URL",
              "description": "URL of the main game character or object image",
              "format": "uri",
              "default": "https://placehold.co/800x800/FF0000/FFFFFF.png?text=BALLOON"
            },
            "parallax_image_url": {
              "type": ["string", "null"],
              "title": "Parallax Image URL",
              "description": "URL of the parallax scrolling background image",
              "format": "uri",
              "default": "https://placehold.co/512x256/00FF00/FFFFFF.png?text=PARALLAX"
            }
          }
        },
        "stages": {
          "type": "array",
          "title": "Stages",
          "description": "List of game stage configurations",
          "items": {
            "type": "object",
            "title": "Stage",
            "description": "Individual stage configuration",
            "required": [],
            "properties": {}
          },
          "default": []
        },
        "obstacle_models_urls": {
          "type": "array",
          "title": "Obstacle Model URLs",
          "description": "List of URLs for 3D obstacle model assets",
          "items": {
            "type": "string",
            "title": "Obstacle Model URL",
            "format": "uri"
          },
          "default": []
        },
        "obstacle_sprite_urls": {
          "type": "array",
          "title": "Obstacle Sprite URLs",
          "description": "List of URLs for obstacle sprite image assets",
          "items": {
            "type": "string",
            "title": "Obstacle Sprite URL",
            "format": "uri"
          },
          "default": [
            "https://placehold.co/800x800/FF0000/FFFFFF.png?text=OBS_1",
            "https://placehold.co/800x800/AA0000/FFFFFF.png?text=OBS_2",
            "https://placehold.co/800x800/660000/FFFFFF.png?text=OBS_3"
          ]
        },
        "coin_model_url": {
          "type": ["string", "null"],
          "title": "Coin Model URL",
          "description": "URL of the 3D coin model asset",
          "format": "uri",
          "default": ""
        },
        "power_ups_config": {
          "type": "array",
          "title": "Power-Ups Configuration",
          "description": "List of power-up item configurations",
          "items": {
            "type": "object",
            "title": "Power-Up",
            "description": "Individual power-up configuration",
            "required": [],
            "properties": {}
          },
          "default": []
        },
        "attributes": {
          "type": "object",
          "title": "Game Attributes",
          "description": "Physics and gameplay attribute values",
          "required": ["lift_force", "horizontal_force", "max_velocity", "level_duration", "gravity", "obstacle_spawn_interval", "obstacle_speed", "sway_frequency", "sway_magnitude"],
          "properties": {
            "lift_force": {
              "type": "number",
              "title": "Lift Force",
              "description": "Upward force applied to the player character",
              "minimum": 0.0,
              "maximum": 50.0,
              "multipleOf": 0.01,
              "default": 4.0
            },
            "horizontal_force": {
              "type": "number",
              "title": "Horizontal Force",
              "description": "Horizontal movement force applied to the player character",
              "minimum": 0.0,
              "maximum": 50.0,
              "multipleOf": 0.01,
              "default": 10.0
            },
            "max_velocity": {
              "type": "number",
              "title": "Max Velocity",
              "description": "Maximum movement speed of the player character",
              "minimum": 0.0,
              "maximum": 100.0,
              "multipleOf": 0.01,
              "default": 8.0
            },
            "level_duration": {
              "type": "number",
              "title": "Level Duration",
              "description": "Duration of each level in seconds",
              "minimum": 0.0,
              "maximum": 600.0,
              "multipleOf": 0.01,
              "default": 120.0
            },
            "gravity": {
              "type": "number",
              "title": "Gravity",
              "description": "Gravitational force applied to the player character",
              "minimum": 0.0,
              "maximum": 20.0,
              "multipleOf": 0.01,
              "default": 1.0
            },
            "obstacle_spawn_interval": {
              "type": "number",
              "title": "Obstacle Spawn Interval",
              "description": "Time interval in seconds between obstacle spawns",
              "minimum": 0.1,
              "maximum": 30.0,
              "multipleOf": 0.01,
              "default": 1.5
            },
            "obstacle_speed": {
              "type": "number",
              "title": "Obstacle Speed",
              "description": "Movement speed of spawned obstacles",
              "minimum": 0.0,
              "maximum": 50.0,
              "multipleOf": 0.01,
              "default": 4.0
            },
            "sway_frequency": {
              "type": "number",
              "title": "Sway Frequency",
              "description": "Frequency of the player character sway animation",
              "minimum": 0.0,
              "maximum": 20.0,
              "multipleOf": 0.01,
              "default": 1.5
            },
            "sway_magnitude": {
              "type": "number",
              "title": "Sway Magnitude",
              "description": "Amplitude of the player character sway animation",
              "minimum": 0.0,
              "maximum": 10.0,
              "multipleOf": 0.01,
              "default": 0.5
            }
          }
        }
      }
    },
    "audio": {
      "type": "object",
      "title": "Audio",
      "description": "Audio asset URLs for music and sound effects",
      "required": ["music_url", "victory_url", "lose_url", "coin_url", "obstacle_url", "powerup_url", "shield_url"],
      "properties": {
        "music_url": {
          "type": ["string", "null"],
          "title": "Music URL",
          "description": "URL of the background music audio file",
          "format": "uri",
          "default": "https://rtainor.com/verygana/musicmemory/nokia.mp3"
        },
        "victory_url": {
          "type": ["string", "null"],
          "title": "Victory Sound URL",
          "description": "URL of the victory audio file",
          "format": "uri",
          "default": "https://rtainor.com/verygana/musicmemory/nokia.mp3"
        },
        "lose_url": {
          "type": ["string", "null"],
          "title": "Lose Sound URL",
          "description": "URL of the defeat audio file",
          "format": "uri",
          "default": "https://rtainor.com/verygana/musicmemory/nokia.mp3"
        },
        "coin_url": {
          "type": ["string", "null"],
          "title": "Coin Sound URL",
          "description": "URL of the coin collection sound effect",
          "format": "uri",
          "default": "https://cdn.aframe.io/360-image-gallery-boilerplate/audio/click.ogg"
        },
        "obstacle_url": {
          "type": ["string", "null"],
          "title": "Obstacle Sound URL",
          "description": "URL of the obstacle collision sound effect",
          "format": "uri",
          "default": "https://cdn.aframe.io/360-image-gallery-boilerplate/audio/click.ogg"
        },
        "powerup_url": {
          "type": ["string", "null"],
          "title": "Power-Up Sound URL",
          "description": "URL of the power-up activation sound effect",
          "format": "uri",
          "default": "https://cdn.aframe.io/360-image-gallery-boilerplate/audio/click.ogg"
        },
        "shield_url": {
          "type": ["string", "null"],
          "title": "Shield Sound URL",
          "description": "URL of the shield activation sound effect",
          "format": "uri",
          "default": "https://cdn.aframe.io/360-image-gallery-boilerplate/audio/click.ogg"
        }
      }
    },
    "texts": {
      "type": "object",
      "title": "Texts",
      "description": "In-game display texts for victory and defeat screens",
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
          "default": "¡Felicidades, has ganado!"
        },
        "defeat_title": {
          "type": "string",
          "title": "Defeat Title",
          "description": "Title text displayed on the defeat screen",
          "minLength": 1,
          "maxLength": 100,
          "default": "DERROTA"
        },
        "defeat_phrase": {
          "type": "string",
          "title": "Defeat Phrase",
          "description": "Motivational phrase displayed on the defeat screen",
          "minLength": 1,
          "maxLength": 300,
          "default": "¡No te rindas, inténtalo de nuevo!"
        }
      }
    },
    "rewards": {
      "type": "object",
      "title": "Rewards",
      "description": "Coin reward configuration for player actions and completion",
      "required": ["coins_per_action", "coins_on_completion"],
      "properties": {
        "coins_per_action": {
          "type": "integer",
          "title": "Coins Per Action",
          "description": "Number of coins awarded for each in-game action",
          "minimum": 0,
          "maximum": 10000,
          "default": 20
        },
        "coins_on_completion": {
          "type": "integer",
          "title": "Coins On Completion",
          "description": "Number of coins awarded upon completing the game",
          "minimum": 0,
          "maximum": 100000,
          "default": 100
        }
      }
    },
    "personalization": {
      "type": "object",
      "title": "Personalization",
      "description": "Custom visual asset URLs for personalized game elements",
      "required": ["coin_url", "coin_count_url"],
      "properties": {
        "coin_url": {
          "type": ["string", "null"],
          "title": "Coin Image URL",
          "description": "URL of the custom coin image asset",
          "format": "uri",
          "default": "https://placehold.co/500x500/FFD700/FFFFFF.png?text=COIN"
        },
        "coin_count_url": {
          "type": ["string", "null"],
          "title": "Coin Count Image URL",
          "description": "URL of the custom coin counter display image asset",
          "format": "uri",
          "default": "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COUNT"
        }
      }
    }
  }
}',
    '{
  "ui:order": ["meta", "game_config", "branding", "game", "audio", "texts", "rewards", "personalization"],
  "meta": {
    "ui:title": "Metadata",
    "ui:description": "General brand and configuration metadata",
    "brand_id": {
      "ui:widget": "textInput",
      "ui:title": "Brand ID",
      "ui:placeholder": "e.g. 0001",
      "ui:help": "Unique identifier assigned to this brand"
    }
  },
  "game_config": {
    "ui:title": "Game Configuration",
    "ui:description": "Core gameplay rules and parameters",
    "time_limit": {
      "ui:widget": "numberInput",
      "ui:title": "Time Limit (seconds)",
      "ui:placeholder": "e.g. 150",
      "ui:help": "Total time allowed per game session in seconds"
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
      "ui:help": "Maximum number of attempts a player can make"
    }
  },
  "branding": {
    "ui:title": "Branding",
    "ui:description": "Logo assets and background shader layer configuration",
    "images": {
      "ui:title": "Branding Images",
      "main_logo_url": {
        "ui:widget": "assetUpload",
        "ui:title": "Main Logo",
        "ui:help": "Upload or provide URL for the main logo image",
        "ui:options": { "assetType": "image" }
      },
      "main_logo_offset_y": {
        "ui:widget": "numberInput",
        "ui:title": "Main Logo Vertical Offset",
        "ui:placeholder": "e.g. 100",
        "ui:help": "Vertical pixel offset to position the main logo"
      },
      "logo_watermark_url": {
        "ui:widget": "assetUpload",
        "ui:title": "Watermark Logo",
        "ui:help": "Upload or provide URL for the watermark logo image",
        "ui:options": { "assetType": "image" }
      },
      "logo_watermark_offset_y": {
        "ui:widget": "numberInput",
        "ui:title": "Watermark Logo Vertical Offset",
        "ui:placeholder": "e.g. -200",
        "ui:help": "Vertical pixel offset to position the watermark logo"
      }
    },
    "shader_background_config": {
      "ui:title": "Shader Background Configuration",
      "ui:description": "Configure the layered scrolling shader background",
      "Back": {
        "ui:title": "Back Layer",
        "ui:description": "Configuration for the rear background shader layer",
        "SpriteUrl": {
          "ui:widget": "assetUpload",
          "ui:title": "Back Layer Sprite",
          "ui:help": "Upload or provide URL for the background sprite image",
          "ui:options": { "assetType": "image" }
        },
        "Enabled": {
          "ui:widget": "checkbox",
          "ui:title": "Enable Back Layer",
          "ui:help": "Toggle visibility of the back shader layer"
        },
        "ColorHex": {
          "ui:widget": "colorPicker",
          "ui:title": "Back Layer Color (RGBA Hex)",
          "ui:help": "RGBA hex color to tint the back layer sprite"
        },
        "Speed": {
          "ui:widget": "decimalInput",
          "ui:title": "Scroll Speed",
          "ui:placeholder": "e.g. 0.05",
          "ui:help": "Scrolling speed of the back layer"
        },
        "Alpha": {
          "ui:widget": "decimalInput",
          "ui:title": "Alpha (Opacity)",
          "ui:placeholder": "0.0 to 1.0",
          "ui:help": "Opacity of the back layer (0.0 = transparent, 1.0 = opaque)"
        },
        "Tiling": {
          "ui:title": "Tiling",
          "ui:description": "Repetition factor for the sprite texture",
          "x": {
            "ui:widget": "decimalInput",
            "ui:title": "Tiling X",
            "ui:placeholder": "e.g. 1"
          },
          "y": {
            "ui:widget": "decimalInput",
            "ui:title": "Tiling Y",
            "ui:placeholder": "e.g. 1"
          }
        },
        "Direction": {
          "ui:title": "Scroll Direction",
          "ui:description": "Direction vector for background scrolling (-1 to 1)",
          "x": {
            "ui:widget": "decimalInput",
            "ui:title": "Direction X",
            "ui:placeholder": "e.g. 1"
          },
          "y": {
            "ui:widget": "decimalInput",
            "ui:title": "Direction Y",
            "ui:placeholder": "e.g. 0"
          }
        },
        "Rotation": {
          "ui:widget": "decimalInput",
          "ui:title": "Rotation (degrees)",
          "ui:placeholder": "e.g. 0",
          "ui:help": "Rotation angle of the back layer sprite in degrees"
        }
      },
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
          "ui:title": "Front Layer Color (RGBA Hex)",
          "ui:help": "RGBA hex color to tint the front layer sprite"
        },
        "Speed": {
          "ui:widget": "decimalInput",
          "ui:title": "Scroll Speed",
          "ui:placeholder": "e.g. 0.0",
          "ui:help": "Scrolling speed of the front layer"
        },
        "Alpha": {
          "ui:widget": "decimalInput",
          "ui:title": "Alpha (Opacity)",
          "ui:placeholder": "0.0 to 1.0",
          "ui:help": "Opacity of the front layer (0.0 = transparent, 1.0 = opaque)"
        }
      }
    }
  },
  "game": {
    "ui:title": "Game",
    "ui:description": "Game assets, obstacles, power-ups, and physics attributes",
    "images": {
      "ui:title": "Game Images",
      "main_image_url": {
        "ui:widget": "assetUpload",
        "ui:title": "Main Game Image",
        "ui:help": "Upload or provide URL for the main character or game object image",
        "ui:options": { "assetType": "image" }
      },
      "parallax_image_url": {
        "ui:widget": "assetUpload",
        "ui:title": "Parallax Background Image",
        "ui:help": "Upload or provide URL for the parallax scrolling background image",
        "ui:options": { "assetType": "image" }
      }
    },
    "stages": {
      "ui:title": "Stages",
      "ui:description": "Define the game stages and their configurations",
      "ui:help": "Add one entry per stage"
    },
    "obstacle_models_urls": {
      "ui:title": "Obstacle Model URLs",
      "ui:description": "List of 3D model asset URLs for obstacles",
      "items": {
        "ui:widget": "assetUpload",
        "ui:title": "Obstacle Model",
        "ui:options": { "assetType": "image" }
      }
    },
    "obstacle_sprite_urls": {
      "ui:title": "Obstacle Sprite URLs",
      "ui:description": "List of sprite image URLs for obstacles",
      "items": {
        "ui:widget": "assetUpload",
        "ui:title": "Obstacle Sprite",
        "ui:options": { "assetType": "image" }
      }
    },
    "coin_model_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Coin Model URL",
      "ui:help": "Upload or provide URL for the 3D coin model asset",
      "ui:options": { "assetType": "image" }
    },
    "power_ups_config": {
      "ui:title": "Power-Ups Configuration",
      "ui:description": "Define available power-ups and their settings",
      "ui:help": "Add one entry per power-up type"
    },
    "attributes": {
      "ui:title": "Game Attributes",
      "ui:description": "Physics and gameplay tuning parameters",
      "lift_force": {
        "ui:widget": "decimalInput",
        "ui:title": "Lift Force",
        "ui:placeholder": "e.g. 4.0",
        "ui:help": "Upward force applied to the player character on input"
      },
      "horizontal_force": {
        "ui:widget": "decimalInput",
        "ui:title": "Horizontal Force",
        "ui:placeholder": "e.g. 10.0",
        "ui:help": "Lateral force applied to the player character"
      },
      "max_velocity": {
        "ui:widget": "decimalInput",
        "ui:title": "Max Velocity",
        "ui:placeholder": "e.g. 8.0",
        "ui:help": "Maximum movement speed the player character can reach"
      },
      "level_duration": {
        "ui:widget": "decimalInput",
        "ui:title": "Level Duration (seconds)",
        "ui:placeholder": "e.g. 120.0",
        "ui:help": "Total duration of a single level in seconds"
      },
      "gravity": {
        "ui:widget": "decimalInput",
        "ui:title": "Gravity",
        "ui:placeholder": "e.g. 1.0",
        "ui:help": "Gravitational pull applied to the player character"
      },
      "obstacle_spawn_interval": {
        "ui:widget": "decimalInput",
        "ui:title": "Obstacle Spawn Interval (seconds)",
        "ui:placeholder": "e.g. 1.5",
        "ui:help": "Time in seconds between each obstacle spawn"
      },
      "obstacle_speed": {
        "ui:widget": "decimalInput",
        "ui:title": "Obstacle Speed",
        "ui:placeholder": "e.g. 4.0",
        "ui:help": "Speed at which obstacles move across the screen"
      },
      "sway_frequency": {
        "ui:widget": "decimalInput",
        "ui:title": "Sway Frequency",
        "ui:placeholder": "e.g. 1.5",
        "ui:help": "Frequency of the player character sway oscillation"
      },
      "sway_magnitude": {
        "ui:widget": "decimalInput",
        "ui:title": "Sway Magnitude",
        "ui:placeholder": "e.g. 0.5",
        "ui:help": "Amplitude of the player character sway oscillation"
      }
    }
  },
  "audio": {
    "ui:title": "Audio",
    "ui:description": "Audio asset URLs for music and sound effects",
    "music_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Background Music",
      "ui:help": "Upload or provide URL for the background music track",
      "ui:options": { "assetType": "audio" }
    },
    "victory_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Victory Sound",
      "ui:help": "Upload or provide URL for the victory audio clip",
      "ui:options": { "assetType": "audio" }
    },
    "lose_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Defeat Sound",
      "ui:help": "Upload or provide URL for the defeat audio clip",
      "ui:options": { "assetType": "audio" }
    },
    "coin_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Coin Sound",
      "ui:help": "Upload or provide URL for the coin collection sound effect",
      "ui:options": { "assetType": "audio" }
    },
    "obstacle_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Obstacle Sound",
      "ui:help": "Upload or provide URL for the obstacle collision sound effect",
      "ui:options": { "assetType": "audio" }
    },
    "powerup_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Power-Up Sound",
      "ui:help": "Upload or provide URL for the power-up activation sound effect",
      "ui:options": { "assetType": "audio" }
    },
    "shield_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Shield Sound",
      "ui:help": "Upload or provide URL for the shield activation sound effect",
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
      "ui:placeholder": "e.g. ¡Felicidades, has ganado!",
      "ui:help": "Motivational phrase shown on the victory screen"
    },
    "defeat_title": {
      "ui:widget": "textInput",
      "ui:title": "Defeat Title",
      "ui:placeholder": "e.g. DERROTA",
      "ui:help": "Title shown on the defeat screen"
    },
    "defeat_phrase": {
      "ui:widget": "textInput",
      "ui:title": "Defeat Phrase",
      "ui:placeholder": "e.g. ¡No te rindas, inténtalo de nuevo!",
      "ui:help": "Motivational phrase shown on the defeat screen"
    }
  },
  "rewards": {
    "ui:title": "Rewards",
    "ui:description": "Coin reward values for player actions and level completion",
    "coins_per_action": {
      "ui:widget": "numberInput",
      "ui:title": "Coins Per Action",
      "ui:placeholder": "e.g. 20",
      "ui:help": "Coins awarded to the player for each qualifying in-game action"
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
    WHERE game_id = 3 AND version = 1
);