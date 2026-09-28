-- endless-runner — versión 2 del esquema de configuración.
--
-- Exige `rewards.keys_per_action` >= 1 y `game.key_spawn_probability` > 0, con
-- defaults 1 y 0.3. El build descarta la configuración entera si cualquiera de los
-- dos vale 0: arranca con el contenido de fábrica y el popup dice «No hay
-- anunciantes», sin ningún error. La v1 tenía `default: 0` y `minimum: 0` en ambos,
-- así que el formulario del diseñador los pre-llenaba en 0 y la validación los dejaba
-- pasar. Verificado interceptando la respuesta en el navegador: solo con los dos
-- mayores que 0 carga el brandeo.
--
-- El resto del esquema sigue igual.
--
-- Va como versión nueva porque los seeds usan `INSERT ... WHERE NOT EXISTS`: editar
-- el archivo no toca ninguna base ya sembrada. `latestDefinition()` elige por
-- `max(version)`. Las campañas ya creadas guardan su propio `config_definition_id`.
--
-- Los dos statements se guardan con el `url` además del id: los ids del seed son
-- fijos, pero en una base sembrada de otra forma el id podría ser de otro juego, y
-- este script le cambiaría el esquema sin error.
UPDATE game_config_definitions SET is_latest = false
WHERE game_id = 12 AND version = 1
  AND EXISTS (SELECT 1 FROM games WHERE id = 12 AND url = 'endless-runner');

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
    12,
    2,
    '{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "title": "Wood Sign Runner Game Configuration",
  "description": "Full configuration for a runner game featuring a selectable character and wood sign obstacles with phrases and images",
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
      "description": "Character selection, wood sign content and key spawn settings",
      "required": ["character_id", "wood_sign_phrases", "wood_sign_images", "key_spawn_probability"],
      "properties": {
        "character_id": {
          "type": "integer",
          "title": "Character ID",
          "description": "ID of the preset character to use. Use -1 for the default character.",
          "default": -1,
          "minimum": -1
        },
        "wood_sign_phrases": {
          "type": "array",
          "title": "Wood Sign Phrases",
          "description": "List of text phrases displayed on wood sign obstacles during the run",
          "minItems": 1,
          "items": {
            "type": "string",
            "title": "Phrase",
            "description": "A single phrase shown on a wood sign obstacle",
            "minLength": 1,
            "maxLength": 256
          }
        },
        "wood_sign_images": {
          "type": "array",
          "title": "Wood Sign Images",
          "description": "List of images displayed on wood sign obstacles during the run",
          "minItems": 1,
          "items": {
            "type": "object",
            "title": "Wood Sign Image",
            "description": "A single image entry for a wood sign obstacle",
            "required": ["url"],
            "properties": {
              "url": {
                "type": ["string", "null"],
                "title": "Image URL",
                "description": "URL of the image shown on the wood sign",
                "format": "uri",
                "default": ""
              }
            }
          }
        },
        "key_spawn_probability": {
          "type": "number",
          "title": "Key Spawn Probability",
          "description": "Probability of a key spawning during gameplay. Must be greater than 0: with 0 the game discards the whole configuration (1.0 = always)",
          "default": 0.3,
          "exclusiveMinimum": 0.0,
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
          "description": "Keys awarded each time the player collects a key during the run. At least 1: with 0 the game discards the whole configuration",
          "default": 1,
          "minimum": 1,
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
    "ui:description": "Character selection, wood sign content and key spawn probability",
    "ui:order": ["character_id", "key_spawn_probability", "wood_sign_phrases", "wood_sign_images"],
    "character_id": {
      "ui:widget": "numberInput",
      "ui:placeholder": "-1",
      "ui:help": "Preset character ID to use. Set to -1 to use the default character."
    },
    "key_spawn_probability": {
      "ui:widget": "decimalInput",
      "ui:placeholder": "0.3",
      "ui:help": "Chance of a key spawning during gameplay. Must be greater than 0 (1.0 = always)"
    },
    "wood_sign_phrases": {
      "ui:title": "Wood Sign Phrases",
      "ui:description": "Text phrases shown on wood sign obstacles. At least one phrase is required.",
      "items": {
        "ui:widget": "textInput",
        "ui:placeholder": "e.g. ¡Sigue corriendo!",
        "ui:help": "A phrase displayed on a wood sign obstacle"
      }
    },
    "wood_sign_images": {
      "ui:title": "Wood Sign Images",
      "ui:description": "Images shown on wood sign obstacles. At least one image is required.",
      "items": {
        "url": {
          "ui:widget": "assetUpload",
          "ui:options": { "assetType": "image" },
          "ui:help": "Image displayed on a wood sign obstacle"
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
      "ui:placeholder": "1",
      "ui:help": "Keys granted each time the player collects a key during the run"
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
WHERE NOT EXISTS (SELECT 1 FROM game_config_definitions WHERE game_id = 12 AND version = 2)
  AND EXISTS (SELECT 1 FROM games WHERE id = 12 AND url = 'endless-runner');
