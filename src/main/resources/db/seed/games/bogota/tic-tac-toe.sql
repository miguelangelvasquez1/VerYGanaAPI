INSERT INTO games (id, title, description, delivery_type, url, front_page_url, active, created_at, updated_at)
SELECT
		 17,
    'Tic Tac Toe',
    'El clásico tres en raya contra la IA. Personaliza las piezas con el logo de tu marca.',
    'PATH',
    'tictactoe',
    'https://games.verygana.com/game_icons/bogota/triqui.png',
     true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM games WHERE id = 17);

 
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
    17,
    1,
    '{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "title": "Round-Based Piece Game Configuration",
  "description": "Full configuration for a round-based game where players collect branded pieces and earn keys per round",
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
      "description": "Logo and watermark image assets",
      "required": ["main_logo_url", "watermark_logo_url"],
      "properties": {
        "main_logo_url": {
          "type": ["string", "null"],
          "title": "Main Logo URL",
          "description": "URL of the main game logo image",
          "format": "uri",
          "default": ""
        },
        "watermark_logo_url": {
          "type": ["string", "null"],
          "title": "Watermark Logo URL",
          "description": "URL of the small watermark logo overlaid on the game",
          "format": "uri",
          "default": ""
        }
      }
    },
    "game_config": {
      "type": "object",
      "title": "Game Config",
      "description": "Core gameplay parameters for the round-based session",
      "required": [],
      "properties": {}
    },
    "game": {
      "type": "object",
      "title": "Game Assets",
      "description": "Round win phrases and branded piece image",
      "required": ["round_win_phrases", "piece_logo_url"],
      "properties": {
        "round_win_phrases": {
          "type": "array",
          "title": "Round Win Phrases",
          "description": "List of phrases shown randomly or in sequence when the player wins a round",
          "minItems": 1,
          "items": {
            "type": "string",
            "title": "Phrase",
            "description": "A single phrase displayed on round victory",
            "minLength": 1,
            "maxLength": 256
          }
        },
        "piece_logo_url": {
          "type": ["string", "null"],
          "title": "Piece Logo URL",
          "description": "URL of the branded logo image displayed on the collectible piece",
          "format": "uri",
          "default": ""
        }
      }
    },
    "audio": {
      "type": "object",
      "title": "Audio",
      "description": "URLs for all game audio assets",
      "required": ["key_win_url", "victory_url", "game_over_url"],
      "properties": {
        "key_win_url": {
          "type": ["string", "null"],
          "title": "Key Win Sound URL",
          "description": "URL of the sound played when the player earns a key",
          "format": "uri",
          "default": ""
        },
        "victory_url": {
          "type": ["string", "null"],
          "title": "Victory Sound URL",
          "description": "URL of the sound played when the player completes the game",
          "format": "uri",
          "default": ""
        },
        "game_over_url": {
          "type": ["string", "null"],
          "title": "Game Over Sound URL",
          "description": "URL of the sound played when the game ends in defeat",
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
      "required": ["keys_per_action", "keys_on_completion"],
      "properties": {
        "keys_per_action": {
          "type": "integer",
          "title": "Keys Per Action",
          "description": "Keys awarded each time the player wins a round",
          "default": 0,
          "minimum": 0,
          "maximum": 10000
        },
        "keys_on_completion": {
          "type": "integer",
          "title": "Keys on Completion",
          "description": "Keys awarded upon completing all rounds",
          "default": 0,
          "minimum": 0,
          "maximum": 100000
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
    "ui:description": "Logo and watermark assets",
    "ui:order": ["main_logo_url", "watermark_logo_url"],
    "main_logo_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "image" },
      "ui:help": "Main logo displayed on the game screen"
    },
    "watermark_logo_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "image" },
      "ui:help": "Small watermark logo overlaid on the game"
    }
  },
  "game_config": {
    "ui:title": "Game Configuration",
    "ui:description": "Core gameplay parameters (no fields configured yet)"
  },
  "game": {
    "ui:title": "Game Assets",
    "ui:description": "Branded piece image and round win phrases",
    "ui:order": ["piece_logo_url", "round_win_phrases"],
    "piece_logo_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "image" },
      "ui:help": "Branded logo image shown on the collectible piece"
    },
    "round_win_phrases": {
      "ui:title": "Round Win Phrases",
      "ui:description": "Add one or more phrases to display when the player wins a round.",
      "items": {
        "ui:widget": "textInput",
        "ui:placeholder": "e.g. ¡Ronda superada!",
        "ui:help": "A phrase shown on the round victory screen"
      }
    }
  },
  "audio": {
    "ui:title": "Audio",
    "ui:description": "Sound effects for key events",
    "ui:order": ["key_win_url", "victory_url", "game_over_url"],
    "key_win_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:help": "Sound played when the player earns a key"
    },
    "victory_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:help": "Sound played when all rounds are completed"
    },
    "game_over_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:help": "Sound played when the game ends in defeat"
    }
  },
  "texts": {
    "ui:title": "UI Texts",
    "ui:description": "Messages displayed on the game over screen",
    "game_over_messages": {
      "ui:title": "Game Over Messages",
      "ui:description": "Add one or more messages to display when the game ends in defeat",
      "items": {
        "ui:widget": "textInput",
        "ui:placeholder": "e.g. ¡Inténtalo de nuevo!",
        "ui:help": "A message shown on the game over screen"
      }
    }
  },
  "rewards": {
    "ui:title": "Rewards",
    "ui:description": "Key reward values for player progression",
    "keys_per_action": {
      "ui:widget": "numberInput",
      "ui:placeholder": "0",
      "ui:help": "Keys granted each time the player wins a round"
    },
    "keys_on_completion": {
      "ui:widget": "numberInput",
      "ui:placeholder": "0",
      "ui:help": "Keys granted upon completing all rounds"
    }
  }
}',
    true,
    true,
    NOW(),
    'system'
WHERE NOT EXISTS (
    SELECT 1 FROM game_config_definitions 
    WHERE game_id = 17 AND version = 1
);