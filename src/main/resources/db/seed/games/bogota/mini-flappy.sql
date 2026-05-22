INSERT INTO games (id, title, description, delivery_type, url, front_page_url, active, created_at, updated_at)
SELECT
		 14,
    'Mini Flappy',
    'Vuela entre obstáculos tocando la pantalla. Los logos en las rocas y mensajes en los aviones pueden ser de tu marca.',
    'PATH',
    'mini-flappy',
    'https://games.verygana.com/game_icons/bogota/mini_flappy.png',
     true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM games WHERE id = 14);

 
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
    14,
    1,
    '{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "title": "Plane & Rock Runner Game Configuration",
  "description": "Full configuration for a runner game featuring a multi-color character, plane word obstacles and rock logo obstacles",
  "required": ["meta", "branding", "game_config", "game", "audio", "texts", "rewards"],
  "properties": {
    "meta": {
      "type": "object",
      "title": "Meta",
      "description": "Metadata identifying the brand and campaign",
      "required": ["brand_id", "campaign_id"],
      "properties": {
        "brand_id": {
          "type": "string",
          "title": "Brand ID",
          "description": "Identifier for the brand configuration",
          "default": "",
          "minLength": 0,
          "maxLength": 64
        },
        "campaign_id": {
          "type": "string",
          "title": "Campaign ID",
          "description": "Identifier for the marketing campaign this game belongs to",
          "default": "",
          "minLength": 0,
          "maxLength": 64
        }
      }
    },
    "branding": {
      "type": "object",
      "title": "Branding",
      "description": "Logo asset for the game",
      "required": ["main_logo_url"],
      "properties": {
        "main_logo_url": {
          "type": ["string", "null"],
          "title": "Main Logo URL",
          "description": "URL of the main game logo image",
          "format": "uri",
          "default": ""
        }
      }
    },
    "game_config": {
      "type": "object",
      "title": "Game Config",
      "description": "Core gameplay parameters for the runner session",
      "required": [],
      "properties": {}
    },
    "game": {
      "type": "object",
      "title": "Game Assets",
      "description": "Character setup, obstacle content and key spawn settings",
      "required": ["plane_words", "rock_logos", "character_id", "character_colors", "key_spawn_probability"],
      "properties": {
        "plane_words": {
          "type": "array",
          "title": "Plane Words",
          "description": "List of words or phrases displayed on plane obstacles during the run",
          "minItems": 1,
          "items": {
            "type": "string",
            "title": "Word",
            "description": "A single word or short phrase shown on a plane obstacle",
            "minLength": 1,
            "maxLength": 128
          }
        },
        "rock_logos": {
          "type": "array",
          "title": "Rock Logos",
          "description": "List of logo images displayed on rock obstacles during the run",
          "minItems": 1,
          "items": {
            "type": "object",
            "title": "Rock Logo",
            "description": "A single logo image entry for a rock obstacle",
            "required": ["url"],
            "properties": {
              "url": {
                "type": ["string", "null"],
                "title": "Image URL",
                "description": "URL of the logo image shown on the rock obstacle",
                "format": "uri",
                "default": ""
              }
            }
          }
        },
        "character_id": {
          "type": "integer",
          "title": "Character ID",
          "description": "ID of the preset character to use. Use -1 for the default character.",
          "default": -1,
          "minimum": -1
        },
        "character_colors": {
          "type": "array",
          "title": "Character Colors",
          "description": "Ordered list of tint colors applied to the character sprite zones or animation states. Expects exactly 3 values.",
          "minItems": 3,
          "maxItems": 3,
          "items": {
            "type": "string",
            "title": "Color",
            "description": "A tint color in hexadecimal format",
            "default": "#FFFFFF",
            "minLength": 4,
            "maxLength": 9
          }
        },
        "key_spawn_probability": {
          "type": "number",
          "title": "Key Spawn Probability",
          "description": "Probability of a key spawning during gameplay (0.0 = never, 1.0 = always)",
          "default": 0.0,
          "minimum": 0.0,
          "maximum": 1.0,
          "multipleOf": 0.01
        }
      }
    },
    "audio": {
      "type": "object",
      "title": "Audio",
      "description": "URLs for all game audio assets",
      "required": ["key_win_url", "lose_url"],
      "properties": {
        "key_win_url": {
          "type": ["string", "null"],
          "title": "Key Win Sound URL",
          "description": "URL of the sound played when the player collects a key",
          "format": "uri",
          "default": ""
        },
        "lose_url": {
          "type": ["string", "null"],
          "title": "Lose Sound URL",
          "description": "URL of the sound played when the player loses",
          "format": "uri",
          "default": ""
        }
      }
    },
    "texts": {
      "type": "object",
      "title": "Texts",
      "description": "Display text used throughout the game UI",
      "required": ["game_over_messages"],
      "properties": {
        "game_over_messages": {
          "type": "array",
          "title": "Game Over Messages",
          "description": "List of messages shown randomly or in sequence on the game over screen",
          "items": {
            "type": "string",
            "title": "Message",
            "description": "A single game over message string",
            "minLength": 0,
            "maxLength": 256
          }
        }
      }
    },
    "rewards": {
      "type": "object",
      "title": "Rewards",
      "description": "Key reward values for player actions",
      "required": ["keys_per_action"],
      "properties": {
        "keys_per_action": {
          "type": "integer",
          "title": "Keys Per Action",
          "description": "Keys awarded each time the player collects a key during the run",
          "default": 0,
          "minimum": 0,
          "maximum": 10000
        }
      }
    }
  }
}',
    '{
  "ui:order": ["meta", "branding", "game_config", "game", "audio", "texts", "rewards"],
  "meta": {
    "ui:title": "Meta",
    "ui:description": "Brand and campaign identifiers",
    "brand_id": {
      "ui:widget": "textInput",
      "ui:placeholder": "e.g. my-brand",
      "ui:help": "Unique identifier for the brand configuration"
    },
    "campaign_id": {
      "ui:widget": "textInput",
      "ui:placeholder": "e.g. summer-2025",
      "ui:help": "Identifier for the campaign this game is associated with"
    }
  },
  "branding": {
    "ui:title": "Branding",
    "ui:description": "Logo asset",
    "main_logo_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "image" },
      "ui:help": "Main logo displayed on the game screen"
    }
  },
  "game_config": {
    "ui:title": "Game Configuration",
    "ui:description": "Core gameplay parameters (no fields configured yet)"
  },
  "game": {
    "ui:title": "Game Assets",
    "ui:description": "Character setup, obstacle content and key spawn probability",
    "ui:order": ["character_id", "character_colors", "key_spawn_probability", "plane_words", "rock_logos"],
    "character_id": {
      "ui:widget": "numberInput",
      "ui:placeholder": "-1",
      "ui:help": "Preset character ID. Use -1 for the default character."
    },
    "character_colors": {
      "ui:title": "Character Colors",
      "ui:description": "Exactly 3 tint colors applied to the character sprite zones or animation states.",
      "items": {
        "ui:widget": "colorPicker",
        "ui:help": "Tint color for this character zone or state"
      }
    },
    "key_spawn_probability": {
      "ui:widget": "decimalInput",
      "ui:placeholder": "0.0",
      "ui:help": "Chance of a key spawning during gameplay (0.0 = never, 1.0 = always)"
    },
    "plane_words": {
      "ui:title": "Plane Words",
      "ui:description": "Words or phrases shown on plane obstacles. At least one entry is required.",
      "items": {
        "ui:widget": "textInput",
        "ui:placeholder": "e.g. ¡VUELA!",
        "ui:help": "A word or short phrase displayed on a plane obstacle"
      }
    },
    "rock_logos": {
      "ui:title": "Rock Logos",
      "ui:description": "Logo images shown on rock obstacles. At least one entry is required.",
      "items": {
        "url": {
          "ui:widget": "assetUpload",
          "ui:options": { "assetType": "image" },
          "ui:help": "Logo image displayed on a rock obstacle"
        }
      }
    }
  },
  "audio": {
    "ui:title": "Audio",
    "ui:description": "Sound effects for key events",
    "ui:order": ["key_win_url", "lose_url"],
    "key_win_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:help": "Sound played when the player collects a key"
    },
    "lose_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:help": "Sound played when the player loses"
    }
  },
  "texts": {
    "ui:title": "UI Texts",
    "ui:description": "Messages displayed on the game over screen",
    "game_over_messages": {
      "ui:title": "Game Over Messages",
      "ui:description": "Add one or more messages to display when the player loses",
      "items": {
        "ui:widget": "textInput",
        "ui:placeholder": "e.g. ¡Inténtalo de nuevo!",
        "ui:help": "A message shown on the game over screen"
      }
    }
  },
  "rewards": {
    "ui:title": "Rewards",
    "ui:description": "Key reward values for player actions",
    "keys_per_action": {
      "ui:widget": "numberInput",
      "ui:placeholder": "0",
      "ui:help": "Keys granted each time the player collects a key during the run"
    }
  }
}',
    true,
    true,
    NOW(),
    'system'
WHERE NOT EXISTS (
    SELECT 1 FROM game_config_definitions 
    WHERE game_id = 14 AND version = 1
);