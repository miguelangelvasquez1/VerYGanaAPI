INSERT INTO games (id, title, description, delivery_type, url, front_page_url, active, created_at, updated_at)
SELECT
		 16,
    'Stack Tower',
    'Apila contenedores con precisión. Acumula la mayor cantidad posible sin que se caigan.',
    'PATH',
    'stack-tower',
    'https://games.verygana.com/game_icons/bogota/stack_tower.png',
     true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM games WHERE id = 16);

 
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
    16,
    1,
    '{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "title": "Container Game Configuration",
  "description": "Full configuration for a container-based game where players collect or match containers to earn keys",
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
      "description": "Core gameplay parameters for the container session",
      "required": [],
      "properties": {}
    },
    "game": {
      "type": "object",
      "title": "Game Assets",
      "description": "Container colors, images and key progression settings",
      "required": ["container_colors", "container_images", "containers_per_key"],
      "properties": {
        "container_colors": {
          "type": "array",
          "title": "Container Colors",
          "description": "List of tint colors applied to containers, cycled or assigned in order",
          "minItems": 1,
          "items": {
            "type": "string",
            "title": "Color",
            "description": "A tint color in hexadecimal format applied to a container",
            "default": "#FFFFFF",
            "minLength": 4,
            "maxLength": 9
          }
        },
        "container_images": {
          "type": "array",
          "title": "Container Images",
          "description": "List of images used as container visuals, cycled or assigned in order",
          "minItems": 1,
          "items": {
            "type": "object",
            "title": "Container Image",
            "description": "A single image entry for a container",
            "required": ["url"],
            "properties": {
              "url": {
                "type": ["string", "null"],
                "title": "Image URL",
                "description": "URL of the container image",
                "format": "uri",
                "default": ""
              }
            }
          }
        },
        "containers_per_key": {
          "type": "integer",
          "title": "Containers Per Key",
          "description": "Number of containers the player must collect or complete to earn one key",
          "default": 0,
          "minimum": 0,
          "maximum": 1000
        }
      }
    },
    "audio": {
      "type": "object",
      "title": "Audio",
      "description": "URLs for all game audio assets",
      "required": ["key_win_url", "victory_url"],
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
          "description": "URL of the sound played when the game is completed",
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
          "description": "Keys awarded each time the player completes a container action",
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
    "ui:description": "Container visuals and key progression settings",
    "ui:order": ["containers_per_key", "container_colors", "container_images"],
    "containers_per_key": {
      "ui:widget": "numberInput",
      "ui:placeholder": "0",
      "ui:help": "How many containers the player must collect or complete to earn one key"
    },
    "container_colors": {
      "ui:title": "Container Colors",
      "ui:description": "Add one color per container type. Colors are cycled or assigned in order.",
      "items": {
        "ui:widget": "colorPicker",
        "ui:help": "Tint color applied to this container"
      }
    },
    "container_images": {
      "ui:title": "Container Images",
      "ui:description": "Add one image per container type. Images are cycled or assigned in order.",
      "items": {
        "url": {
          "ui:widget": "assetUpload",
          "ui:options": { "assetType": "image" },
          "ui:help": "Image used for this container"
        }
      }
    }
  },
  "audio": {
    "ui:title": "Audio",
    "ui:description": "Sound effects for key events",
    "ui:order": ["key_win_url", "victory_url"],
    "key_win_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:help": "Sound played when the player earns a key"
    },
    "victory_url": {
      "ui:widget": "assetUpload",
      "ui:options": { "assetType": "audio" },
      "ui:help": "Sound played when the game is completed"
    }
  },
  "texts": {
    "ui:title": "UI Texts",
    "ui:description": "Messages displayed on the game over screen",
    "game_over_messages": {
      "ui:title": "Game Over Messages",
      "ui:description": "Add one or more messages to display when the game ends",
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
      "ui:help": "Keys granted each time the player completes a container action"
    }
  }
}',
    true,
    true,
    NOW(),
    'system'
WHERE NOT EXISTS (
    SELECT 1 FROM game_config_definitions 
    WHERE game_id = 16 AND version = 1
);