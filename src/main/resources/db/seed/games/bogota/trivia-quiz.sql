INSERT INTO games (id, title, description, delivery_type, url, front_page_url, active, created_at, updated_at)
SELECT
		 19,
    'Trivia Quiz',
    'Responde preguntas de trivia antes de que se acabe el tiempo. Crea preguntas sobre tu marca o industria.',
    'PATH',
    'trivia-quiz',
    'https://games.verygana.com/game_icons/bogota/trivia.png',
     true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM games WHERE id = 19);

 
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
    19,
    1,
    '{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "title": "Trivia / Quiz Game Configuration",
  "description": "Full configuration for a multiple-choice trivia or quiz game",
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
      "description": "Core gameplay parameters for the trivia session",
      "required": [],
      "properties": {}
    },
    "game": {
      "type": "object",
      "title": "Game Content",
      "description": "Questions and answer options that make up the quiz",
      "required": ["questions"],
      "properties": {
        "questions": {
          "type": "array",
          "title": "Questions",
          "description": "Ordered list of multiple-choice questions",
          "minItems": 1,
          "items": {
            "type": "object",
            "title": "Question",
            "description": "A single multiple-choice question with answer options",
            "required": ["id", "question", "options", "correct_answer_index"],
            "properties": {
              "id": {
                "type": "integer",
                "title": "Question ID",
                "description": "Unique numeric identifier for this question",
                "minimum": 1
              },
              "question": {
                "type": "string",
                "title": "Question Text",
                "description": "The question shown to the player",
                "default": "",
                "minLength": 1,
                "maxLength": 512
              },
              "options": {
                "type": "array",
                "title": "Answer Options",
                "description": "List of possible answers for this question",
                "minItems": 2,
                "maxItems": 6,
                "items": {
                  "type": "string",
                  "title": "Option",
                  "description": "A single answer option text",
                  "minLength": 1,
                  "maxLength": 256
                }
              },
              "correct_answer_index": {
                "type": "integer",
                "title": "Correct Answer Index",
                "description": "Zero-based index of the correct answer within the options array",
                "default": 0,
                "minimum": 0,
                "maximum": 5
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
          "description": "URL of the sound played when the quiz is completed",
          "format": "uri",
          "default": ""
        }
      }
    },
    "texts": {
      "type": "object",
      "title": "Texts",
      "description": "Display text used throughout the game UI",
      "required": ["victory_messages"],
      "properties": {
        "victory_messages": {
          "type": "array",
          "title": "Victory Messages",
          "description": "List of messages shown randomly or in sequence on the victory screen",
          "items": {
            "type": "string",
            "title": "Message",
            "description": "A single victory message string",
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
          "description": "Keys awarded each time the player answers a question correctly",
          "default": 0,
          "minimum": 0,
          "maximum": 10000
        },
        "keys_on_completion": {
          "type": "integer",
          "title": "Keys on Completion",
          "description": "Keys awarded upon completing all questions",
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
    "ui:title": "Game Content",
    "ui:description": "Define the questions and answer options the player will see",
    "questions": {
      "ui:title": "Questions",
      "ui:description": "Add one entry per question. Mark which answer option is correct using its zero-based index.",
      "items": {
        "ui:order": ["id", "question", "options", "correct_answer_index"],
        "id": {
          "ui:widget": "numberInput",
          "ui:placeholder": "1",
          "ui:help": "Unique numeric identifier for this question"
        },
        "question": {
          "ui:widget": "textInput",
          "ui:placeholder": "e.g. ¿Cuál es la capital de Colombia?",
          "ui:help": "The question text shown to the player"
        },
        "options": {
          "ui:title": "Answer Options",
          "ui:description": "Between 2 and 6 answer choices",
          "items": {
            "ui:widget": "textInput",
            "ui:placeholder": "e.g. Bogotá",
            "ui:help": "A single answer option"
          }
        },
        "correct_answer_index": {
          "ui:widget": "numberInput",
          "ui:placeholder": "0",
          "ui:help": "Zero-based index of the correct answer (0 = first option, 1 = second option, etc.)"
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
      "ui:help": "Sound played when the quiz is fully completed"
    }
  },
  "texts": {
    "ui:title": "UI Texts",
    "ui:description": "Messages displayed on the victory screen",
    "victory_messages": {
      "ui:title": "Victory Messages",
      "ui:description": "Add one or more messages to display when the player wins",
      "items": {
        "ui:widget": "textInput",
        "ui:placeholder": "e.g. ¡Respondiste todo correctamente!",
        "ui:help": "A message shown on the victory screen"
      }
    }
  },
  "rewards": {
    "ui:title": "Rewards",
    "ui:description": "Key reward values for player progression",
    "keys_per_action": {
      "ui:widget": "numberInput",
      "ui:placeholder": "0",
      "ui:help": "Keys granted each time the player answers correctly"
    },
    "keys_on_completion": {
      "ui:widget": "numberInput",
      "ui:placeholder": "0",
      "ui:help": "Keys granted upon completing all questions"
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
    WHERE game_id = 19 AND version = 1
);