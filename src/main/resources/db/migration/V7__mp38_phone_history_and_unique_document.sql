-- Índice para búsqueda rápida y control de posible duplicidad (MP-38)
CREATE INDEX idx_consumer_details_document ON consumer_details(document_number);

-- Historial de teléfonos para proteger beneficios y evitar robos por números reciclados
CREATE TABLE phone_number_history (
                                      id BIGINT NOT NULL AUTO_INCREMENT,
                                      user_id BIGINT NOT NULL,
                                      phone_number VARCHAR(20) NOT NULL,
                                      assigned_at DATETIME(6) NOT NULL,
                                      detached_at DATETIME(6),
                                      PRIMARY KEY (id),
                                      CONSTRAINT fk_phone_history_user
                                          FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO phone_number_history (user_id, phone_number, assigned_at)
SELECT id, phone_number, COALESCE(registered_date, CURRENT_TIMESTAMP(6))
FROM users;

CREATE INDEX idx_phone_history_number
    ON phone_number_history(phone_number);

CREATE INDEX idx_phone_history_user
    ON phone_number_history(user_id);