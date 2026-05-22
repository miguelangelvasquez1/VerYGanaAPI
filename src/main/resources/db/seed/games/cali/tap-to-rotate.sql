INSERT INTO games (id, title, description, delivery_type, url, front_page_url, active, created_at, updated_at)
SELECT
		 9,
    'Tap To Rotate',
    'Salta y esquiva trampas en un runner de plataformas. Sobrevive el mayor tiempo posible sin caer ni chocar.',
    'QUERY',
    'Tap%20Runner',
    'https://games.verygana.com/game_icons/cali/tap_to_rotate.png',
     true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM games WHERE id = 9);

 
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
    9,
    1,
    '{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "title": "Runner Game Configuration",
  "description": "Full configuration for an endless runner game",
  "required": ["meta", "game_config", "branding", "audio", "texts", "rewards", "personalization"],
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
      "description": "Core physics and gameplay parameters",
      "required": ["scroll_speed", "max_scroll_speed", "acceleration", "jump_force", "gravity_scale", "game_duration", "use_countdown", "max_lives"],
      "properties": {
        "scroll_speed": {
          "type": "number",
          "title": "Scroll Speed",
          "description": "Initial horizontal scrolling speed of the game world",
          "default": 4.0,
          "minimum": 0.1,
          "maximum": 50.0,
          "multipleOf": 0.1
        },
        "max_scroll_speed": {
          "type": "number",
          "title": "Max Scroll Speed",
          "description": "Maximum horizontal scrolling speed the game can reach",
          "default": 10.0,
          "minimum": 0.1,
          "maximum": 100.0,
          "multipleOf": 0.1
        },
        "acceleration": {
          "type": "number",
          "title": "Acceleration",
          "description": "Rate at which scroll speed increases over time",
          "default": 0.05,
          "minimum": 0.0,
          "maximum": 5.0,
          "multipleOf": 0.01
        },
        "jump_force": {
          "type": "number",
          "title": "Jump Force",
          "description": "Upward force applied to the player on jump",
          "default": 12.0,
          "minimum": 0.1,
          "maximum": 100.0,
          "multipleOf": 0.1
        },
        "gravity_scale": {
          "type": "number",
          "title": "Gravity Scale",
          "description": "Gravity multiplier applied to the player",
          "default": 3.0,
          "minimum": 0.1,
          "maximum": 20.0,
          "multipleOf": 0.1
        },
        "game_duration": {
          "type": "integer",
          "title": "Game Duration (seconds)",
          "description": "Total time allowed for a game session in seconds",
          "default": 60,
          "minimum": 10,
          "maximum": 600
        },
        "use_countdown": {
          "type": "boolean",
          "title": "Use Countdown",
          "description": "Whether the timer counts down instead of up",
          "default": true
        },
        "max_lives": {
          "type": "integer",
          "title": "Max Lives",
          "description": "Number of lives the player starts with",
          "default": 3,
          "minimum": 1,
          "maximum": 10
        }
      }
    },
    "branding": {
      "type": "object",
      "title": "Branding",
      "description": "Visual identity assets, game visuals, and background configuration",
      "required": ["images", "visuals", "background_config"],
      "properties": {
        "images": {
          "type": "object",
          "title": "Brand Images",
          "description": "Logo and watermark assets",
          "required": ["main_logo_url", "main_logo_offset_y", "logo_watermark_url", "logo_watermark_offset_y"],
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
              "default": 50.0,
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
              "default": -50.0,
              "minimum": -500.0,
              "maximum": 500.0,
              "multipleOf": 0.1
            }
          }
        },
        "visuals": {
          "type": "object",
          "title": "Game Visuals",
          "description": "Sprites, textures, colors and scale values for in-game objects",
          "required": [
            "player_url", "ground_url", "ground_trap_url", "coin_url", "air_trap_url",
            "bg_image_url", "death_wall_url", "coin_scale", "air_trap_scale",
            "ground_trap_scale", "ground_texture_scale", "bg_texture_scale",
            "bg_solid_color", "ground_color", "dw_primary_color", "dw_secondary_color",
            "dw_bg_color", "dw_scroll_x", "dw_scroll_y", "dw_desphase_x", "dw_desphase_y"
          ],
          "properties": {
            "player_url": {
              "type": ["string", "null"],
              "title": "Player Sprite URL",
              "description": "URL of the player character sprite",
              "format": "uri",
              "default": ""
            },
            "ground_url": {
              "type": ["string", "null"],
              "title": "Ground Texture URL",
              "description": "URL of the ground tile texture",
              "format": "uri",
              "default": ""
            },
            "ground_trap_url": {
              "type": ["string", "null"],
              "title": "Ground Trap Sprite URL",
              "description": "URL of the ground-level trap/obstacle sprite",
              "format": "uri",
              "default": ""
            },
            "coin_url": {
              "type": ["string", "null"],
              "title": "Coin Sprite URL",
              "description": "URL of the collectible coin sprite",
              "format": "uri",
              "default": ""
            },
            "air_trap_url": {
              "type": ["string", "null"],
              "title": "Air Trap Sprite URL",
              "description": "URL of the airborne trap/obstacle sprite",
              "format": "uri",
              "default": ""
            },
            "bg_image_url": {
              "type": ["string", "null"],
              "title": "Background Image URL",
              "description": "URL of the scrolling background image",
              "format": "uri",
              "default": ""
            },
            "death_wall_url": {
              "type": ["string", "null"],
              "title": "Death Wall Sprite URL",
              "description": "URL of the death wall sprite that chases the player",
              "format": "uri",
              "default": ""
            },
            "coin_scale": {
              "type": "number",
              "title": "Coin Scale",
              "description": "Size multiplier for the coin sprite",
              "default": 1.2,
              "minimum": 0.1,
              "maximum": 10.0,
              "multipleOf": 0.01
            },
            "air_trap_scale": {
              "type": "number",
              "title": "Air Trap Scale",
              "description": "Size multiplier for the air trap sprite",
              "default": 1.0,
              "minimum": 0.1,
              "maximum": 10.0,
              "multipleOf": 0.01
            },
            "ground_trap_scale": {
              "type": "number",
              "title": "Ground Trap Scale",
              "description": "Size multiplier for the ground trap sprite",
              "default": 0.9,
              "minimum": 0.1,
              "maximum": 10.0,
              "multipleOf": 0.01
            },
            "ground_texture_scale": {
              "type": "number",
              "title": "Ground Texture Scale",
              "description": "Tiling scale for the ground texture",
              "default": 1.0,
              "minimum": 0.1,
              "maximum": 20.0,
              "multipleOf": 0.01
            },
            "bg_texture_scale": {
              "type": "number",
              "title": "Background Texture Scale",
              "description": "Tiling scale for the background texture",
              "default": 1.0,
              "minimum": 0.1,
              "maximum": 20.0,
              "multipleOf": 0.01
            },
            "bg_solid_color": {
              "type": "string",
              "title": "Background Solid Color",
              "description": "Solid fallback color for the background",
              "default": "#1a0b2e",
              "minLength": 4,
              "maxLength": 9
            },
            "ground_color": {
              "type": "string",
              "title": "Ground Color",
              "description": "Tint color applied to the ground",
              "default": "#ffffff",
              "minLength": 4,
              "maxLength": 9
            },
            "dw_primary_color": {
              "type": "string",
              "title": "Death Wall Primary Color",
              "description": "Primary color of the death wall visual effect",
              "default": "#ff0000",
              "minLength": 4,
              "maxLength": 9
            },
            "dw_secondary_color": {
              "type": "string",
              "title": "Death Wall Secondary Color",
              "description": "Secondary color of the death wall visual effect",
              "default": "#550000",
              "minLength": 4,
              "maxLength": 9
            },
            "dw_bg_color": {
              "type": "string",
              "title": "Death Wall Background Color",
              "description": "Background color behind the death wall effect",
              "default": "#220000",
              "minLength": 4,
              "maxLength": 9
            },
            "dw_scroll_x": {
              "type": "number",
              "title": "Death Wall Scroll X",
              "description": "Horizontal scroll speed of the death wall texture",
              "default": 0.5,
              "minimum": -10.0,
              "maximum": 10.0,
              "multipleOf": 0.01
            },
            "dw_scroll_y": {
              "type": "number",
              "title": "Death Wall Scroll Y",
              "description": "Vertical scroll speed of the death wall texture",
              "default": 0.5,
              "minimum": -10.0,
              "maximum": 10.0,
              "multipleOf": 0.01
            },
            "dw_desphase_x": {
              "type": "number",
              "title": "Death Wall Dephase X",
              "description": "Horizontal phase offset for the death wall animation",
              "default": 0.1,
              "minimum": -10.0,
              "maximum": 10.0,
              "multipleOf": 0.01
            },
            "dw_desphase_y": {
              "type": "number",
              "title": "Death Wall Dephase Y",
              "description": "Vertical phase offset for the death wall animation",
              "default": 0.1,
              "minimum": -10.0,
              "maximum": 10.0,
              "multipleOf": 0.01
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
                  "description": "Tint color in hexadecimal format, supports alpha (e.g. #FFFFFF80)",
                  "default": "#FFFFFF80",
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
                  "default": 0.1,
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
                  "default": "#0A0A1E",
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
                  "default": 0.0,
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
      "required": ["music_url", "jump_url", "land_url", "coin_url", "death_url", "victory_url", "lose_url"],
      "properties": {
        "music_url": {
          "type": ["string", "null"],
          "title": "Background Music URL",
          "description": "URL of the background music track",
          "format": "uri",
          "default": ""
        },
        "jump_url": {
          "type": ["string", "null"],
          "title": "Jump Sound URL",
          "description": "URL of the sound played when the player jumps",
          "format": "uri",
          "default": ""
        },
        "land_url": {
          "type": ["string", "null"],
          "title": "Land Sound URL",
          "description": "URL of the sound played when the player lands",
          "format": "uri",
          "default": ""
        },
        "coin_url": {
          "type": ["string", "null"],
          "title": "Coin Collect Sound URL",
          "description": "URL of the sound played when a coin is collected",
          "format": "uri",
          "default": ""
        },
        "death_url": {
          "type": ["string", "null"],
          "title": "Death Sound URL",
          "description": "URL of the sound played when the player dies",
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
      "required": ["victory_title", "victory_phrase", "defeat_title", "defeat_phrase", "label_score", "label_time", "label_record"],
      "properties": {
        "victory_title": {
          "type": "string",
          "title": "Victory Title",
          "description": "Headline shown on the victory screen",
          "default": "¡MISIÓN CUMPLIDA!",
          "minLength": 1,
          "maxLength": 64
        },
        "victory_phrase": {
          "type": "string",
          "title": "Victory Phrase",
          "description": "Subtitle shown on the victory screen",
          "default": "Excelente trabajo, sobreviviste.",
          "minLength": 1,
          "maxLength": 256
        },
        "defeat_title": {
          "type": "string",
          "title": "Defeat Title",
          "description": "Headline shown on the game over screen",
          "default": "¡CUIDADO AHI!",
          "minLength": 1,
          "maxLength": 64
        },
        "defeat_phrase": {
          "type": "string",
          "title": "Defeat Phrase",
          "description": "Subtitle shown on the game over screen",
          "default": "Mejor suerte la próxima vez.",
          "minLength": 1,
          "maxLength": 256
        },
        "label_score": {
          "type": "string",
          "title": "Score Label",
          "description": "HUD label for the score/keys counter",
          "default": "LLAVES",
          "minLength": 1,
          "maxLength": 32
        },
        "label_time": {
          "type": "string",
          "title": "Time Label",
          "description": "HUD label for the timer",
          "default": "TIEMPO",
          "minLength": 1,
          "maxLength": 32
        },
        "label_record": {
          "type": "string",
          "title": "Record Label",
          "description": "HUD label for the personal best/record display",
          "default": "RÉCORD",
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
    }
  }
}',
    '{
  "ui:order": ["meta", "game_config", "branding", "audio", "texts", "rewards", "personalization"],
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
    "ui:description": "Physics parameters and session rules",
    "ui:order": ["scroll_speed", "max_scroll_speed", "acceleration", "jump_force", "gravity_scale", "game_duration", "use_countdown", "max_lives"],
    "scroll_speed": {
      "ui:widget": "decimalInput",
      "ui:placeholder": "4.0",
      "ui:help": "Initial world scroll speed"
    },
    "max_scroll_speed": {
      "ui:widget": "decimalInput",
      "ui:placeholder": "10.0",
      "ui:help": "Speed cap the game will never exceed"
    },
    "acceleration": {
      "ui:widget": "decimalInput",
      "ui:placeholder": "0.05",
      "ui:help": "How fast scroll speed ramps up over time"
    },
    "jump_force": {
      "ui:widget": "decimalInput",
      "ui:placeholder": "12.0",
      "ui:help": "Upward impulse applied to the player on jump"
    },
    "gravity_scale": {
      "ui:widget": "decimalInput",
      "ui:placeholder": "3.0",
      "ui:help": "Gravity multiplier — higher values make jumps snappier"
    },
    "game_duration": {
      "ui:widget": "numberInput",
      "ui:placeholder": "60",
      "ui:help": "Session length in seconds"
    },
    "use_countdown": {
      "ui:widget": "checkbox",
      "ui:help": "Tick the timer down instead of up"
    },
    "max_lives": {
      "ui:widget": "numberInput",
      "ui:placeholder": "3",
      "ui:help": "Lives available at session start"
    }
  },
  "branding": {
    "ui:title": "Branding",
    "ui:description": "Visual identity assets, game sprites, colors and background layers",
    "images": {
      "ui:title": "Brand Images",
      "ui:order": ["main_logo_url", "main_logo_offset_y", "logo_watermark_url", "logo_watermark_offset_y"],
      "main_logo_url": {
        "ui:widget": "assetUpload",
        "ui:options": { "assetType": "image" },
        "ui:help": "Main logo displayed on the game screen"
      },
      "main_logo_offset_y": {
        "ui:widget": "decimalInput",
        "ui:placeholder": "50.0",
        "ui:help": "Vertical position offset for the main logo"
      },
      "logo_watermark_url": {
        "ui:widget": "assetUpload",
        "ui:options": { "assetType": "image" },
        "ui:help": "Small watermark logo overlaid on the game"
      },
      "logo_watermark_offset_y": {
        "ui:widget": "decimalInput",
        "ui:placeholder": "-50.0",
        "ui:help": "Vertical position offset for the watermark logo"
      }
    },
    "visuals": {
      "ui:title": "Game Visuals",
      "ui:description": "Sprites, textures, scale and color settings for in-game objects",
      "ui:order": [
        "player_url", "ground_url", "ground_trap_url", "coin_url", "air_trap_url",
        "bg_image_url", "death_wall_url",
        "coin_scale", "air_trap_scale", "ground_trap_scale", "ground_texture_scale", "bg_texture_scale",
        "bg_solid_color", "ground_color",
        "dw_primary_color", "dw_secondary_color", "dw_bg_color",
        "dw_scroll_x", "dw_scroll_y", "dw_desphase_x", "dw_desphase_y"
      ],
      "player_url": {
        "ui:widget": "assetUpload",
        "ui:options": { "assetType": "image" },
        "ui:help": "Player character sprite"
      },
      "ground_url": {
        "ui:widget": "assetUpload",
        "ui:options": { "assetType": "image" },
        "ui:help": "Ground tile texture"
      },
      "ground_trap_url": {
        "ui:widget": "assetUpload",
        "ui:options": { "assetType": "image" },
        "ui:help": "Obstacle placed on the ground"
      },
      "coin_url": {
        "ui:widget": "assetUpload",
        "ui:options": { "assetType": "image" },
        "ui:help": "Collectible coin sprite"
      },
      "air_trap_url": {
        "ui:widget": "assetUpload",
        "ui:options": { "assetType": "image" },
        "ui:help": "Airborne obstacle/mine sprite"
      },
      "bg_image_url": {
        "ui:widget": "assetUpload",
        "ui:options": { "assetType": "image" },
        "ui:help": "Scrolling background image"
      },
      "death_wall_url": {
        "ui:widget": "assetUpload",
        "ui:options": { "assetType": "image" },
        "ui:help": "Death wall sprite that pursues the player"
      },
      "coin_scale": {
        "ui:widget": "decimalInput",
        "ui:placeholder": "1.2",
        "ui:help": "Size multiplier for the coin sprite"
      },
      "air_trap_scale": {
        "ui:widget": "decimalInput",
        "ui:placeholder": "1.0",
        "ui:help": "Size multiplier for the air trap sprite"
      },
      "ground_trap_scale": {
        "ui:widget": "decimalInput",
        "ui:placeholder": "0.9",
        "ui:help": "Size multiplier for the ground trap sprite"
      },
      "ground_texture_scale": {
        "ui:widget": "decimalInput",
        "ui:placeholder": "1.0",
        "ui:help": "Tiling scale for the ground texture"
      },
      "bg_texture_scale": {
        "ui:widget": "decimalInput",
        "ui:placeholder": "1.0",
        "ui:help": "Tiling scale for the background texture"
      },
      "bg_solid_color": {
        "ui:widget": "colorPicker",
        "ui:help": "Solid fallback color shown behind the background image"
      },
      "ground_color": {
        "ui:widget": "colorPicker",
        "ui:help": "Tint color applied to the ground tiles"
      },
      "dw_primary_color": {
        "ui:widget": "colorPicker",
        "ui:help": "Primary color of the death wall effect"
      },
      "dw_secondary_color": {
        "ui:widget": "colorPicker",
        "ui:help": "Secondary color of the death wall effect"
      },
      "dw_bg_color": {
        "ui:widget": "colorPicker",
        "ui:help": "Background color behind the death wall effect"
      },
      "dw_scroll_x": {
        "ui:widget": "decimalInput",
        "ui:placeholder": "0.5",
        "ui:help": "Horizontal scroll speed of the death wall texture"
      },
      "dw_scroll_y": {
        "ui:widget": "decimalInput",
        "ui:placeholder": "0.5",
        "ui:help": "Vertical scroll speed of the death wall texture"
      },
      "dw_desphase_x": {
        "ui:widget": "decimalInput",
        "ui:placeholder": "0.1",
        "ui:help": "Horizontal phase offset for the death wall animation"
      },
      "dw_desphase_y": {
        "ui:widget": "decimalInput",
        "ui:placeholder": "0.1",
        "ui:help": "Vertical phase offset for the death wall animation"
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
          "ui:help": "Sprite for the foreground background layer"
        },
        "ColorHex": {
          "ui:widget": "colorPicker",
          "ui:help": "Tint color (supports 8-digit hex with alpha)"
        },
        "Enabled": {
          "ui:widget": "checkbox"
        },
        "Speed": {
          "ui:widget": "decimalInput",
          "ui:placeholder": "0.1"
        },
        "Rotation": {
          "ui:widget": "decimalInput",
          "ui:placeholder": "0.0"
        },
        "LayoutMode": {
          "ui:widget": "radio"
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
          "ui:help": "Sprite for the main background layer (leave empty for solid color)"
        },
        "ColorHex": {
          "ui:widget": "colorPicker",
          "ui:help": "Tint color applied to the back layer"
        },
        "Enabled": {
          "ui:widget": "checkbox"
        },
        "Speed": {
          "ui:widget": "decimalInput",
          "ui:placeholder": "0.0"
        },
        "Rotation": {
          "ui:widget": "decimalInput",
          "ui:placeholder": "0.0"
        },
        "LayoutMode": {
          "ui:widget": "radio"
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
    "ui:order": ["music_url", "jump_url", "land_url", "coin_url", "death_url", "victory_url", "lose_url"],
    "music_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:help": "Looping background music during gameplay"
    },
    "jump_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:help": "Sound played when the player jumps"
    },
    "land_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:help": "Sound played when the player lands on the ground"
    },
    "coin_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:help": "Sound played when a coin is collected"
    },
    "death_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:help": "Sound played when the player dies"
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
    "ui:order": ["victory_title", "victory_phrase", "defeat_title", "defeat_phrase", "label_score", "label_time", "label_record"],
    "victory_title": {
      "ui:widget": "textInput",
      "ui:placeholder": "¡MISIÓN CUMPLIDA!",
      "ui:help": "Large headline on the victory screen"
    },
    "victory_phrase": {
      "ui:widget": "textInput",
      "ui:placeholder": "Excelente trabajo, sobreviviste.",
      "ui:help": "Subtitle message on the victory screen"
    },
    "defeat_title": {
      "ui:widget": "textInput",
      "ui:placeholder": "¡CUIDADO AHI!",
      "ui:help": "Large headline on the game over screen"
    },
    "defeat_phrase": {
      "ui:widget": "textInput",
      "ui:placeholder": "Mejor suerte la próxima vez.",
      "ui:help": "Subtitle message on the game over screen"
    },
    "label_score": {
      "ui:widget": "textInput",
      "ui:placeholder": "LLAVES",
      "ui:help": "HUD label for the score counter"
    },
    "label_time": {
      "ui:widget": "textInput",
      "ui:placeholder": "TIEMPO",
      "ui:help": "HUD label for the timer"
    },
    "label_record": {
      "ui:widget": "textInput",
      "ui:placeholder": "RÉCORD",
      "ui:help": "HUD label for the personal best record"
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
  }
}',
    true,
    true,
    NOW(),
    'system'
WHERE NOT EXISTS (
    SELECT 1 FROM game_config_definitions 
    WHERE game_id = 9 AND version = 1
);