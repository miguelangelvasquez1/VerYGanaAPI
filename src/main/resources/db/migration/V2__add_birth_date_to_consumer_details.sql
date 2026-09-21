ALTER TABLE consumer_details ADD COLUMN birth_date DATE NULL;

UPDATE consumer_details cd
JOIN user_details ud ON cd.user_id = ud.user_id
JOIN users u ON ud.user_id = u.id
SET cd.birth_date = DATE_SUB(DATE(u.registered_date), INTERVAL cd.age YEAR)
WHERE cd.age IS NOT NULL AND u.registered_date IS NOT NULL;

-- Datos de prueba sin edad/registro utilizable: valor fijo de relleno.
UPDATE consumer_details SET birth_date = '2000-01-01' WHERE birth_date IS NULL;

ALTER TABLE consumer_details MODIFY COLUMN birth_date DATE NOT NULL;

ALTER TABLE consumer_details DROP COLUMN age;
