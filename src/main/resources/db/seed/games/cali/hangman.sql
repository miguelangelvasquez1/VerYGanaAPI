INSERT INTO games (id, title, description, delivery_type, url, front_page_url, active, created_at, updated_at)
SELECT
		 5,
    'Hangman',
    'Adivina la palabra oculta letra por letra antes de agotar tus intentos. Usa power-ups para obtener pistas.',
    'QUERY',
    'Hangman',
    'https://games.verygana.com/game_icons/cali/hangman.png',
     true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM games WHERE id = 5);

 
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
    5,
    1,
    '{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "title": "Game Configuration",
  "required": ["meta", "branding", "game_config", "game", "audio", "texts", "rewards", "personalization"],
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
      "description": "Visual branding assets, shader layers, and parallax configuration",
      "required": ["images", "shader_background_config"],
      "properties": {
        "images": {
          "type": "object",
          "title": "Branding Images",
          "description": "Logo, watermark, and keyboard sprite image assets",
          "required": ["main_image_url", "main_image_offset_y", "logo_watermark_url", "logo_watermark_offset_y", "keyboard_sprite_url"],
          "properties": {
            "main_image_url": {
              "type": ["string", "null"],
              "title": "Main Image URL",
              "description": "URL of the main branding image or logo",
              "format": "uri",
              "default": "https://placehold.co/400x200/FF5733/FFFFFF.png?text=LOGO"
            },
            "main_image_offset_y": {
              "type": "integer",
              "title": "Main Image Offset Y",
              "description": "Vertical pixel offset for positioning the main branding image",
              "minimum": -1000,
              "maximum": 1000,
              "default": 0
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
              "description": "Vertical pixel offset for positioning the watermark logo",
              "minimum": -1000,
              "maximum": 1000,
              "default": 0
            },
            "keyboard_sprite_url": {
              "type": ["string", "null"],
              "title": "Keyboard Sprite URL",
              "description": "URL of the sprite image used for keyboard key buttons",
              "format": "uri",
              "default": "https://placehold.co/64x64/00FF00/000000.png?text=KEY"
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
              "required": ["SpriteUrl", "ColorHex", "Speed", "Alpha", "Rotation", "Tiling", "Direction"],
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
                  "default": 0.2
                },
                "Rotation": {
                  "type": "number",
                  "title": "Rotation",
                  "description": "Rotation angle in degrees for the background layer",
                  "minimum": -360.0,
                  "maximum": 360.0,
                  "multipleOf": 0.01,
                  "default": 0
                },
                "Alpha": {
                  "type": "number",
                  "title": "Alpha",
                  "description": "Opacity of the background layer (0.0 transparent to 1.0 opaque)",
                  "minimum": 0.0,
                  "maximum": 1.0,
                  "multipleOf": 0.01,
                  "default": 1
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
                }
              }
            },
            "Front": {
              "type": "object",
              "title": "Front Layer",
              "description": "Configuration for the foreground shader layer",
              "required": ["SpriteUrl", "ColorHex", "Speed", "Alpha", "Rotation", "Tiling", "Direction"],
              "properties": {
                "SpriteUrl": {
                  "type": ["string", "null"],
                  "title": "Sprite URL",
                  "description": "URL of the foreground sprite image",
                  "format": "uri",
                  "default": ""
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
                  "default": 0.5
                },
                "Rotation": {
                  "type": "number",
                  "title": "Rotation",
                  "description": "Rotation angle in degrees for the foreground layer",
                  "minimum": -360.0,
                  "maximum": 360.0,
                  "multipleOf": 0.01,
                  "default": 0
                },
                "Alpha": {
                  "type": "number",
                  "title": "Alpha",
                  "description": "Opacity of the foreground layer (0.0 transparent to 1.0 opaque)",
                  "minimum": 0.0,
                  "maximum": 1.0,
                  "multipleOf": 0.01,
                  "default": 0
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
                }
              }
            }
          }
        },
        "parallax_config": {
          "type": ["object", "null"],
          "title": "Parallax Configuration",
          "description": "Optional parallax scrolling layer configuration",
          "default": null
        }
      }
    },
    "game_config": {
      "type": "object",
      "title": "Game Configuration",
      "description": "Core gameplay rules and session parameters",
      "required": ["time_limit", "difficulty", "max_attempts"],
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
          "description": "Maximum number of incorrect letter attempts before game over",
          "minimum": 1,
          "maximum": 20,
          "default": 6
        }
      }
    },
    "game": {
      "type": "object",
      "title": "Game",
      "description": "Word list, power-ups, hangman progress images, and visual settings",
      "required": ["font_color_hex", "words", "power_ups_config", "hangman_progress_urls"],
      "properties": {
        "font_color_hex": {
          "type": "string",
          "title": "Font Color Hex",
          "description": "Hex color used for in-game text rendering",
          "minLength": 7,
          "maxLength": 9,
          "default": "#FFFFFF"
        },
        "words": {
          "type": "array",
          "title": "Words",
          "description": "List of word entries used in the hangman game",
          "minItems": 1,
          "items": {
            "type": "object",
            "title": "Word Entry",
            "description": "A single word, its hint, and associated score",
            "required": ["word", "hint", "score"],
            "properties": {
              "word": {
                "type": "string",
                "title": "Word",
                "description": "The word or phrase the player must guess",
                "minLength": 1,
                "maxLength": 200
              },
              "hint": {
                "type": "string",
                "title": "Hint",
                "description": "Clue displayed to help the player guess the word",
                "minLength": 1,
                "maxLength": 300
              },
              "score": {
                "type": "integer",
                "title": "Score",
                "description": "Points awarded for correctly guessing this word",
                "minimum": 0,
                "maximum": 100000,
                "default": 100
              }
            }
          },
          "default": [
            { "word": "SUN AND MOON", "hint": "Celestial Bodies", "score": 100 },
            { "word": "MOON", "hint": "Natural Satellite", "score": 150 },
            { "word": "BLUE SKY", "hint": "Above Us", "score": 200 }
          ]
        },
        "power_ups_config": {
          "type": "array",
          "title": "Power-Ups Configuration",
          "description": "List of available power-up definitions for the game",
          "items": {
            "type": "object",
            "title": "Power-Up",
            "description": "Definition of a single power-up item",
            "required": ["type", "display_name", "color_hex", "cost", "icon_url"],
            "properties": {
              "type": {
                "type": "string",
                "title": "Type",
                "description": "Internal type identifier for the power-up",
                "enum": ["RevealLetter", "ZapOptions", "ExtraLife"],
                "minLength": 1,
                "maxLength": 100
              },
              "display_name": {
                "type": "string",
                "title": "Display Name",
                "description": "Label shown to the player for this power-up",
                "minLength": 1,
                "maxLength": 100
              },
              "color_hex": {
                "type": "string",
                "title": "Color Hex",
                "description": "RGBA hex color used to tint the power-up icon",
                "minLength": 7,
                "maxLength": 9,
                "default": "#ffffffff"
              },
              "cost": {
                "type": "integer",
                "title": "Cost",
                "description": "Coin cost to activate this power-up",
                "minimum": 0,
                "maximum": 100000,
                "default": 50
              },
              "icon_url": {
                "type": ["string", "null"],
                "title": "Icon URL",
                "description": "URL of the icon image for this power-up",
                "format": "uri"
              }
            }
          },
          "default": [
            {
              "type": "RevealLetter",
              "display_name": "Hint",
              "color_hex": "#ffffffff",
              "cost": 50,
              "icon_url": "https://placehold.co/64x64/FFFF00/000000.png?text=HINT"
            },
            {
              "type": "ZapOptions",
              "display_name": "Zap",
              "color_hex": "#ffffffff",
              "cost": 30,
              "icon_url": "https://placehold.co/64x64/00FFFF/000000.png?text=ZAP"
            },
            {
              "type": "ExtraLife",
              "display_name": "Life",
              "color_hex": "#ffffffff",
              "cost": 100,
              "icon_url": "https://placehold.co/64x64/FF00FF/FFFFFF.png?text=LIFE"
            }
          ]
        },
        "hangman_progress_urls": {
          "type": "array",
          "title": "Hangman Progress URLs",
          "description": "Ordered list of image URLs representing each stage of the hangman drawing",
          "minItems": 1,
          "maxItems": 20,
          "items": {
            "type": "string",
            "title": "Hangman Stage URL",
            "description": "URL of the image for a single hangman progress stage",
            "format": "uri"
          },
          "default": [
            "https://placehold.co/256x256/333333/FFFFFF.png?text=HANG_1",
            "https://placehold.co/256x256/333333/FFFFFF.png?text=HANG_2",
            "https://placehold.co/256x256/333333/FFFFFF.png?text=HANG_3",
            "https://placehold.co/256x256/333333/FFFFFF.png?text=HANG_4",
            "https://placehold.co/256x256/333333/FFFFFF.png?text=HANG_5",
            "https://placehold.co/256x256/333333/FFFFFF.png?text=HANG_6",
            "https://placehold.co/256x256/333333/FFFFFF.png?text=HANG_7"
          ]
        }
      }
    },
    "audio": {
      "type": "object",
      "title": "Audio",
      "description": "Audio asset URLs for music and all sound effects",
      "required": ["victory_sound_url", "defeat_sound_url", "click_url", "music_url", "reveal_sound_url", "zap_sound_url", "life_sound_url"],
      "properties": {
        "music_url": {
          "type": ["string", "null"],
          "title": "Background Music URL",
          "description": "URL of the background music audio file",
          "format": "uri",
          "default": "https://rtainor.com/verygana/musicmemory/nokia.mp3"
        },
        "victory_sound_url": {
          "type": ["string", "null"],
          "title": "Victory Sound URL",
          "description": "URL of the sound effect played on victory",
          "format": "uri",
          "default": "https://rtainor.com/verygana/musicmemory/nokia.mp3"
        },
        "defeat_sound_url": {
          "type": ["string", "null"],
          "title": "Defeat Sound URL",
          "description": "URL of the sound effect played on defeat",
          "format": "uri",
          "default": "https://rtainor.com/verygana/musicmemory/nokia.mp3"
        },
        "click_url": {
          "type": ["string", "null"],
          "title": "Click Sound URL",
          "description": "URL of the sound effect played on keyboard key press",
          "format": "uri",
          "default": "https://cdn.aframe.io/360-image-gallery-boilerplate/audio/click.ogg"
        },
        "reveal_sound_url": {
          "type": ["string", "null"],
          "title": "Reveal Sound URL",
          "description": "URL of the sound effect played when a letter is revealed via power-up",
          "format": "uri",
          "default": "https://cdn.aframe.io/360-image-gallery-boilerplate/audio/click.ogg"
        },
        "zap_sound_url": {
          "type": ["string", "null"],
          "title": "Zap Sound URL",
          "description": "URL of the sound effect played when the Zap power-up is activated",
          "format": "uri",
          "default": "https://cdn.aframe.io/360-image-gallery-boilerplate/audio/click.ogg"
        },
        "life_sound_url": {
          "type": ["string", "null"],
          "title": "Life Sound URL",
          "description": "URL of the sound effect played when the Extra Life power-up is activated",
          "format": "uri",
          "default": "https://rtainor.com/verygana/musicmemory/windows_xp.mp3"
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
          "default": "VICTORY!"
        },
        "victory_phrase": {
          "type": "string",
          "title": "Victory Phrase",
          "description": "Motivational phrase displayed on the victory screen",
          "minLength": 1,
          "maxLength": 300,
          "default": "Congratulations! You Won!"
        },
        "defeat_title": {
          "type": "string",
          "title": "Defeat Title",
          "description": "Title text displayed on the defeat screen",
          "minLength": 1,
          "maxLength": 100,
          "default": "GAME OVER"
        },
        "defeat_phrase": {
          "type": "string",
          "title": "Defeat Phrase",
          "description": "Motivational phrase displayed on the defeat screen",
          "minLength": 1,
          "maxLength": 300,
          "default": "Try Again"
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
  "ui:order": ["meta", "branding", "game_config", "game", "audio", "texts", "rewards", "personalization"],
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
    "ui:description": "Logo assets, keyboard sprite, and shader background layers",
    "images": {
      "ui:title": "Branding Images",
      "main_image_url": {
        "ui:widget": "assetUpload",
        "ui:title": "Main Branding Image",
        "ui:help": "Upload or provide URL for the main branding image or logo",
        "ui:options": { "assetType": "image" }
      },
      "main_image_offset_y": {
        "ui:widget": "numberInput",
        "ui:title": "Main Image Vertical Offset",
        "ui:placeholder": "e.g. 0",
        "ui:help": "Vertical pixel offset for positioning the main branding image"
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
        "ui:placeholder": "e.g. 0",
        "ui:help": "Vertical pixel offset for positioning the watermark logo"
      },
      "keyboard_sprite_url": {
        "ui:widget": "assetUpload",
        "ui:title": "Keyboard Key Sprite",
        "ui:help": "Upload or provide URL for the sprite image used for keyboard key buttons",
        "ui:options": { "assetType": "image" }
      }
    },
    "shader_background_config": {
      "ui:title": "Shader Background Configuration",
      "ui:description": "Configure the layered scrolling shader background",
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
          "ui:placeholder": "e.g. 0.2",
          "ui:help": "Scrolling speed of the back layer"
        },
        "Rotation": {
          "ui:widget": "decimalInput",
          "ui:title": "Rotation (degrees)",
          "ui:placeholder": "e.g. 0",
          "ui:help": "Rotation angle of the back layer sprite in degrees"
        },
        "Alpha": {
          "ui:widget": "decimalInput",
          "ui:title": "Alpha (Opacity)",
          "ui:placeholder": "0.0 to 1.0",
          "ui:help": "Opacity of the back layer"
        },
        "Tiling": {
          "ui:title": "Tiling",
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
        "ColorHex": {
          "ui:widget": "colorPicker",
          "ui:title": "Front Layer Color (RGBA Hex)",
          "ui:help": "RGBA hex color to tint the front layer sprite"
        },
        "Speed": {
          "ui:widget": "decimalInput",
          "ui:title": "Scroll Speed",
          "ui:placeholder": "e.g. 0.5",
          "ui:help": "Scrolling speed of the front layer"
        },
        "Rotation": {
          "ui:widget": "decimalInput",
          "ui:title": "Rotation (degrees)",
          "ui:placeholder": "e.g. 0",
          "ui:help": "Rotation angle of the front layer sprite in degrees"
        },
        "Alpha": {
          "ui:widget": "decimalInput",
          "ui:title": "Alpha (Opacity)",
          "ui:placeholder": "0.0 to 1.0",
          "ui:help": "Opacity of the front layer"
        },
        "Tiling": {
          "ui:title": "Tiling",
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
        }
      }
    },
    "parallax_config": {
      "ui:title": "Parallax Configuration",
      "ui:description": "Optional parallax scrolling layer configuration",
      "ui:help": "Leave empty to disable parallax. Fill in to enable layered parallax scrolling."
    }
  },
  "game_config": {
    "ui:title": "Game Configuration",
    "ui:description": "Core gameplay rules and session parameters",
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
      "ui:placeholder": "e.g. 6",
      "ui:help": "Maximum number of incorrect letter guesses allowed before game over"
    }
  },
  "game": {
    "ui:title": "Game",
    "ui:description": "Word list, power-ups, hangman progress images, and visual settings",
    "font_color_hex": {
      "ui:widget": "colorPicker",
      "ui:title": "Font Color (Hex)",
      "ui:help": "Hex color used for in-game text rendering"
    },
    "words": {
      "ui:title": "Words",
      "ui:description": "Define the words and phrases the player must guess",
      "ui:help": "Add one entry per word. Each entry requires a word, a hint, and a score.",
      "items": {
        "ui:order": ["word", "hint", "score"],
        "word": {
          "ui:widget": "textInput",
          "ui:title": "Word or Phrase",
          "ui:placeholder": "e.g. SUN AND MOON",
          "ui:help": "The word or phrase the player must guess. Use uppercase letters."
        },
        "hint": {
          "ui:widget": "textInput",
          "ui:title": "Hint",
          "ui:placeholder": "e.g. Celestial Bodies",
          "ui:help": "Clue displayed to assist the player in guessing the word"
        },
        "score": {
          "ui:widget": "numberInput",
          "ui:title": "Score",
          "ui:placeholder": "e.g. 100",
          "ui:help": "Points awarded for correctly guessing this word"
        }
      }
    },
    "power_ups_config": {
      "ui:title": "Power-Ups Configuration",
      "ui:description": "Define the available power-ups and their costs",
      "ui:help": "Add one entry per power-up type available to the player.",
      "items": {
        "ui:order": ["type", "display_name", "color_hex", "cost", "icon_url"],
        "type": {
          "ui:widget": "radio",
          "ui:title": "Power-Up Type",
          "ui:help": "Internal type identifier for this power-up"
        },
        "display_name": {
          "ui:widget": "textInput",
          "ui:title": "Display Name",
          "ui:placeholder": "e.g. Hint",
          "ui:help": "Label shown to the player for this power-up"
        },
        "color_hex": {
          "ui:widget": "colorPicker",
          "ui:title": "Icon Tint Color (RGBA Hex)",
          "ui:help": "RGBA hex color used to tint the power-up icon"
        },
        "cost": {
          "ui:widget": "numberInput",
          "ui:title": "Coin Cost",
          "ui:placeholder": "e.g. 50",
          "ui:help": "Number of coins required to activate this power-up"
        },
        "icon_url": {
          "ui:widget": "assetUpload",
          "ui:title": "Power-Up Icon",
          "ui:help": "Upload or provide URL for the power-up icon image",
          "ui:options": { "assetType": "image" }
        }
      }
    },
    "hangman_progress_urls": {
      "ui:title": "Hangman Progress Images",
      "ui:description": "Ordered images representing each stage of the hangman drawing",
      "ui:help": "Upload one image per incorrect guess stage, in order from first to last.",
      "items": {
        "ui:widget": "assetUpload",
        "ui:title": "Hangman Stage Image",
        "ui:options": { "assetType": "image" }
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
    "victory_sound_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Victory Sound",
      "ui:help": "Upload or provide URL for the victory sound effect",
      "ui:options": { "assetType": "audio" }
    },
    "defeat_sound_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Defeat Sound",
      "ui:help": "Upload or provide URL for the defeat sound effect",
      "ui:options": { "assetType": "audio" }
    },
    "click_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Key Click Sound",
      "ui:help": "Upload or provide URL for the keyboard key press sound effect",
      "ui:options": { "assetType": "audio" }
    },
    "reveal_sound_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Reveal Letter Sound",
      "ui:help": "Upload or provide URL for the sound effect when a letter is revealed by power-up",
      "ui:options": { "assetType": "audio" }
    },
    "zap_sound_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Zap Power-Up Sound",
      "ui:help": "Upload or provide URL for the Zap power-up activation sound effect",
      "ui:options": { "assetType": "audio" }
    },
    "life_sound_url": {
      "ui:widget": "assetUpload",
      "ui:title": "Extra Life Sound",
      "ui:help": "Upload or provide URL for the Extra Life power-up activation sound effect",
      "ui:options": { "assetType": "audio" }
    }
  },
  "texts": {
    "ui:title": "Texts",
    "ui:description": "Display texts for game result screens",
    "victory_title": {
      "ui:widget": "textInput",
      "ui:title": "Victory Title",
      "ui:placeholder": "e.g. VICTORY!",
      "ui:help": "Title shown on the victory screen"
    },
    "victory_phrase": {
      "ui:widget": "textInput",
      "ui:title": "Victory Phrase",
      "ui:placeholder": "e.g. Congratulations! You Won!",
      "ui:help": "Motivational phrase shown on the victory screen"
    },
    "defeat_title": {
      "ui:widget": "textInput",
      "ui:title": "Defeat Title",
      "ui:placeholder": "e.g. GAME OVER",
      "ui:help": "Title shown on the defeat screen"
    },
    "defeat_phrase": {
      "ui:widget": "textInput",
      "ui:title": "Defeat Phrase",
      "ui:placeholder": "e.g. Try Again",
      "ui:help": "Motivational phrase shown on the defeat screen"
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
    WHERE game_id = 5 AND version = 1
);