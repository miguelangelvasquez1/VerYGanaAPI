INSERT INTO games (id, title, description, delivery_type, url, front_page_url, active, created_at, updated_at)
SELECT
		 11,
    'Dash Runner',
    'Corre esquivando obstáculos y recoge llaves en el camino. ¡Llega lo más lejos posible!',
    'PATH',
    'dash-runner',
    'https://games.verygana.com/game_icons/bogota/dash_runner.png',
     true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM games WHERE id = 11);

 
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
    11,
    1,
    '{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "title": "Phrase Runner Game Configuration",
  "description": "Full configuration for a character-based runner game with scrolling background phrases",
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
      "description": "Visual assets, phrases and spawn settings for the game world",
      "required": ["background_phrases", "character_image_url", "character_color", "key_spawn_probability"],
      "properties": {
        "background_phrases": {
          "type": "array",
          "title": "Background Phrases",
          "description": "List of phrases that scroll across the background during gameplay",
          "minItems": 1,
          "items": {
            "type": "string",
            "title": "Phrase",
            "description": "A single phrase displayed in the scrolling background",
            "minLength": 1,
            "maxLength": 256
          }
        },
        "character_image_url": {
          "type": ["string", "null"],
          "title": "Character Image URL",
          "description": "URL of the player character sprite",
          "format": "uri",
          "default": ""
        },
        "character_color": {
          "type": "string",
          "title": "Character Tint Color",
          "description": "Tint color applied to the character sprite in hexadecimal format",
          "default": "#FFFFFF",
          "minLength": 4,
          "maxLength": 9
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
    "ui:description": "Character sprite, background phrases and key spawn settings",
    "ui:order": ["character_image_url", "character_color", "key_spawn_probability", "background_phrases"],
    "character_image_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "image" },
      "ui:help": "Sprite for the player character"
    },
    "character_color": {
      "ui:widget": "colorPicker",
      "ui:help": "Tint color applied to the character sprite"
    },
    "key_spawn_probability": {
      "ui:widget": "decimalInput",
      "ui:placeholder": "0.0",
      "ui:help": "Chance of a key spawning during gameplay (0.0 = never, 1.0 = always)"
    },
    "background_phrases": {
      "ui:title": "Background Phrases",
      "ui:description": "Add one phrase per entry. These will scroll across the background during gameplay.",
      "items": {
        "ui:widget": "textInput",
        "ui:placeholder": "e.g. ¡Corre y gana!",
        "ui:help": "A phrase displayed in the scrolling game background"
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
    WHERE game_id = 11 AND version = 1
);