# ARCHITECTURE.md

Fuente de verdad para el diseño y desarrollo de la aplicación web personal de alimentación.

---

## 1. Qué es la app
Una app web personal (por ahora de un solo usuario real, pero con login y multiusuario desde el diseño). El usuario:
- Crea sus propios alimentos, con calorías, proteínas y carbohidratos por cada 100 g (sólidos) o 100 ml (líquidos). También hay columnas opcionales para grasas, fibra, azúcar y sodio que por ahora no se usan.
- Define medidas caseras editables por alimento (ej. "Huevo cocido: 1 unidad = 50 g", "Arroz: 1 puño cerrado = 300 g").
- Registra cada día sus comidas: desayuno, almuerzo y cena (tipos globales fijos) más tipos adicionales que él mismo crea (snack, pre-entreno...). Cada comida tiene hora aproximada y varios alimentos con cantidad.
- Ve el total diario de kcal/proteínas/carbohidratos contra sus metas, y tiene notas generales por día.
- Guarda su perfil (fecha de nacimiento, altura, objetivo) y su peso con fecha.

**Ejemplo de registro:**
- Fecha: 10-10-2026
- Desayuno: 4 huevos cocidos, 1 maduro cocinado, 400 ml de jugo de manzana con azúcar.
- Almuerzo: 1 papa cocida, 1 puño cerrado de arroz, 100 g de menestra, 1 entrepierna de pollo asado.

---

## 2. Stack y entorno
- **Lenguaje y framework:** Maven, Java 17, Spring Boot 4.1.1, groupId `com.fitness`, artifact `fitness`, paquete base `com.fitness`, jar, configuración en `application.properties`.
- **Dependencias base:**
  - Spring Web (`spring-boot-starter-webmvc`)
  - Spring Data JPA (`spring-boot-starter-data-jpa`)
  - MySQL Driver (`com.mysql:mysql-connector-j`)
  - Spring Security (`spring-boot-starter-security`)
  - Validation (`spring-boot-starter-validation`)
  - Flyway (`spring-boot-starter-flyway` + `org.flywaydb:flyway-mysql`)
  - Tests (`spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-security-test`)
- **Particularidades Spring Boot 4:** Starters modularizados, autoconfiguración modular, Jackson 3, Hibernate 7 y Spring Security 7.
- **Base de datos:** MySQL 8.x, base `app_alimentacion` (utf8mb4).
- **Frontend futuro:** HTML/CSS/JS plano servido como recursos estáticos por el mismo Spring Boot (no es parte de la fase 1).
- **Convenciones:** Sin Lombok. DTOs como `record`. Inyección por constructor.

---

## 3. Convenciones de código
- **Idioma:** Todo en inglés: clases, métodos, variables, comentarios, mensajes de commit. Mensajes de error de la API en inglés.
- **Estructura de paquetes (por funcionalidad):**
  ```
  com.fitness
  ├── FitnessApplication.java
  ├── auth/      controller, service, dto, security
  ├── user/      entity, repository
  ├── food/      controller, service, repository, entity, dto   (fase 2)
  ├── meal/      controller, service, repository, entity, dto   (fase 3)
  ├── goal/ profile/ weight/                                    (fase 5)
  └── common/    config, exception, util
  ```
- **Capas:**
  - **Controller:** solo HTTP (validar entrada, llamar al service, devolver DTO).
  - **Service:** lógica de negocio y `@Transactional`.
  - **Repository:** Spring Data JPA.
  - **Entity:** nunca se expone en la API, siempre DTO.
- **API REST:** bajo `/api/...`, recursos en plural, JSON, códigos HTTP semánticos (201 en creación, 204 sin contenido, 400 validación, 401 no autenticado, 404 no existe o no es tuyo, 409 conflicto).
- **Validación:** Bean Validation en los DTOs de entrada.
- **Manejo de errores:** `@RestControllerAdvice` global que devuelve `ProblemDetail` (RFC 9457) con un campo `errors` para errores de validación por campo.
- **Precisión nutricional:** SIEMPRE `BigDecimal`, nunca `double`/`float`. Redondeo a 2 decimales con `RoundingMode.HALF_UP`.
- **Fechas:** `LocalDate` y `LocalTime` (sin zona horaria; el día es el día local del usuario). Campos de auditoría `created_at`/`updated_at` rellenados por BD (mapeados como solo lectura).
- **JPA & Flyway:** `spring.jpa.hibernate.ddl-auto=validate` (esquema gobernado por Flyway, JPA nunca lo modifica) y `spring.jpa.open-in-view=false`.
- **Credenciales:** Variables de entorno (`DB_URL` con valor por defecto `jdbc:mysql://localhost:3306/app_alimentacion`, `DB_USER` y `DB_PASSWORD` sin valor por defecto). Nunca contraseñas en el repositorio.

---

## 4. Base de datos
- **Fuente de verdad:** `alimentacion_schema.sql` (en raíz) y migraciones Flyway en `src/main/resources/db/migration`.
- **Inmutabilidad de migraciones:** NUNCA se edita una migración ya aplicada (`V1__init.sql`, cambios en `V2__...`, `V3__...`).
- **Tablas:**
  - `users`: usuarios y credenciales (bcrypt).
  - `user_profiles`: perfil físico (fecha nacimiento, altura, objetivo).
  - `weight_logs`: registro histórico de peso diario.
  - `nutrition_goals`: metas nutricionales históricas (`valid_from`).
  - `foods`: catálogo de alimentos por 100 g o 100 ml con soft-delete (`is_archived`).
  - `food_measures`: medidas caseras de porción por alimento.
  - `meal_types`: tipos de comida (globales con `user_id` nulo, custom con `user_id`).
  - `daily_logs`: registro por usuario y día con notas.
  - `meals`: eventos de comida asociados a un `daily_log` y `meal_type`.
  - `meal_items`: items individuales con snapshot congelado de nutrientes.
- **Vistas:**
  - `v_daily_totals`: agregación diaria de calorías, proteínas, carbohidratos y grasas.
  - `v_daily_vs_goal`: comparación de totales diarios contra la meta vigente en esa fecha.

---

## 5. Reglas de negocio (diseño transversal)

### Regla de Oro (Aislamiento por usuario)
> **REGLA DE ORO:** El `userId` sale SIEMPRE de la sesión (helper `SecurityUtils`), nunca del request. Todo acceso a datos de un usuario se hace por `id` Y `userId` (ej. `findByIdAndUserId`), jamás solo por `id`. Si el recurso no existe o es de otro usuario, responde 404 (`ResourceNotFoundException`). Para las medidas: primero se carga el alimento por `id` + `userId` (404 si no), y luego la medida por `id` + `foodId`.

1. **Aislamiento por usuario:** toda consulta se filtra por el `user_id` del usuario autenticado (obtenido de la sesión, NUNCA del request). Si un recurso existe pero pertenece a otro usuario, responder 404 (no revelar existencia).
2. **Valores nutricionales base:** siempre referidos a 100 g o 100 ml según `unit_type`.
3. **Cálculo nutricional (en backend exclusivamente):**
   - Si tiene medida: `amount_base = quantity × measure.base_amount`.
   - Si no tiene medida: `amount_base = quantity` (en g/ml directos).
   - Nutriente: `amount_base / 100 × valor_por_100`. Nutrientes opcionales NULL se mantienen NULL.
4. **Congelado del historial:** al guardar un `meal_item` se copian en snapshot el nombre del alimento, unidad, nombre de la medida y nutrientes calculados. Modificar un alimento en el catálogo NO altera registros pasados. Al editar un item, se recalcula con valores actuales del alimento.
5. **Archivado de alimentos:** los alimentos no se borran físicamente, se archivan (`is_archived = true`). Desaparecen de las opciones de registro pero protegen la integridad referencial (`RESTRICT` en `meal_items.food_id`). Un alimento archivado no aparece en el listado por defecto y en la fase 3 no se podrá usar para registrar comidas.
6. **Tipos de comida (`meal_types`):** `user_id` NULL identifica tipos globales inmutables (Desayuno, Almuerzo, Cena). Tipos con `user_id` son personalizados. Tipos en uso no pueden eliminarse.
7. **Días (`daily_logs`):** un registro por usuario y fecha. Soporta múltiples comidas del mismo tipo el mismo día. Se crea bajo demanda.
8. **Metas (`nutrition_goals`):** históricas con `valid_from`. La meta vigente para una fecha dada es la más reciente donde `valid_from <= log_date`.

---

## 6. Seguridad (decisión de arquitectura)
- **Sesión HTTP:** basada en cookie `JSESSIONID` (`HttpOnly`, `SameSite=Lax`, timeout de 7 días). Contraseñas cifradas con BCrypt.
- **Control de accesos Spring Security:**
  - Permitir sin autenticación únicamente `/api/auth/register`, `/api/auth/login` y recursos estáticos.
  - Todo lo demás requiere autenticación.
  - Sin `formLogin` ni `httpBasic`.
  - Peticiones no autenticadas a `/api/**` responden con error HTTP 401 en JSON (sin redirecciones).
- **CSRF:** Deshabilitado temporalmente (la API solo procesa JSON y la cookie cuenta con `SameSite=Lax`; pensado para pruebas iniciales con Postman).
  > **Nota de seguridad:** Pendiente de revisar e implementar protección CSRF adecuada (ej. token CSRF) antes de exponer la app públicamente en producción.
- **Persistencia de sesión manual:** En Spring Security 6/7, el `SecurityContext` no se persiste automáticamente en la sesión tras autenticación manual vía controller; se utiliza explícitamente `HttpSessionSecurityContextRepository`.
- **Identidad del usuario:** El principal expone el `id` del usuario (clase personalizada que implementa `UserDetails`). Helper en `common` o `auth` para resolver el `userId` actual de forma tipada en la capa de servicios.
- **Mensajes de autenticación:** No revelar si falló el nombre de usuario o la contraseña (mensaje genérico). Usuarios con `is_active = false` tienen el inicio de sesión bloqueado.

---

## 7. Módulo de Alimentos y Medidas (Fase 2)

### Endpoints de Alimentos
- `GET /api/foods?q=&includeArchived=false`: Lista alimentos del usuario autenticado ordenados alfabéticamente por nombre (`ASC`). Admite filtro opcional `q` (contiene texto, insensible a mayúsculas y acentos gracias a `utf8mb4_0900_ai_ci`). Por defecto excluye archivados a menos que `includeArchived=true`. Incluye las medidas asociadas sin provocar N+1 queries.
- `GET /api/foods/{id}`: Obtiene el alimento del usuario con sus medidas (incluso si está archivado).
- `POST /api/foods`: Crea un alimento (HTTP 201 + header `Location`). Permite registrar medidas opcionales anidadas en el payload en una única transacción.
- `PUT /api/foods/{id}`: Actualiza nombre, nutrientes y notas del alimento. `unitType` no puede ser modificado. Las medidas no se alteran por este endpoint.
- `POST /api/foods/{id}/archive`: Archiva el alimento (`is_archived = true`). Operación idempotente.
- `POST /api/foods/{id}/unarchive`: Desarchiva el alimento (`is_archived = false`). Operación idempotente.
> **Regla de integridad:** No existe eliminación física (`DELETE`) de alimentos.

### Endpoints de Medidas Caseras (`/api/foods/{foodId}/measures`)
- `GET /api/foods/{foodId}/measures`: Lista medidas del alimento, ordenadas con la predeterminada (`isDefault=true`) primero y luego alfabéticamente por nombre.
- `POST /api/foods/{foodId}/measures`: Agrega una medida (HTTP 201 + header `Location`).
- `PUT /api/foods/{foodId}/measures/{measureId}`: Modifica nombre, cantidad base o estado default.
- `DELETE /api/foods/{foodId}/measures/{measureId}`: Eliminación física de la medida (HTTP 204). Si se borra la medida default, el alimento queda sin default (no se autoselecciona otra).

### Reglas de Negocio de Alimentos y Medidas
1. **Unicidad de nombre de alimento:** Único por usuario, considerando activos y archivados. Si colisiona con uno archivado, responde 409 sugiriendo desarchivarlo. En edición se excluye el propio id.
2. **Unicidad de nombre de medida:** Único por alimento (409 en colisión).
3. **Gestión de medida default:**
   - La primera medida registrada en un alimento queda automáticamente como `isDefault=true`.
   - Si una medida se marca como `isDefault=true`, cualquier otra medida default de ese alimento pasa a `false`.
   - En creación anidada con múltiples medidas: máximo una puede venir con `isDefault=true` (si hay más, error 400); si ninguna lo trae, la primera de la lista asume el rol default.
   - Si se elimina la medida default, el alimento queda sin default.
4. **Independencia del historial:** Modificar o eliminar medidas no afecta a `meal_items` preexistentes (guardan snapshots congelados; FK con `ON DELETE SET NULL`).

### Validaciones de Entrada
- `name` (alimento): Obligatorio, 1 a 120 caracteres, trimmed.
- `unitType`: Obligatorio (`g` o `ml`). Entrada no válida da 400 Bad Request.
- `caloriesKcal`: Obligatorio, entre 0 y 900, máx 2 decimales.
- `proteinG`, `carbsG`: Obligatorios, entre 0 y 100, máx 2 decimales.
- `fatG`, `fiberG`, `sugarG`: Opcionales, entre 0 y 100, máx 2 decimales.
- `sodiumMg`: Opcional, entre 0 y 40000, máx 2 decimales.
- `notes`: Opcional, máx 255 caracteres, trimmed.
- `name` (medida): Obligatorio, 1 a 60 caracteres, trimmed.
- `baseAmount`: Obligatorio, entre 0.01 y 10000, máx 2 decimales.

---

## 8. Roadmap de implementación
- [x] **Fase 1:** Base del proyecto, Flyway, registro, login, logout, me, configuración de seguridad y manejo global de excepciones.
- [x] **Fase 2:** Alimentos (`/api/foods`, archivar) y medidas (`/api/foods/{id}/measures`) con CRUD, filtrados por usuario.
- [ ] **Fase 3:** Tipos de comida (`/api/meal-types`), registro de días y comidas (`GET /api/days/{date}`, `POST /api/days/{date}/meals`, edición y borrado de comidas/items) con cálculo y snapshot.
- [ ] **Fase 4:** Resumen diario contra meta (`/api/days/{date}/summary`, rango de fechas) y notas del día.
- [ ] **Fase 5:** Metas nutricionales, perfil y registro de peso.
- [ ] **Fase 6:** Pruebas de integración con Testcontainers y pulido final.

---

## 9. Estrategia de pruebas
- Pruebas unitarias con JUnit 5 + Mockito para la lógica de negocio de los servicios.
- Fase 1 y 2 sin Testcontainers (no se asume Docker en el entorno local).
