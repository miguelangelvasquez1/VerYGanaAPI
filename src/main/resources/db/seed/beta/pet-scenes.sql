-- ============================================================================
-- Escenas de mascotas: objetos por escena (SCRUM-88, por ahora solo escenas)
-- ============================================================================
-- Lo que el juego descarga de /pet/scenes-objects. Cada objeto guarda su
-- object_key en el bucket de mascotas (cloudflare.r2.pets-bucket-name), no la
-- URL: PetAssetUrlResolver la arma al responder. Por eso los archivos tienen que
-- estar subidos con esa misma clave, o el juego recibe un 404 y el objeto no sale.
-- Origen de los archivos: ~/Desktop/PetVirtual_Assets.
--
-- Coordenadas: lienzo de 1920 de ancho, origen abajo a la izquierda con Y hacia
-- arriba, x/y en el centro del objeto.
--
-- sceneId: -1 dormitorio, 0 sala, 1 cocina, 2 baño.
--
-- "ESPEJO MEDICO" conserva el espacio en object_id, que es lo que lee el juego,
-- pero no en la clave: el resolver no codifica la URL y un espacio en ella falla
-- según quién haga la petición.
--
-- Idempotente y sin pisar al diseñador: una escena se crea solo si no existe, y
-- recibe estos objetos solo si está vacía. Si el diseñador ya armó una escena
-- desde el panel, este archivo no la toca.
-- ============================================================================

DROP TEMPORARY TABLE IF EXISTS tmp_seed_scene_objects;

CREATE TEMPORARY TABLE tmp_seed_scene_objects (
    scene_number INT          NOT NULL,
    object_id    VARCHAR(100) NOT NULL,
    type         VARCHAR(20)  NOT NULL,
    object_key   VARCHAR(255) NOT NULL,
    x            INT          NOT NULL,
    y            INT          NOT NULL,
    width        INT          NOT NULL,
    height       INT          NOT NULL
);

INSERT INTO tmp_seed_scene_objects (scene_number, object_id, type, object_key, x, y, width, height) VALUES
    (-1, 'ALFOMBRA1',     'image', 'scene-objects/ALFOMBRA1.png',      952,  85, 503, 190),
    (-1, 'CAMA2',         'image', 'scene-objects/CAMA2.png',          958, 158, 516, 212),
    (-1, 'ARMARIO',       'image', 'scene-objects/ARMARIO.png',        361, 382, 370, 529),
    (-1, 'ESPEJO',        'image', 'scene-objects/ESPEJO.png',        1441, 350, 247, 449),
    (-1, 'LINTERNA',      'image', 'scene-objects/LINTERNA.png',      1673, 391, 178, 531),
    ( 0, 'LINTERNA1',     'image', 'scene-objects/LINTERNA1.png',     1543, 458, 197, 611),
    ( 0, 'ALFOMBRA',      'image', 'scene-objects/ALFOMBRA.png',       960, 108, 845, 183),
    ( 0, 'SALA',          'image', 'scene-objects/SALA.png',           960, 414, 651, 561),
    ( 0, 'video_3',       'video', 'scene-objects/video_3.mp4',        962, 543, 592, 252),
    ( 1, 'NEVERA',        'image', 'scene-objects/NEVERA.png',        1079, 470, 727, 640),
    ( 1, 'COCINA',        'image', 'scene-objects/COCINA.png',         603, 356, 379, 409),
    ( 1, 'HORNO',         'image', 'scene-objects/HORNO.png',          925, 305, 397, 356),
    ( 2, 'ESPEJO1',       'image', 'scene-objects/ESPEJO1.png',       1162, 660, 451, 391),
    ( 2, 'BANO',          'image', 'scene-objects/BANO.png',           964, 365, 923, 461),
    ( 2, 'ESPEJO MEDICO', 'image', 'scene-objects/ESPEJO_MEDICO.png',  760, 640, 356, 435);

-- 1. Crear las escenas que falten. pet_scenes.scene_id no tiene índice único, así
--    que se deduplica con NOT EXISTS.
INSERT INTO pet_scenes (scene_id, active)
SELECT DISTINCT t.scene_number, b'1'
FROM tmp_seed_scene_objects t
WHERE NOT EXISTS (SELECT 1 FROM pet_scenes s WHERE s.scene_id = t.scene_number);

-- 2. Llenar solo las escenas vacías. Si hubiera dos filas con el mismo scene_id
--    (pasa en bases viejas de dev), se usa la más antigua, igual que el resto de seeds.
INSERT INTO pet_scene_objects (scene_id, object_id, type, object_key, x, y, width, height, scale_multiplier)
SELECT target.id, t.object_id, t.type, t.object_key, t.x, t.y, t.width, t.height, 1.0
FROM tmp_seed_scene_objects t
JOIN (SELECT scene_id AS scene_number, MIN(id) AS id FROM pet_scenes GROUP BY scene_id) target
  ON target.scene_number = t.scene_number
WHERE NOT EXISTS (SELECT 1 FROM pet_scene_objects o WHERE o.scene_id = target.id);

DROP TEMPORARY TABLE IF EXISTS tmp_seed_scene_objects;
