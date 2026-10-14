-- =====================================================================
--  App de alimentación personal - Esquema MySQL 8.x
--  Ejecutar completo en MySQL Workbench (rayo "Execute all")
-- =====================================================================

CREATE DATABASE IF NOT EXISTS app_alimentacion
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE app_alimentacion;

-- ---------------------------------------------------------------------
-- 1. USUARIOS Y PERFIL
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS users (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username      VARCHAR(50)  NOT NULL,
  password_hash VARCHAR(255) NOT NULL,          -- hash (bcrypt/argon2), nunca la contraseña en texto
  display_name  VARCHAR(100) NULL,
  is_active     TINYINT(1)   NOT NULL DEFAULT 1,
  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_username (username)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_profiles (
  user_id    BIGINT UNSIGNED NOT NULL,
  birth_date DATE NULL,                          -- la edad se calcula, así no se desactualiza
  height_cm  DECIMAL(5,2) NULL,
  goal       ENUM('lose','maintain','gain') NULL, -- bajar, mantener, subir
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_profiles_user FOREIGN KEY (user_id)
    REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT ck_profiles_height CHECK (height_cm IS NULL OR height_cm > 0)
) ENGINE=InnoDB;

-- Registro de peso: un registro por día
CREATE TABLE IF NOT EXISTS weight_logs (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id    BIGINT UNSIGNED NOT NULL,
  log_date   DATE NOT NULL,
  weight_kg  DECIMAL(5,2) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_weight_user_date (user_id, log_date),
  CONSTRAINT fk_weight_user FOREIGN KEY (user_id)
    REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT ck_weight_positive CHECK (weight_kg > 0)
) ENGINE=InnoDB;

-- Metas diarias con historial: vale desde valid_from hasta que haya una más nueva
CREATE TABLE IF NOT EXISTS nutrition_goals (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id       BIGINT UNSIGNED NOT NULL,
  valid_from    DATE NOT NULL,
  calories_kcal DECIMAL(7,2) NOT NULL,
  protein_g     DECIMAL(7,2) NOT NULL,
  carbs_g       DECIMAL(7,2) NOT NULL,
  fat_g         DECIMAL(7,2) NULL,
  fiber_g       DECIMAL(7,2) NULL,
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_goals_user_from (user_id, valid_from),
  CONSTRAINT fk_goals_user FOREIGN KEY (user_id)
    REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT ck_goals_positive CHECK (calories_kcal > 0 AND protein_g >= 0 AND carbs_g >= 0)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 2. ALIMENTOS Y MEDIDAS CASERAS
-- ---------------------------------------------------------------------

-- Valores nutricionales SIEMPRE por 100 g (sólidos) o por 100 ml (líquidos).
-- fat/fiber/sugar/sodium quedan listos pero opcionales (NULL = no se sabe).
CREATE TABLE IF NOT EXISTS foods (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id       BIGINT UNSIGNED NOT NULL,
  name          VARCHAR(120) NOT NULL,
  unit_type     ENUM('g','ml') NOT NULL DEFAULT 'g',
  calories_kcal DECIMAL(7,2) NOT NULL DEFAULT 0,   -- por 100 g/ml
  protein_g     DECIMAL(7,2) NOT NULL DEFAULT 0,
  carbs_g       DECIMAL(7,2) NOT NULL DEFAULT 0,
  fat_g         DECIMAL(7,2) NULL,
  fiber_g       DECIMAL(7,2) NULL,
  sugar_g       DECIMAL(7,2) NULL,
  sodium_mg     DECIMAL(8,2) NULL,
  notes         VARCHAR(255) NULL,                 -- ej. "dato aproximado, de internet"
  is_archived   TINYINT(1) NOT NULL DEFAULT 0,     -- archivar en vez de borrar (protege el historial)
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_foods_user_name (user_id, name),
  KEY idx_foods_user_active (user_id, is_archived, name),
  CONSTRAINT fk_foods_user FOREIGN KEY (user_id)
    REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT ck_foods_nonneg CHECK (calories_kcal >= 0 AND protein_g >= 0 AND carbs_g >= 0)
) ENGINE=InnoDB;

-- Medidas caseras editables por alimento: "1 unidad = 50 g", "1 puño cerrado = 300 g"
-- base_amount está en g o ml según foods.unit_type. Siempre se puede registrar en g/ml directos.
CREATE TABLE IF NOT EXISTS food_measures (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  food_id     BIGINT UNSIGNED NOT NULL,
  name        VARCHAR(60) NOT NULL,
  base_amount DECIMAL(8,2) NOT NULL,
  is_default  TINYINT(1) NOT NULL DEFAULT 0,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_measures_food_name (food_id, name),
  CONSTRAINT fk_measures_food FOREIGN KEY (food_id)
    REFERENCES foods (id) ON DELETE CASCADE,
  CONSTRAINT ck_measures_amount CHECK (base_amount > 0)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 3. REGISTRO DE COMIDAS
-- ---------------------------------------------------------------------

-- user_id NULL = tipo global (desayuno, almuerzo, cena); con user_id = tipo creado por el usuario
CREATE TABLE IF NOT EXISTS meal_types (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id    BIGINT UNSIGNED NULL,
  name       VARCHAR(50) NOT NULL,
  sort_order SMALLINT NOT NULL DEFAULT 100,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_mealtypes_user_name (user_id, name),
  CONSTRAINT fk_mealtypes_user FOREIGN KEY (user_id)
    REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Un día por usuario, con sus notas generales
CREATE TABLE IF NOT EXISTS daily_logs (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id    BIGINT UNSIGNED NOT NULL,
  log_date   DATE NOT NULL,                         -- fecha local, sin zona horaria
  notes      TEXT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_daily_user_date (user_id, log_date),
  CONSTRAINT fk_daily_user FOREIGN KEY (user_id)
    REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Una comida concreta del día (puede haber varios snacks el mismo día)
CREATE TABLE IF NOT EXISTS meals (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  daily_log_id BIGINT UNSIGNED NOT NULL,
  meal_type_id BIGINT UNSIGNED NOT NULL,
  eaten_time   TIME NULL,                           -- aproximada si no la recuerda
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_meals_daily (daily_log_id, eaten_time),
  KEY idx_meals_type (meal_type_id),
  CONSTRAINT fk_meals_daily FOREIGN KEY (daily_log_id)
    REFERENCES daily_logs (id) ON DELETE CASCADE,
  CONSTRAINT fk_meals_type FOREIGN KEY (meal_type_id)
    REFERENCES meal_types (id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- Cada alimento comido. Los valores calculados se CONGELAN aquí:
-- si luego editas el alimento, el historial no cambia.
--   quantity / measure_name_snapshot : lo que el usuario escribió (ej. 4 "unidad")
--   amount_base                      : total en g o ml (ej. 4 x 50 = 200)
--   calories_kcal, protein_g, ...    : amount_base / 100 * valor_por_100 del alimento al momento
CREATE TABLE IF NOT EXISTS meal_items (
  id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  meal_id               BIGINT UNSIGNED NOT NULL,
  food_id               BIGINT UNSIGNED NOT NULL,
  measure_id            BIGINT UNSIGNED NULL,        -- NULL si se registró en g/ml directos
  quantity              DECIMAL(10,2) NOT NULL,
  measure_name_snapshot VARCHAR(60) NULL,            -- NULL si fue en g/ml directos
  amount_base           DECIMAL(10,2) NOT NULL,
  unit_type_snapshot    ENUM('g','ml') NOT NULL,
  food_name_snapshot    VARCHAR(120) NOT NULL,
  calories_kcal         DECIMAL(9,2) NOT NULL,
  protein_g             DECIMAL(9,2) NOT NULL,
  carbs_g               DECIMAL(9,2) NOT NULL,
  fat_g                 DECIMAL(9,2) NULL,
  fiber_g               DECIMAL(9,2) NULL,
  sugar_g               DECIMAL(9,2) NULL,
  sodium_mg             DECIMAL(10,2) NULL,
  created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_items_meal (meal_id),
  KEY idx_items_food (food_id),
  CONSTRAINT fk_items_meal FOREIGN KEY (meal_id)
    REFERENCES meals (id) ON DELETE CASCADE,
  CONSTRAINT fk_items_food FOREIGN KEY (food_id)
    REFERENCES foods (id) ON DELETE RESTRICT,
  CONSTRAINT fk_items_measure FOREIGN KEY (measure_id)
    REFERENCES food_measures (id) ON DELETE SET NULL,
  CONSTRAINT ck_items_positive CHECK (quantity > 0 AND amount_base > 0)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 4. VISTAS ÚTILES
-- ---------------------------------------------------------------------

-- Totales por día
CREATE OR REPLACE VIEW v_daily_totals AS
SELECT
  dl.user_id,
  dl.log_date,
  COALESCE(SUM(mi.calories_kcal), 0) AS calories_kcal,
  COALESCE(SUM(mi.protein_g), 0)     AS protein_g,
  COALESCE(SUM(mi.carbs_g), 0)       AS carbs_g,
  COALESCE(SUM(mi.fat_g), 0)         AS fat_g
FROM daily_logs dl
LEFT JOIN meals m       ON m.daily_log_id = dl.id
LEFT JOIN meal_items mi ON mi.meal_id = m.id
GROUP BY dl.id, dl.user_id, dl.log_date;

-- Totales del día contra la meta vigente ese día
CREATE OR REPLACE VIEW v_daily_vs_goal AS
SELECT
  t.user_id,
  t.log_date,
  t.calories_kcal, g.calories_kcal AS goal_calories_kcal,
  t.protein_g,     g.protein_g     AS goal_protein_g,
  t.carbs_g,       g.carbs_g       AS goal_carbs_g
FROM v_daily_totals t
LEFT JOIN nutrition_goals g
  ON g.user_id = t.user_id
 AND g.valid_from = (
       SELECT MAX(g2.valid_from)
       FROM nutrition_goals g2
       WHERE g2.user_id = t.user_id AND g2.valid_from <= t.log_date
     );

-- ---------------------------------------------------------------------
-- 5. DATOS INICIALES
-- ---------------------------------------------------------------------

-- Tipos de comida globales
INSERT INTO meal_types (user_id, name, sort_order)
SELECT NULL, v.name, v.sort_order
FROM (SELECT 'Desayuno' AS name, 1 AS sort_order
      UNION ALL SELECT 'Almuerzo', 2
      UNION ALL SELECT 'Cena', 3) v
WHERE NOT EXISTS (SELECT 1 FROM meal_types WHERE user_id IS NULL AND name = v.name);

-- Usuario de prueba (REEMPLAZA el hash por uno real generado desde tu backend)
INSERT INTO users (username, password_hash, display_name)
VALUES ('demo', 'REEMPLAZAR_CON_HASH_REAL', 'Demo')
ON DUPLICATE KEY UPDATE username = username;

SET @demo := (SELECT id FROM users WHERE username = 'demo');

-- Valores aproximados por 100 g (edítalos si encuentras datos más exactos)
INSERT INTO foods (user_id, name, unit_type, calories_kcal, protein_g, carbs_g, fat_g, notes)
VALUES
  (@demo, 'Huevo cocido',      'g', 155.00, 12.60,  1.10, 10.60, 'Aproximado'),
  (@demo, 'Arroz blanco cocido','g', 130.00,  2.70, 28.20,  0.30, 'Aproximado')
ON DUPLICATE KEY UPDATE name = name;

SET @huevo := (SELECT id FROM foods WHERE user_id = @demo AND name = 'Huevo cocido');
SET @arroz := (SELECT id FROM foods WHERE user_id = @demo AND name = 'Arroz blanco cocido');

INSERT INTO food_measures (food_id, name, base_amount, is_default)
VALUES
  (@huevo, 'Unidad',       50.00, 1),
  (@arroz, 'Puño cerrado', 300.00, 1)
ON DUPLICATE KEY UPDATE name = name;

-- ---------------------------------------------------------------------
-- EJEMPLO (comentado): cómo se guarda "4 huevos cocidos" en el desayuno
-- amount_base = 4 x 50 = 200 g  ->  kcal = 200/100 x 155 = 310
-- ---------------------------------------------------------------------
-- INSERT INTO daily_logs (user_id, log_date) VALUES (@demo, '2026-10-10');
-- INSERT INTO meals (daily_log_id, meal_type_id, eaten_time)
--   VALUES (LAST_INSERT_ID(),
--           (SELECT id FROM meal_types WHERE user_id IS NULL AND name = 'Desayuno'),
--           '08:00:00');
-- INSERT INTO meal_items
--   (meal_id, food_id, measure_id, quantity, measure_name_snapshot, amount_base,
--    unit_type_snapshot, food_name_snapshot, calories_kcal, protein_g, carbs_g, fat_g)
-- VALUES
--   (LAST_INSERT_ID(), @huevo,
--    (SELECT id FROM food_measures WHERE food_id = @huevo AND name = 'Unidad'),
--    4, 'Unidad', 200.00, 'g', 'Huevo cocido', 310.00, 25.20, 2.20, 21.20);
