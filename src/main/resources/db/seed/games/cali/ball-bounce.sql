INSERT INTO games (id, title, description, delivery_type, url, front_page_url, active, created_at, updated_at)
SELECT
		 2,
    'Ball Bounce',
    'Rompe todos los ladrillos con la pelota antes de que se acabe el tiempo. Recoge power-ups para conseguir ventajas.',
    'QUERY',
    'Ball%20Bounce',
    'https://games.verygana.com/game_icons/cali/ball_bounce.png',
     true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM games WHERE id = 2);

 
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
    2,
    1,
    '{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "title": "Breakout Game Configuration",
  "description": "Full configuration object for a branded Breakout game instance",
  "required": ["meta", "branding", "game_config", "game", "audio", "texts", "rewards", "personalization"],
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
          "description": "Unique identifier for the brand associated with this game",
          "default": "default",
          "minLength": 1,
          "maxLength": 100
        }
      }
    },
    "branding": {
      "type": "object",
      "title": "Branding",
      "description": "Visual branding assets and background shader configuration",
      "required": ["images", "shader_background_config"],
      "properties": {
        "images": {
          "type": "object",
          "title": "Images",
          "description": "Brand image assets used in the game UI",
          "required": ["main_image_url", "main_image_offset_y", "logo_watermark_url", "logo_watermark_offset_y"],
          "properties": {
            "main_image_url": {
              "type": ["string", "null"],
              "title": "Main Image URL",
              "description": "URL of the main brand image displayed in the game",
              "format": "uri",
              "default": "https://placehold.co/400x200/FF5733/FFFFFF.png?text=LOGO"
            },
            "main_image_offset_y": {
              "type": "number",
              "title": "Main Image Offset Y",
              "description": "Vertical offset in pixels for the main image position",
              "default": 0,
              "minimum": -2000,
              "maximum": 2000
            },
            "logo_watermark_url": {
              "type": ["string", "null"],
              "title": "Logo Watermark URL",
              "description": "URL of the watermark logo overlay",
              "format": "uri",
              "default": "https://placehold.co/150x50/333333/FFFFFF.png?text=WATERMARK"
            },
            "logo_watermark_offset_y": {
              "type": "number",
              "title": "Logo Watermark Offset Y",
              "description": "Vertical offset in pixels for the watermark logo position",
              "default": 0,
              "minimum": -2000,
              "maximum": 2000
            }
          }
        },
        "shader_background_config": {
          "type": "object",
          "title": "Shader Background Config",
          "description": "Configuration for the background shader and layer sprites",
          "required": ["PrimaryColorHex", "SecondaryColorHex", "ParticleColorHex", "Speed", "Difficulty", "UseShader", "Front", "Back"],
          "properties": {
            "PrimaryColorHex": {
              "type": "string",
              "title": "Primary Color (Hex)",
              "description": "Primary background shader color in hex format",
              "default": "#1a0b2e",
              "minLength": 7,
              "maxLength": 9
            },
            "SecondaryColorHex": {
              "type": "string",
              "title": "Secondary Color (Hex)",
              "description": "Secondary background shader color in hex format",
              "default": "#2d1b4e",
              "minLength": 7,
              "maxLength": 9
            },
            "ParticleColorHex": {
              "type": "string",
              "title": "Particle Color (Hex)",
              "description": "Color of background shader particles in hex format",
              "default": "#432c7a",
              "minLength": 7,
              "maxLength": 9
            },
            "Speed": {
              "type": "number",
              "title": "Shader Speed",
              "description": "Animation speed of the background shader",
              "default": 0.5,
              "minimum": 0.0,
              "maximum": 10.0
            },
            "Difficulty": {
              "type": "number",
              "title": "Shader Difficulty",
              "description": "Complexity/density parameter of the background shader effect",
              "default": 1.0,
              "minimum": 0.1,
              "maximum": 10.0
            },
            "UseShader": {
              "type": "boolean",
              "title": "Use Shader",
              "description": "Whether to enable the procedural background shader",
              "default": true
            },
            "Front": {
              "type": "object",
              "title": "Front Layer",
              "description": "Configuration for the front (closest) background sprite layer",
              "required": ["Enabled"],
              "properties": {
                "Enabled": {
                  "type": "boolean",
                  "title": "Enabled",
                  "description": "Whether the front sprite layer is active",
                  "default": false
                }
              }
            },
            "Back": {
              "type": "object",
              "title": "Back Layer",
              "description": "Configuration for the back (furthest) background sprite layer",
              "required": ["SpriteUrl", "Enabled"],
              "properties": {
                "SpriteUrl": {
                  "type": ["string", "null"],
                  "title": "Sprite URL",
                  "description": "URL of the sprite texture used for the back layer",
                  "format": "uri",
                  "default": "https://placehold.co/1024x512/000033/FFFFFF.png?text=BG"
                },
                "Enabled": {
                  "type": "boolean",
                  "title": "Enabled",
                  "description": "Whether the back sprite layer is active",
                  "default": true
                }
              }
            }
          }
        }
      }
    },
    "game_config": {
      "type": "object",
      "title": "Game Config",
      "description": "High-level game configuration including difficulty and scoring",
      "required": ["difficulty", "game_duration_sec", "score_per_hit"],
      "properties": {
        "difficulty": {
          "type": "string",
          "title": "Difficulty",
          "description": "Global difficulty preset for the game",
          "enum": ["Easy", "Medium", "Hard"],
          "default": "Medium"
        },
        "game_duration_sec": {
          "type": "integer",
          "title": "Game Duration (seconds)",
          "description": "Total duration of the game session in seconds",
          "default": 60,
          "minimum": 10,
          "maximum": 600
        },
        "score_per_hit": {
          "type": "integer",
          "title": "Score per Hit",
          "description": "Points awarded to the player for each brick hit",
          "default": 10,
          "minimum": 0,
          "maximum": 10000
        }
      }
    },
    "game": {
      "type": "object",
      "title": "Game",
      "description": "Gameplay physics, layout, textures, levels and power-ups",
      "required": ["ball_speed", "paddle_speed", "brick_rows", "brick_columns", "url_ball_texture", "url_paddle_texture", "levels", "power_ups"],
      "properties": {
        "ball_speed": {
          "type": "number",
          "title": "Ball Speed",
          "description": "Default movement speed of the ball in units per second",
          "default": 500.0,
          "minimum": 50.0,
          "maximum": 2000.0
        },
        "paddle_speed": {
          "type": "number",
          "title": "Paddle Speed",
          "description": "Movement speed of the player paddle in units per second",
          "default": 600.0,
          "minimum": 50.0,
          "maximum": 2000.0
        },
        "brick_rows": {
          "type": "integer",
          "title": "Brick Rows",
          "description": "Default number of brick rows in the grid",
          "default": 5,
          "minimum": 1,
          "maximum": 20
        },
        "brick_columns": {
          "type": "integer",
          "title": "Brick Columns",
          "description": "Default number of brick columns in the grid",
          "default": 9,
          "minimum": 1,
          "maximum": 30
        },
        "url_ball_texture": {
          "type": ["string", "null"],
          "title": "Ball Texture URL",
          "description": "URL of the texture image applied to the ball",
          "format": "uri",
          "default": "https://placehold.co/64x64/FFFFFF/000000.png?text=BALL"
        },
        "url_paddle_texture": {
          "type": ["string", "null"],
          "title": "Paddle Texture URL",
          "description": "URL of the texture image applied to the paddle",
          "format": "uri",
          "default": "https://placehold.co/256x64/00FF00/000000.png?text=PADDLE"
        },
        "levels": {
          "type": "array",
          "title": "Levels",
          "description": "Ordered list of level configurations with per-level overrides and power-ups",
          "items": {
            "type": "object",
            "title": "Level",
            "required": ["level_difficulty", "ball_speed_override", "timer_sec", "brick_rows", "brick_columns", "url_image", "power_ups"],
            "properties": {
              "level_difficulty": {
                "type": "string",
                "title": "Level Difficulty",
                "description": "Difficulty label for this specific level",
                "enum": ["Easy", "Medium", "Hard"],
                "default": "Easy"
              },
              "ball_speed_override": {
                "type": "number",
                "title": "Ball Speed Override",
                "description": "Ball speed specific to this level, overriding the global value",
                "default": 450.0,
                "minimum": 50.0,
                "maximum": 2000.0
              },
              "timer_sec": {
                "type": "integer",
                "title": "Timer (seconds)",
                "description": "Time limit in seconds for completing this level",
                "default": 40,
                "minimum": 5,
                "maximum": 600
              },
              "brick_rows": {
                "type": "integer",
                "title": "Brick Rows",
                "description": "Number of brick rows for this level",
                "default": 3,
                "minimum": 1,
                "maximum": 20
              },
              "brick_columns": {
                "type": "integer",
                "title": "Brick Columns",
                "description": "Number of brick columns for this level",
                "default": 4,
                "minimum": 1,
                "maximum": 30
              },
              "url_image": {
                "type": ["string", "null"],
                "title": "Brick Image URL",
                "description": "URL of the texture image applied to bricks in this level",
                "format": "uri",
                "default": "https://placehold.co/256x256/FF0000/FFFFFF.png?text=BRICK"
              },
              "power_ups": {
                "type": "array",
                "title": "Power-Ups",
                "description": "List of power-ups available to drop from bricks in this level",
                "items": {
                  "type": "object",
                  "title": "Power-Up",
                  "required": ["type", "drop_chance", "duration", "color_hex", "url_icon"],
                  "properties": {
                    "type": {
                      "type": "string",
                      "title": "Type",
                      "description": "Identifier of the power-up type",
                      "enum": ["MultiBall", "PaddleGrow", "Laser", "SlowMotion", "Life"],
                      "default": "MultiBall"
                    },
                    "drop_chance": {
                      "type": "number",
                      "title": "Drop Chance",
                      "description": "Probability (0.0 to 1.0) that this power-up drops when a brick is hit",
                      "default": 0.1,
                      "minimum": 0.0,
                      "maximum": 1.0
                    },
                    "duration": {
                      "type": "number",
                      "title": "Duration (seconds)",
                      "description": "Duration of the power-up effect in seconds (0 means instant/permanent)",
                      "default": 0,
                      "minimum": 0,
                      "maximum": 120
                    },
                    "color_hex": {
                      "type": "string",
                      "title": "Color (Hex)",
                      "description": "Display color of the power-up item in hex format",
                      "default": "#FFFFFF",
                      "minLength": 7,
                      "maxLength": 9
                    },
                    "url_icon": {
                      "type": ["string", "null"],
                      "title": "Icon URL",
                      "description": "URL of the icon image displayed for this power-up",
                      "format": "uri",
                      "default": "https://placehold.co/64x64/FFFFFF/000000.png?text=PU"
                    }
                  }
                }
              }
            }
          }
        },
        "power_ups": {
          "type": "array",
          "title": "Global Power-Ups",
          "description": "Default global power-up definitions used when no level-specific overrides are provided",
          "items": {
            "type": "object",
            "title": "Power-Up",
            "required": ["type", "drop_chance", "duration", "color_hex", "url_icon"],
            "properties": {
              "type": {
                "type": "string",
                "title": "Type",
                "description": "Identifier of the power-up type",
                "enum": ["MultiBall", "PaddleGrow", "Laser", "SlowMotion", "Life"],
                "default": "MultiBall"
              },
              "drop_chance": {
                "type": "number",
                "title": "Drop Chance",
                "description": "Probability (0.0 to 1.0) that this power-up drops when a brick is hit",
                "default": 0.1,
                "minimum": 0.0,
                "maximum": 1.0
              },
              "duration": {
                "type": "number",
                "title": "Duration (seconds)",
                "description": "Duration of the power-up effect in seconds (0 means instant/permanent)",
                "default": 0,
                "minimum": 0,
                "maximum": 120
              },
              "color_hex": {
                "type": "string",
                "title": "Color (Hex)",
                "description": "Display color of the power-up item in hex format",
                "default": "#FFFFFF",
                "minLength": 7,
                "maxLength": 9
              },
              "url_icon": {
                "type": ["string", "null"],
                "title": "Icon URL",
                "description": "URL of the icon image displayed for this power-up",
                "format": "uri",
                "default": "https://placehold.co/64x64/FFFFFF/000000.png?text=PU"
              }
            }
          }
        }
      }
    },
    "audio": {
      "type": "object",
      "title": "Audio",
      "description": "Audio asset URLs for game music and sound effects",
      "required": ["hit_url", "brick_url", "win_url", "lose_url", "music_url", "laser_url", "powerup_url"],
      "properties": {
        "hit_url": {
          "type": ["string", "null"],
          "title": "Hit Sound URL",
          "description": "URL of the sound played when the ball hits the paddle",
          "format": "uri",
          "default": ""
        },
        "brick_url": {
          "type": ["string", "null"],
          "title": "Brick Sound URL",
          "description": "URL of the sound played when the ball hits a brick",
          "format": "uri",
          "default": ""
        },
        "win_url": {
          "type": ["string", "null"],
          "title": "Win Sound URL",
          "description": "URL of the sound played when the player wins",
          "format": "uri",
          "default": ""
        },
        "lose_url": {
          "type": ["string", "null"],
          "title": "Lose Sound URL",
          "description": "URL of the sound played when the player loses",
          "format": "uri",
          "default": ""
        },
        "music_url": {
          "type": ["string", "null"],
          "title": "Background Music URL",
          "description": "URL of the background music track played during gameplay",
          "format": "uri",
          "default": ""
        },
        "laser_url": {
          "type": ["string", "null"],
          "title": "Laser Sound URL",
          "description": "URL of the sound played when the Laser power-up fires",
          "format": "uri",
          "default": ""
        },
        "powerup_url": {
          "type": ["string", "null"],
          "title": "Power-Up Sound URL",
          "description": "URL of the sound played when any power-up is collected",
          "format": "uri",
          "default": ""
        }
      }
    },
    "texts": {
      "type": "object",
      "title": "Texts",
      "description": "Customizable text strings displayed in the game UI",
      "required": ["victory_phrase", "defeat_phrase", "victory_title", "defeat_title", "floating_words", "floating_color_hex", "floating_font_size", "show_particles_with_words"],
      "properties": {
        "victory_title": {
          "type": "string",
          "title": "Victory Title",
          "description": "Title text shown on the victory screen",
          "default": "¡VICTORIA!",
          "minLength": 1,
          "maxLength": 50
        },
        "victory_phrase": {
          "type": "string",
          "title": "Victory Phrase",
          "description": "Subtitle phrase shown on the victory screen.",
          "default": "¡Felicidades! Has completado el nivel.",
          "minLength": 1,
          "maxLength": 300
        },
        "defeat_title": {
          "type": "string",
          "title": "Defeat Title",
          "description": "Title text shown on the game over screen",
          "default": "DERROTA",
          "minLength": 1,
          "maxLength": 50
        },
        "defeat_phrase": {
          "type": "string",
          "title": "Defeat Phrase",
          "description": "Subtitle phrase shown on the game over screen.",
          "default": "¡No te rindas! Inténtalo de nuevo.",
          "minLength": 1,
          "maxLength": 300
        },
        "floating_words": {
          "type": "array",
          "title": "Floating Words",
          "description": "List of words randomly displayed as floating text on successful hits",
          "items": {
            "type": "string",
            "title": "Word",
            "minLength": 1,
            "maxLength": 50
          }
        },
        "floating_color_hex": {
          "type": "string",
          "title": "Floating Text Color (Hex)",
          "description": "Color of the floating words in hex format",
          "default": "#00FFFF",
          "minLength": 7,
          "maxLength": 9
        },
        "floating_font_size": {
          "type": "integer",
          "title": "Floating Font Size",
          "description": "Font size in points used for floating words",
          "default": 42,
          "minimum": 8,
          "maximum": 200
        },
        "show_particles_with_words": {
          "type": "boolean",
          "title": "Show Particles with Words",
          "description": "Whether to show a particle burst alongside floating words",
          "default": false
        }
      }
    },
    "rewards": {
      "type": "object",
      "title": "Rewards",
      "description": "Coin reward values for actions and game completion",
      "required": ["coins_per_action", "coins_on_completion"],
      "properties": {
        "coins_per_action": {
          "type": "integer",
          "title": "Coins per Action",
          "description": "Number of coins awarded for each successful brick hit",
          "default": 10,
          "minimum": 0,
          "maximum": 10000
        },
        "coins_on_completion": {
          "type": "integer",
          "title": "Coins on Completion",
          "description": "Bonus coins awarded when the player completes the game",
          "default": 100,
          "minimum": 0,
          "maximum": 100000
        }
      }
    },
    "personalization": {
      "type": "object",
      "title": "Personalization",
      "description": "Customizable UI asset images for coins and counters",
      "required": ["coin_url", "coin_count_url"],
      "properties": {
        "coin_url": {
          "type": ["string", "null"],
          "title": "Coin Image URL",
          "description": "URL of the coin image displayed in the HUD",
          "format": "uri",
          "default": "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COIN"
        },
        "coin_count_url": {
          "type": ["string", "null"],
          "title": "Coin Count Image URL",
          "description": "URL of the icon displayed next to the coin counter",
          "format": "uri",
          "default": "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COUNT"
        }
      }
    }
  }
}',
    '{
  "ui:order": ["meta", "branding", "game_config", "game", "audio", "texts", "rewards", "personalization"],
  "meta": {
    "ui:title": "Meta",
    "ui:description": "Brand identification metadata",
    "brand_id": {
      "ui:widget": "textInput",
      "ui:placeholder": "e.g. default",
      "ui:help": "Unique brand identifier for this game configuration"
    }
  },
  "branding": {
    "ui:title": "Branding",
    "ui:description": "Visual branding assets and background shader settings",
    "images": {
      "ui:title": "Brand Images",
      "main_image_url": {
        "ui:widget": "assetUpload",
        "ui:options": { "assetType": "image" },
        "ui:title": "Main Image",
        "ui:placeholder": "https://...",
        "ui:help": "Recommended size: 400x200px"
      },
      "main_image_offset_y": {
        "ui:widget": "decimalInput",
        "ui:title": "Main Image Offset Y",
        "ui:placeholder": "0"
      },
      "logo_watermark_url": {
        "ui:widget": "assetUpload",
        "ui:options": { "assetType": "image" },
        "ui:title": "Logo Watermark",
        "ui:placeholder": "https://...",
        "ui:help": "Recommended size: 150x50px"
      },
      "logo_watermark_offset_y": {
        "ui:widget": "decimalInput",
        "ui:title": "Logo Watermark Offset Y",
        "ui:placeholder": "0"
      }
    },
    "shader_background_config": {
      "ui:title": "Shader Background",
      "PrimaryColorHex": {
        "ui:widget": "colorPicker",
        "ui:title": "Primary Color"
      },
      "SecondaryColorHex": {
        "ui:widget": "colorPicker",
        "ui:title": "Secondary Color"
      },
      "ParticleColorHex": {
        "ui:widget": "colorPicker",
        "ui:title": "Particle Color"
      },
      "Speed": {
        "ui:widget": "decimalInput",
        "ui:title": "Shader Speed",
        "ui:placeholder": "0.5"
      },
      "Difficulty": {
        "ui:widget": "decimalInput",
        "ui:title": "Shader Difficulty",
        "ui:placeholder": "1.0"
      },
      "UseShader": {
        "ui:widget": "checkbox",
        "ui:title": "Use Procedural Shader"
      },
      "Front": {
        "ui:title": "Front Layer",
        "Enabled": {
          "ui:widget": "checkbox",
          "ui:title": "Enable Front Layer"
        }
      },
      "Back": {
        "ui:title": "Back Layer",
        "SpriteUrl": {
          "ui:widget": "assetUpload",
          "ui:options": { "assetType": "image" },
          "ui:title": "Sprite Texture",
          "ui:placeholder": "https://..."
        },
        "Enabled": {
          "ui:widget": "checkbox",
          "ui:title": "Enable Back Layer"
        }
      }
    }
  },
  "game_config": {
    "ui:title": "Game Config",
    "ui:description": "High-level gameplay settings",
    "difficulty": {
      "ui:widget": "radio",
      "ui:title": "Difficulty"
    },
    "game_duration_sec": {
      "ui:widget": "numberInput",
      "ui:title": "Game Duration (seconds)",
      "ui:placeholder": "60"
    },
    "score_per_hit": {
      "ui:widget": "numberInput",
      "ui:title": "Score per Hit",
      "ui:placeholder": "10"
    }
  },
  "game": {
    "ui:title": "Game",
    "ui:description": "Physics, layout, textures, levels and power-ups",
    "ball_speed": {
      "ui:widget": "decimalInput",
      "ui:title": "Ball Speed",
      "ui:placeholder": "500.0"
    },
    "paddle_speed": {
      "ui:widget": "decimalInput",
      "ui:title": "Paddle Speed",
      "ui:placeholder": "600.0"
    },
    "brick_rows": {
      "ui:widget": "numberInput",
      "ui:title": "Brick Rows",
      "ui:placeholder": "5"
    },
    "brick_columns": {
      "ui:widget": "numberInput",
      "ui:title": "Brick Columns",
      "ui:placeholder": "9"
    },
    "url_ball_texture": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "image" },
      "ui:title": "Ball Texture",
      "ui:placeholder": "https://...",
      "ui:help": "Recommended size: 64x64px"
    },
    "url_paddle_texture": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "image" },
      "ui:title": "Paddle Texture",
      "ui:placeholder": "https://...",
      "ui:help": "Recommended size: 256x64px"
    },
    "levels": {
      "ui:title": "Levels",
      "ui:description": "Configure each level individually",
      "items": {
        "level_difficulty": {
          "ui:widget": "radio",
          "ui:title": "Level Difficulty"
        },
        "ball_speed_override": {
          "ui:widget": "decimalInput",
          "ui:title": "Ball Speed Override",
          "ui:placeholder": "450.0"
        },
        "timer_sec": {
          "ui:widget": "numberInput",
          "ui:title": "Timer (seconds)",
          "ui:placeholder": "40"
        },
        "brick_rows": {
          "ui:widget": "numberInput",
          "ui:title": "Brick Rows",
          "ui:placeholder": "3"
        },
        "brick_columns": {
          "ui:widget": "numberInput",
          "ui:title": "Brick Columns",
          "ui:placeholder": "4"
        },
        "url_image": {
          "ui:widget": "assetUpload",
          "ui:options": { "assetType": "image" },
          "ui:title": "Brick Texture",
          "ui:placeholder": "https://...",
          "ui:help": "Recommended size: 256x256px"
        },
        "power_ups": {
          "ui:title": "Level Power-Ups",
          "items": {
            "type": {
              "ui:widget": "radio",
              "ui:title": "Type"
            },
            "drop_chance": {
              "ui:widget": "decimalInput",
              "ui:title": "Drop Chance",
              "ui:placeholder": "0.1",
              "ui:help": "Value between 0.0 and 1.0"
            },
            "duration": {
              "ui:widget": "decimalInput",
              "ui:title": "Duration (seconds)",
              "ui:placeholder": "0",
              "ui:help": "Set to 0 for instant or permanent effects"
            },
            "color_hex": {
              "ui:widget": "colorPicker",
              "ui:title": "Color"
            },
            "url_icon": {
              "ui:widget": "assetUpload",
              "ui:options": { "assetType": "image" },
              "ui:title": "Icon",
              "ui:placeholder": "https://...",
              "ui:help": "Recommended size: 64x64px"
            }
          }
        }
      }
    },
    "power_ups": {
      "ui:title": "Global Power-Ups",
      "ui:description": "Default power-ups applied when no level override is set",
      "items": {
        "type": {
          "ui:widget": "radio",
          "ui:title": "Type"
        },
        "drop_chance": {
          "ui:widget": "decimalInput",
          "ui:title": "Drop Chance",
          "ui:placeholder": "0.1",
          "ui:help": "Value between 0.0 and 1.0"
        },
        "duration": {
          "ui:widget": "decimalInput",
          "ui:title": "Duration (seconds)",
          "ui:placeholder": "0",
          "ui:help": "Set to 0 for instant or permanent effects"
        },
        "color_hex": {
          "ui:widget": "colorPicker",
          "ui:title": "Color"
        },
        "url_icon": {
          "ui:widget": "assetUpload",
          "ui:options": { "assetType": "image" },
          "ui:title": "Icon",
          "ui:placeholder": "https://...",
          "ui:help": "Recommended size: 64x64px"
        }
      }
    }
  },
  "audio": {
    "ui:title": "Audio",
    "ui:description": "Sound effects and music assets",
    "hit_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:title": "Hit Sound",
      "ui:placeholder": "https://"
    },
    "brick_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:title": "Brick Sound",
      "ui:placeholder": "https://"
    },
    "win_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:title": "Win Sound",
      "ui:placeholder": "https://"
    },
    "lose_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:title": "Lose Sound",
      "ui:placeholder": "https://"
    },
    "music_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:title": "Background Music",
      "ui:placeholder": "https://"
    },
    "laser_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:title": "Laser Sound",
      "ui:placeholder": "https://"
    },
    "powerup_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:title": "Power-Up Sound",
      "ui:placeholder": "https://"
    }
  },
  "texts": {
    "ui:title": "Texts",
    "ui:description": "Customizable game UI text strings",
    "victory_title": {
      "ui:widget": "textInput",
      "ui:title": "Victory Title",
      "ui:placeholder": "¡VICTORIA!"
    },
    "victory_phrase": {
      "ui:widget": "textInput",
      "ui:title": "Victory Phrase",
      "ui:placeholder": "¡Felicidades! Has completado el nivel.",
      "ui:help": "Use for line breaks"
    },
    "defeat_title": {
      "ui:widget": "textInput",
      "ui:title": "Defeat Title",
      "ui:placeholder": "DERROTA"
    },
    "defeat_phrase": {
      "ui:widget": "textInput",
      "ui:title": "Defeat Phrase",
      "ui:placeholder": "¡No te rindas! Inténtalo de nuevo.",
      "ui:help": "Use for line breaks"
    },
    "floating_words": {
      "ui:title": "Floating Words",
      "ui:description": "Words shown as floating text on hit combos",
      "items": {
        "ui:widget": "textInput",
        "ui:placeholder": "e.g. ¡GENIAL!"
      }
    },
    "floating_color_hex": {
      "ui:widget": "colorPicker",
      "ui:title": "Floating Text Color"
    },
    "floating_font_size": {
      "ui:widget": "numberInput",
      "ui:title": "Floating Font Size",
      "ui:placeholder": "42"
    },
    "show_particles_with_words": {
      "ui:widget": "checkbox",
      "ui:title": "Show Particles with Words"
    }
  },
  "rewards": {
    "ui:title": "Rewards",
    "ui:description": "Coin reward settings",
    "coins_per_action": {
      "ui:widget": "numberInput",
      "ui:title": "Coins per Action",
      "ui:placeholder": "10"
    },
    "coins_on_completion": {
      "ui:widget": "numberInput",
      "ui:title": "Coins on Completion",
      "ui:placeholder": "100"
    }
  },
  "personalization": {
    "ui:title": "Personalization",
    "ui:description": "Custom HUD asset images",
    "coin_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "image" },
      "ui:title": "Coin Image",
      "ui:placeholder": "https://...",
      "ui:help": "Recommended size: 128x128px"
    },
    "coin_count_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "image" },
      "ui:title": "Coin Count Icon",
      "ui:placeholder": "https://...",
      "ui:help": "Recommended size: 128x128px"
    }
  }
}',
    true,
    true,
    NOW(),
    'system'
WHERE NOT EXISTS (
    SELECT 1 FROM game_config_definitions 
    WHERE game_id = 2 AND version = 1
);