INSERT INTO games (id, title, description, delivery_type, url, front_page_url, active, created_at, updated_at)
SELECT 
		 1,
    'Avoid The Bomb',
    'Atrapa los objetos buenos y evita las bombas. Recoge power-ups para sobrevivir más tiempo.',
    'QUERY',
    'Avoid%20The%20Bomb',
    'https://games.verygana.com/game_icons/cali/avoid_the_bomb.png',
	true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM games WHERE id = 1);


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
    1,
    1,
    '{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "title": "Game Configuration",
  "description": "Full configuration object for a branded game instance",
  "required": ["meta", "branding", "game_config", "game", "audio", "texts", "rewards", "personalization", "particleEffects"],
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
          "required": ["main_image_url", "logo_watermark_url"],
          "properties": {
            "main_image_url": {
              "type": ["string", "null"],
              "title": "Main Image URL",
              "description": "URL of the main brand image displayed in the game",
              "format": "uri",
              "default": "https://placehold.co/400x200/FF5733/FFFFFF.png?text=LOGO"
            },
            "logo_watermark_url": {
              "type": ["string", "null"],
              "title": "Logo Watermark URL",
              "description": "URL of the watermark logo overlay",
              "format": "uri",
              "default": "https://placehold.co/150x50/333333/FFFFFF.png?text=WATERMARK"
            }
          }
        },
        "shader_background_config": {
          "type": "object",
          "title": "Shader Background Config",
          "description": "Configuration for background and foreground shader layers",
          "required": ["Back", "Front"],
          "properties": {
            "Back": {
              "type": "object",
              "title": "Back Layer",
              "description": "Configuration for the back (furthest) background shader layer",
              "required": ["Enabled", "SpriteUrl", "ColorHex", "ScrollSpeedX", "ScrollSpeedY", "TilingX", "TilingY", "WaveSpeed", "WaveFrequency", "WaveAmplitude"],
              "properties": {
                "Enabled": {
                  "type": "boolean",
                  "title": "Enabled",
                  "description": "Whether the back layer is active",
                  "default": true
                },
                "SpriteUrl": {
                  "type": ["string", "null"],
                  "title": "Sprite URL",
                  "description": "URL of the sprite texture used for the back layer",
                  "format": "uri",
                  "default": "https://placehold.co/1024x512/000033/FFFFFF.png?text=BG"
                },
                "ColorHex": {
                  "type": "string",
                  "title": "Color (Hex)",
                  "description": "Tint color in RGBA hex format (e.g. #FFFFFFFF)",
                  "default": "#FFFFFFFF",
                  "minLength": 7,
                  "maxLength": 9
                },
                "ScrollSpeedX": {
                  "type": "number",
                  "title": "Scroll Speed X",
                  "description": "Horizontal scroll speed of the background layer",
                  "default": 0.05,
                  "minimum": -10.0,
                  "maximum": 10.0
                },
                "ScrollSpeedY": {
                  "type": "number",
                  "title": "Scroll Speed Y",
                  "description": "Vertical scroll speed of the background layer",
                  "default": 0.0,
                  "minimum": -10.0,
                  "maximum": 10.0
                },
                "TilingX": {
                  "type": "number",
                  "title": "Tiling X",
                  "description": "Horizontal tiling factor of the background sprite",
                  "default": 1.0,
                  "minimum": 0.1,
                  "maximum": 20.0
                },
                "TilingY": {
                  "type": "number",
                  "title": "Tiling Y",
                  "description": "Vertical tiling factor of the background sprite",
                  "default": 1.0,
                  "minimum": 0.1,
                  "maximum": 20.0
                },
                "WaveSpeed": {
                  "type": "number",
                  "title": "Wave Speed",
                  "description": "Speed of the wave distortion animation",
                  "default": 0.0,
                  "minimum": 0.0,
                  "maximum": 20.0
                },
                "WaveFrequency": {
                  "type": "number",
                  "title": "Wave Frequency",
                  "description": "Frequency of the wave distortion effect",
                  "default": 0.0,
                  "minimum": 0.0,
                  "maximum": 50.0
                },
                "WaveAmplitude": {
                  "type": "number",
                  "title": "Wave Amplitude",
                  "description": "Amplitude of the wave distortion effect",
                  "default": 0.0,
                  "minimum": 0.0,
                  "maximum": 10.0
                }
              }
            },
            "Front": {
              "type": "object",
              "title": "Front Layer",
              "description": "Configuration for the front (closest) background shader layer",
              "required": ["Enabled", "SpriteUrl", "ColorHex", "ScrollSpeedX", "ScrollSpeedY", "TilingX", "TilingY"],
              "properties": {
                "Enabled": {
                  "type": "boolean",
                  "title": "Enabled",
                  "description": "Whether the front layer is active",
                  "default": false
                },
                "SpriteUrl": {
                  "type": ["string", "null"],
                  "title": "Sprite URL",
                  "description": "URL of the sprite texture used for the front layer",
                  "format": "uri",
                  "default": ""
                },
                "ColorHex": {
                  "type": "string",
                  "title": "Color (Hex)",
                  "description": "Tint color in RGBA hex format (e.g. #FFFFFF00)",
                  "default": "#FFFFFF00",
                  "minLength": 7,
                  "maxLength": 9
                },
                "ScrollSpeedX": {
                  "type": "number",
                  "title": "Scroll Speed X",
                  "description": "Horizontal scroll speed of the front layer",
                  "default": 0.1,
                  "minimum": -10.0,
                  "maximum": 10.0
                },
                "ScrollSpeedY": {
                  "type": "number",
                  "title": "Scroll Speed Y",
                  "description": "Vertical scroll speed of the front layer",
                  "default": 0.0,
                  "minimum": -10.0,
                  "maximum": 10.0
                },
                "TilingX": {
                  "type": "number",
                  "title": "Tiling X",
                  "description": "Horizontal tiling factor of the front sprite",
                  "default": 1.0,
                  "minimum": 0.1,
                  "maximum": 20.0
                },
                "TilingY": {
                  "type": "number",
                  "title": "Tiling Y",
                  "description": "Vertical tiling factor of the front sprite",
                  "default": 1.0,
                  "minimum": 0.1,
                  "maximum": 20.0
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
      "description": "Core gameplay parameters controlling difficulty, physics, and rewards",
      "required": [
        "duration", "lives", "spawn_interval", "min_spawn_interval",
        "difficulty_decrease_step", "min_up_force", "max_up_force",
        "side_force", "gravity_multiplier", "actions_to_complete",
        "freeze_duration", "freeze_overlay_color_hex",
        "good_model_scale", "bad_model_scale", "bonus_model_scale"
      ],
      "properties": {
        "duration": {
          "type": "integer",
          "title": "Duration (seconds)",
          "description": "Total duration of the game in seconds",
          "default": 60,
          "minimum": 10,
          "maximum": 600
        },
        "lives": {
          "type": "integer",
          "title": "Lives",
          "description": "Number of lives the player starts with",
          "default": 3,
          "minimum": 1,
          "maximum": 10
        },
        "spawn_interval": {
          "type": "number",
          "title": "Spawn Interval",
          "description": "Initial interval in seconds between object spawns",
          "default": 1.0,
          "minimum": 0.1,
          "maximum": 10.0
        },
        "min_spawn_interval": {
          "type": "number",
          "title": "Minimum Spawn Interval",
          "description": "Minimum allowed spawn interval as difficulty increases",
          "default": 0.4,
          "minimum": 0.1,
          "maximum": 5.0
        },
        "difficulty_decrease_step": {
          "type": "number",
          "title": "Difficulty Decrease Step",
          "description": "Amount by which spawn interval decreases per step to ramp up difficulty",
          "default": 0.02,
          "minimum": 0.001,
          "maximum": 1.0
        },
        "min_up_force": {
          "type": "number",
          "title": "Min Up Force",
          "description": "Minimum upward launch force applied to spawned objects",
          "default": 13.0,
          "minimum": 1.0,
          "maximum": 50.0
        },
        "max_up_force": {
          "type": "number",
          "title": "Max Up Force",
          "description": "Maximum upward launch force applied to spawned objects",
          "default": 17.0,
          "minimum": 1.0,
          "maximum": 50.0
        },
        "side_force": {
          "type": "number",
          "title": "Side Force",
          "description": "Lateral force applied to spawned objects for horizontal spread",
          "default": 2.5,
          "minimum": 0.0,
          "maximum": 20.0
        },
        "gravity_multiplier": {
          "type": "number",
          "title": "Gravity Multiplier",
          "description": "Multiplier applied to gravity affecting all objects",
          "default": 1.0,
          "minimum": 0.1,
          "maximum": 5.0
        },
        "actions_to_complete": {
          "type": "integer",
          "title": "Actions to Complete",
          "description": "Number of successful actions required to complete the game",
          "default": 5,
          "minimum": 1,
          "maximum": 100
        },
        "freeze_duration": {
          "type": "number",
          "title": "Freeze Duration (seconds)",
          "description": "Duration in seconds for which the freeze bonus effect lasts",
          "default": 5.0,
          "minimum": 0.5,
          "maximum": 30.0
        },
        "freeze_overlay_color_hex": {
          "type": "string",
          "title": "Freeze Overlay Color (Hex)",
          "description": "RGBA hex color of the overlay displayed during a freeze effect",
          "default": "#0000FF4D",
          "minLength": 7,
          "maxLength": 9
        },
        "good_model_scale": {
          "type": "number",
          "title": "Good Model Scale",
          "description": "Global scale multiplier applied to good (target) objects",
          "default": 1.0,
          "minimum": 0.1,
          "maximum": 10.0
        },
        "bad_model_scale": {
          "type": "number",
          "title": "Bad Model Scale",
          "description": "Global scale multiplier applied to bad (bomb) objects",
          "default": 1.0,
          "minimum": 0.1,
          "maximum": 10.0
        },
        "bonus_model_scale": {
          "type": "number",
          "title": "Bonus Model Scale",
          "description": "Global scale multiplier applied to bonus objects",
          "default": 0.5,
          "minimum": 0.1,
          "maximum": 10.0
        }
      }
    },
    "game": {
      "type": "object",
      "title": "Game Objects",
      "description": "3D model assets used for good, bomb, and bonus game objects",
      "required": ["good_objects", "bomb_objects", "bonus_items", "click_effect_url", "explosion_effect_url"],
      "properties": {
        "good_objects": {
          "type": "array",
          "title": "Good Objects",
          "description": "List of 3D models representing objects the player should click",
          "items": {
            "type": "object",
            "title": "Good Object",
            "required": ["url", "scale"],
            "properties": {
              "url": {
                "type": ["string", "null"],
                "title": "Model URL",
                "description": "URL to the .glb 3D model file",
                "format": "uri"
              },
              "scale": {
                "type": "number",
                "title": "Scale",
                "description": "Render scale of this specific good object",
                "default": 1.0,
                "minimum": 0.01,
                "maximum": 100.0
              }
            }
          }
        },
        "bomb_objects": {
          "type": "array",
          "title": "Bomb Objects",
          "description": "List of 3D models representing objects the player should avoid",
          "items": {
            "type": "object",
            "title": "Bomb Object",
            "required": ["url", "scale"],
            "properties": {
              "url": {
                "type": ["string", "null"],
                "title": "Model URL",
                "description": "URL to the .glb 3D model file for the bomb",
                "format": "uri"
              },
              "scale": {
                "type": "number",
                "title": "Scale",
                "description": "Render scale of this specific bomb object",
                "default": 5.0,
                "minimum": 0.01,
                "maximum": 100.0
              }
            }
          }
        },
        "bonus_items": {
          "type": "array",
          "title": "Bonus Items",
          "description": "List of 3D models representing special bonus items with power-up effects",
          "items": {
            "type": "object",
            "title": "Bonus Item",
            "required": ["url", "effect_id", "weight", "scale"],
            "properties": {
              "url": {
                "type": ["string", "null"],
                "title": "Model URL",
                "description": "URL to the .glb 3D model file for the bonus item",
                "format": "uri"
              },
              "effect_id": {
                "type": "integer",
                "title": "Effect ID",
                "description": "Identifier of the effect triggered when this bonus item is collected",
                "minimum": 1,
                "maximum": 99
              },
              "weight": {
                "type": "number",
                "title": "Spawn Weight",
                "description": "Relative spawn probability weight for this bonus item (higher = more frequent)",
                "minimum": 0.0,
                "maximum": 1.0
              },
              "scale": {
                "type": "number",
                "title": "Scale",
                "description": "Render scale of this specific bonus item",
                "default": 2.0,
                "minimum": 0.01,
                "maximum": 100.0
              }
            }
          }
        },
        "click_effect_url": {
          "type": ["string", "null"],
          "title": "Click Effect URL",
          "description": "URL to the visual effect asset shown on regular object click",
          "format": "uri",
          "default": ""
        },
        "explosion_effect_url": {
          "type": ["string", "null"],
          "title": "Explosion Effect URL",
          "description": "URL to the visual effect asset shown on bomb explosion",
          "format": "uri",
          "default": ""
        }
      }
    },
    "audio": {
      "type": "object",
      "title": "Audio",
      "description": "Audio asset URLs for game music and sound effects",
      "required": ["music_url", "win_url", "lose_url", "bomb_url", "normal_item_url", "bonus_item_url", "click_url"],
      "properties": {
        "music_url": {
          "type": ["string", "null"],
          "title": "Background Music URL",
          "description": "URL of the background music track played during gameplay",
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
        "bomb_url": {
          "type": ["string", "null"],
          "title": "Bomb Sound URL",
          "description": "URL of the sound played when the player hits a bomb",
          "format": "uri",
          "default": ""
        },
        "normal_item_url": {
          "type": ["string", "null"],
          "title": "Normal Item Sound URL",
          "description": "URL of the sound played when a normal good item is clicked",
          "format": "uri",
          "default": ""
        },
        "bonus_item_url": {
          "type": ["string", "null"],
          "title": "Bonus Item Sound URL",
          "description": "URL of the sound played when a bonus item is clicked",
          "format": "uri",
          "default": ""
        },
        "click_url": {
          "type": ["string", "null"],
          "title": "Click Sound URL",
          "description": "URL of the generic click sound for UI interactions",
          "format": "uri",
          "default": ""
        }
      }
    },
    "texts": {
      "type": "object",
      "title": "Texts",
      "description": "Customizable text strings displayed in the game UI",
      "required": ["victory_title", "victory_phrase", "defeat_title", "defeat_phrase"],
      "properties": {
        "victory_title": {
          "type": "string",
          "title": "Victory Title",
          "description": "Title text shown on the victory screen",
          "default": "VICTORY",
          "minLength": 1,
          "maxLength": 50
        },
        "victory_phrase": {
          "type": "string",
          "title": "Victory Phrase",
          "description": "Subtitle phrase shown on the victory screen",
          "default": "You made it!",
          "minLength": 1,
          "maxLength": 200
        },
        "defeat_title": {
          "type": "string",
          "title": "Defeat Title",
          "description": "Title text shown on the game over screen",
          "default": "GAME OVER",
          "minLength": 1,
          "maxLength": 50
        },
        "defeat_phrase": {
          "type": "string",
          "title": "Defeat Phrase",
          "description": "Subtitle phrase shown on the game over screen",
          "default": "Try again!",
          "minLength": 1,
          "maxLength": 200
        }
      }
    },
    "rewards": {
      "type": "object",
      "title": "Rewards",
      "description": "Coin reward values and combo multiplier settings",
      "required": ["coins_per_action", "coins_on_completion", "combo_multiplier"],
      "properties": {
        "coins_per_action": {
          "type": "integer",
          "title": "Coins per Action",
          "description": "Number of coins awarded for each successful action",
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
        },
        "combo_multiplier": {
          "type": "number",
          "title": "Combo Multiplier",
          "description": "Multiplier increment applied per consecutive combo hit",
          "default": 0.1,
          "minimum": 0.0,
          "maximum": 5.0
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
          "default": "https://placehold.co/500x500/FFD700/FFFFFF.png?text=COIN"
        },
        "coin_count_url": {
          "type": ["string", "null"],
          "title": "Coin Count Image URL",
          "description": "URL of the icon displayed next to the coin counter",
          "format": "uri",
          "default": "https://placehold.co/128x128/FFD700/FFFFFF.png?text=COUNT"
        }
      }
    },
    "particleEffects": {
      "type": "array",
      "title": "Particle Effects",
      "description": "List of particle effect definitions used throughout the game",
      "items": {
        "type": "object",
        "title": "Particle Effect",
        "required": ["id", "effect_type", "color_hex", "count", "start_size", "lifetime", "speed", "sprite_url"],
        "properties": {
          "id": {
            "type": "string",
            "title": "Effect ID",
            "description": "Unique short identifier for this particle effect",
            "minLength": 1,
            "maxLength": 20
          },
          "effect_type": {
            "type": "string",
            "title": "Effect Type",
            "description": "Type of particle effect behavior",
            "enum": ["Explosion", "Expand", "Fountain", "Orbital", "Implosion", "Rising"],
            "default": "Explosion"
          },
          "color_hex": {
            "type": "string",
            "title": "Color (Hex)",
            "description": "Color of the particles in hex format (e.g. #FF5500)",
            "minLength": 7,
            "maxLength": 9
          },
          "count": {
            "type": "integer",
            "title": "Particle Count",
            "description": "Number of particles emitted per burst",
            "minimum": 1,
            "maximum": 500
          },
          "start_size": {
            "type": "number",
            "title": "Start Size",
            "description": "Initial size of each particle at spawn",
            "minimum": 0.01,
            "maximum": 10.0
          },
          "lifetime": {
            "type": "number",
            "title": "Lifetime (seconds)",
            "description": "Duration in seconds each particle lives before disappearing",
            "minimum": 0.1,
            "maximum": 20.0
          },
          "speed": {
            "type": "number",
            "title": "Speed",
            "description": "Movement speed of the particles",
            "minimum": 0.0,
            "maximum": 50.0
          },
          "sprite_url": {
            "type": ["string", "null"],
            "title": "Sprite URL",
            "description": "Optional URL of a custom sprite image for the particles",
            "format": "uri",
            "default": ""
          }
        }
      }
    }
  }
}',
    '{
  "ui:order": ["meta", "branding", "game_config", "game", "audio", "texts", "rewards", "personalization", "particleEffects"],
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
    "ui:description": "Visual branding and background shader settings",
    "images": {
      "ui:title": "Brand Images",
      "main_image_url": {
        "ui:widget": "assetUpload",
        "ui:options": { "assetType": "image" },
        "ui:title": "Main Image",
        "ui:placeholder": "https://...",
        "ui:help": "Recommended size: 400x200px"
      },
      "logo_watermark_url": {
        "ui:widget": "assetUpload",
        "ui:options": { "assetType": "image" },
        "ui:title": "Logo Watermark",
        "ui:placeholder": "https://...",
        "ui:help": "Recommended size: 150x50px"
      }
    },
    "shader_background_config": {
      "ui:title": "Shader Background",
      "Back": {
        "ui:title": "Back Layer",
        "Enabled": {
          "ui:widget": "checkbox",
          "ui:title": "Enable Back Layer"
        },
        "SpriteUrl": {
          "ui:widget": "assetUpload",
          "ui:options": { "assetType": "image" },
          "ui:title": "Sprite Texture",
          "ui:placeholder": "https://..."
        },
        "ColorHex": {
          "ui:widget": "colorPicker",
          "ui:title": "Tint Color",
          "ui:help": "RGBA hex color, e.g. #FFFFFFFF"
        },
        "ScrollSpeedX": {
          "ui:widget": "decimalInput",
          "ui:title": "Scroll Speed X",
          "ui:placeholder": "0.05"
        },
        "ScrollSpeedY": {
          "ui:widget": "decimalInput",
          "ui:title": "Scroll Speed Y",
          "ui:placeholder": "0.0"
        },
        "TilingX": {
          "ui:widget": "decimalInput",
          "ui:title": "Tiling X",
          "ui:placeholder": "1.0"
        },
        "TilingY": {
          "ui:widget": "decimalInput",
          "ui:title": "Tiling Y",
          "ui:placeholder": "1.0"
        },
        "WaveSpeed": {
          "ui:widget": "decimalInput",
          "ui:title": "Wave Speed",
          "ui:placeholder": "0.0"
        },
        "WaveFrequency": {
          "ui:widget": "decimalInput",
          "ui:title": "Wave Frequency",
          "ui:placeholder": "0.0"
        },
        "WaveAmplitude": {
          "ui:widget": "decimalInput",
          "ui:title": "Wave Amplitude",
          "ui:placeholder": "0.0"
        }
      },
      "Front": {
        "ui:title": "Front Layer",
        "Enabled": {
          "ui:widget": "checkbox",
          "ui:title": "Enable Front Layer"
        },
        "SpriteUrl": {
          "ui:widget": "assetUpload",
          "ui:options": { "assetType": "image" },
          "ui:title": "Sprite Texture",
          "ui:placeholder": "https://..."
        },
        "ColorHex": {
          "ui:widget": "colorPicker",
          "ui:title": "Tint Color",
          "ui:help": "RGBA hex color, e.g. #FFFFFF00"
        },
        "ScrollSpeedX": {
          "ui:widget": "decimalInput",
          "ui:title": "Scroll Speed X",
          "ui:placeholder": "0.1"
        },
        "ScrollSpeedY": {
          "ui:widget": "decimalInput",
          "ui:title": "Scroll Speed Y",
          "ui:placeholder": "0.0"
        },
        "TilingX": {
          "ui:widget": "decimalInput",
          "ui:title": "Tiling X",
          "ui:placeholder": "1.0"
        },
        "TilingY": {
          "ui:widget": "decimalInput",
          "ui:title": "Tiling Y",
          "ui:placeholder": "1.0"
        }
      }
    }
  },
  "game_config": {
    "ui:title": "Game Config",
    "ui:description": "Core gameplay parameters",
    "duration": {
      "ui:widget": "numberInput",
      "ui:title": "Duration (seconds)",
      "ui:placeholder": "60"
    },
    "lives": {
      "ui:widget": "numberInput",
      "ui:title": "Lives",
      "ui:placeholder": "3"
    },
    "spawn_interval": {
      "ui:widget": "decimalInput",
      "ui:title": "Spawn Interval",
      "ui:placeholder": "1.0",
      "ui:help": "Initial seconds between spawns"
    },
    "min_spawn_interval": {
      "ui:widget": "decimalInput",
      "ui:title": "Min Spawn Interval",
      "ui:placeholder": "0.4"
    },
    "difficulty_decrease_step": {
      "ui:widget": "decimalInput",
      "ui:title": "Difficulty Decrease Step",
      "ui:placeholder": "0.02"
    },
    "min_up_force": {
      "ui:widget": "decimalInput",
      "ui:title": "Min Up Force",
      "ui:placeholder": "13.0"
    },
    "max_up_force": {
      "ui:widget": "decimalInput",
      "ui:title": "Max Up Force",
      "ui:placeholder": "17.0"
    },
    "side_force": {
      "ui:widget": "decimalInput",
      "ui:title": "Side Force",
      "ui:placeholder": "2.5"
    },
    "gravity_multiplier": {
      "ui:widget": "decimalInput",
      "ui:title": "Gravity Multiplier",
      "ui:placeholder": "1.0"
    },
    "actions_to_complete": {
      "ui:widget": "numberInput",
      "ui:title": "Actions to Complete",
      "ui:placeholder": "5"
    },
    "freeze_duration": {
      "ui:widget": "decimalInput",
      "ui:title": "Freeze Duration (seconds)",
      "ui:placeholder": "5.0"
    },
    "freeze_overlay_color_hex": {
      "ui:widget": "colorPicker",
      "ui:title": "Freeze Overlay Color",
      "ui:help": "RGBA hex color, e.g. #0000FF4D"
    },
    "good_model_scale": {
      "ui:widget": "decimalInput",
      "ui:title": "Good Model Scale",
      "ui:placeholder": "1.0"
    },
    "bad_model_scale": {
      "ui:widget": "decimalInput",
      "ui:title": "Bad Model Scale",
      "ui:placeholder": "1.0"
    },
    "bonus_model_scale": {
      "ui:widget": "decimalInput",
      "ui:title": "Bonus Model Scale",
      "ui:placeholder": "0.5"
    }
  },
  "game": {
    "ui:title": "Game Objects",
    "ui:description": "3D model assets for game objects",
    "good_objects": {
      "ui:title": "Good Objects",
      "items": {
        "url": {
          "ui:widget": "assetUpload",
          "ui:options": { "assetType": "image" },
          "ui:title": "Model URL (.glb)",
          "ui:placeholder": "https://..."
        },
        "scale": {
          "ui:widget": "decimalInput",
          "ui:title": "Scale",
          "ui:placeholder": "1.0"
        }
      }
    },
    "bomb_objects": {
      "ui:title": "Bomb Objects",
      "items": {
        "url": {
          "ui:widget": "assetUpload",
          "ui:options": { "assetType": "image" },
          "ui:title": "Model URL (.glb)",
          "ui:placeholder": "https://..."
        },
        "scale": {
          "ui:widget": "decimalInput",
          "ui:title": "Scale",
          "ui:placeholder": "5.0"
        }
      }
    },
    "bonus_items": {
      "ui:title": "Bonus Items",
      "items": {
        "url": {
          "ui:widget": "assetUpload",
          "ui:options": { "assetType": "image" },
          "ui:title": "Model URL (.glb)",
          "ui:placeholder": "https://..."
        },
        "effect_id": {
          "ui:widget": "numberInput",
          "ui:title": "Effect ID",
          "ui:placeholder": "1"
        },
        "weight": {
          "ui:widget": "decimalInput",
          "ui:title": "Spawn Weight",
          "ui:placeholder": "0.4",
          "ui:help": "Value between 0.0 and 1.0"
        },
        "scale": {
          "ui:widget": "decimalInput",
          "ui:title": "Scale",
          "ui:placeholder": "2.0"
        }
      }
    },
    "click_effect_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "image" },
      "ui:title": "Click Effect Asset",
      "ui:placeholder": "https://..."
    },
    "explosion_effect_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "image" },
      "ui:title": "Explosion Effect Asset",
      "ui:placeholder": "https://..."
    }
  },
  "audio": {
    "ui:title": "Audio",
    "ui:description": "Sound effects and music assets",
    "music_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:title": "Background Music",
      "ui:placeholder": "https://..."
    },
    "win_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:title": "Win Sound",
      "ui:placeholder": "https://..."
    },
    "lose_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:title": "Lose Sound",
      "ui:placeholder": "https://..."
    },
    "bomb_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:title": "Bomb Sound",
      "ui:placeholder": "https://..."
    },
    "normal_item_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:title": "Normal Item Sound",
      "ui:placeholder": "https://..."
    },
    "bonus_item_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:title": "Bonus Item Sound",
      "ui:placeholder": "https://..."
    },
    "click_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:title": "Click Sound",
      "ui:placeholder": "https://..."
    }
  },
  "texts": {
    "ui:title": "Texts",
    "ui:description": "Customizable game UI text strings",
    "victory_title": {
      "ui:widget": "textInput",
      "ui:title": "Victory Title",
      "ui:placeholder": "VICTORY"
    },
    "victory_phrase": {
      "ui:widget": "textInput",
      "ui:title": "Victory Phrase",
      "ui:placeholder": "You made it!"
    },
    "defeat_title": {
      "ui:widget": "textInput",
      "ui:title": "Defeat Title",
      "ui:placeholder": "GAME OVER"
    },
    "defeat_phrase": {
      "ui:widget": "textInput",
      "ui:title": "Defeat Phrase",
      "ui:placeholder": "Try again!"
    }
  },
  "rewards": {
    "ui:title": "Rewards",
    "ui:description": "Coin and combo reward settings",
    "coins_per_action": {
      "ui:widget": "numberInput",
      "ui:title": "Coins per Action",
      "ui:placeholder": "10"
    },
    "coins_on_completion": {
      "ui:widget": "numberInput",
      "ui:title": "Coins on Completion",
      "ui:placeholder": "100"
    },
    "combo_multiplier": {
      "ui:widget": "decimalInput",
      "ui:title": "Combo Multiplier",
      "ui:placeholder": "0.1",
      "ui:help": "Increment added to the multiplier per combo hit"
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
      "ui:help": "Recommended size: 500x500px"
    },
    "coin_count_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "image" },
      "ui:title": "Coin Count Icon",
      "ui:placeholder": "https://...",
      "ui:help": "Recommended size: 128x128px"
    }
  },
  "particleEffects": {
    "ui:title": "Particle Effects",
    "ui:description": "Define visual particle effects used in the game",
    "items": {
      "id": {
        "ui:widget": "textInput",
        "ui:title": "Effect ID",
        "ui:placeholder": "ex"
      },
      "effect_type": {
        "ui:widget": "radio",
        "ui:title": "Effect Type"
      },
      "color_hex": {
        "ui:widget": "colorPicker",
        "ui:title": "Color"
      },
      "count": {
        "ui:widget": "numberInput",
        "ui:title": "Particle Count",
        "ui:placeholder": "30"
      },
      "start_size": {
        "ui:widget": "decimalInput",
        "ui:title": "Start Size",
        "ui:placeholder": "0.4"
      },
      "lifetime": {
        "ui:widget": "decimalInput",
        "ui:title": "Lifetime (seconds)",
        "ui:placeholder": "0.8"
      },
      "speed": {
        "ui:widget": "decimalInput",
        "ui:title": "Speed",
        "ui:placeholder": "8.0"
      },
      "sprite_url": {
        "ui:widget": "assetUpload",
        "ui:options": { "assetType": "image" },
        "ui:title": "Sprite URL",
        "ui:placeholder": "https://..."
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
    WHERE game_id = 1 AND version = 1
);