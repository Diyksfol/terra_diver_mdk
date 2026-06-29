# Terra Diver — Copilot Instructions

## Проект

Minecraft-мод **Create: Terra Diver** для NeoForge 1.21.1.
Аддон для Create + Create Aeronautics (Simulated) + Sable.
Физичная штуковина (contraption) с буровой короной: летит, пикирует носом в породу, уходит под землю, всплывает.

Главный источник истины по спецификациям: **TD_06 v1.0** (файл в репозитории).
При любом противоречии между этими инструкциями и TD_06 — TD_06 имеет приоритет.

---

## Язык и стиль кода

- Язык кода: **Java**
- Комментарии в коде: **русский**
- Логи и исключения: **английский** (стандарт Java-экосистемы)
- Стиль: стандартный Java (camelCase для методов/переменных, PascalCase для классов)
- Пакет: `com.example.terradiver`

---

## Правила генерации

**Одна функция за раз.** Не генерировать несколько функций в одном ответе если не попросили явно.

**Сигнатуры — строго из TD_06 v1.0.** Не угадывать параметры, не добавлять «удобные» перегрузки. Если сигнатура не предоставлена в промпте — спросить, не выдумывать.

**Неподтверждённые API — явные TODO.** Если метод Sable/Aeronautics не входит в список подтверждённых ниже — писать `// TODO: verify - [название метода]` и использовать заглушку. Не угадывать API по аналогии.

**Балансовые константы — только через конфиг.** Никаких магических чисел. Все числовые константы берутся из `TerrraDiverConfig.[КОНСТАНТА]`. Не хардкодить даже «временно».

**Граничные случаи — реализовывать явно.** Если в промпте переданы граничные случаи из TD_06 — каждый должен быть покрыт в коде, не подразумеваться.

---

## Подтверждённые API (использовать без TODO)

```java
// Sable / Aeronautics
RigidBodyHandle.applyLinearImpulse(Vec3 impulse)   // линейный импульс на тело
KinematicContraption.sable$getOrientation()         // кватернион ориентации штуковины
ForgeSablePrePhysicsTickEvent                        // хук тик-цикла физики

// Create: Avionics
AltitudeSensorBlockEntity.getWorldHeight()          // абсолютный мировой Y (без потолка)
velocity_sensor.getVelocity()                       // вектор скорости
gimbal_sensor.getAngles()                           // углы крена/тангажа/курса (чтение)

// Create: Offroad
offroad:borehead_bearing.getSpeed()                 // знак = CW(+)/CCW(-), величина = RPM
// boreheadBearingSearchRadius default 1.5
// boreheadBearingRotationDivisor default 4.0
// boreheadBearingStallingEnabled = true
// boreheadBearingStallRecoveryTicks = 10
```

---

## Неподтверждённые API (писать TODO)

```java
subLevel.getLevel().getBlockState(worldPos)   // TODO: verify - доступ к мировым блокам из SubLevel
RigidBodyHandle.applyAngularImpulse(...)       // TODO: verify - угловой импульс (фиксация крена)
RigidBodyHandle.getMass()                      // TODO: verify - масса тела для apply_drilling_velocity
// UUID в ItemStack при дропе блока           // TODO: verify - manage_saved_data_lifecycle
```

---

## Архитектура (граф зависимостей)

Функции реализуются строго снизу вверх:

```
Домен 0 (примитивы, нет зависимостей):
  get_heading, is_diggable, get_aligned_crowns,
  check_crown_rotation_consistency, project_crown_front

TD_03 (давление, рёбра):
  compute_raw_debuff, invalidate_hull_cache, compute_exterior_surface,
  find_valid_girder_lines, compute_rib_coverage,
  compute_pressure_debuff_effective, compute_ambient_signal_volume

TD_02 (буровая система):
  compute_max_blocks_per_tick, buffer_has_space, deposit_to_inventory,
  decay_pending_resistance, is_bedrock_blocking,
  compute_crown_face_area, compute_avg_material_factor,
  clear_blocks, update_pending_resistance, compute_drilling_rate,
  compute_pushback
  [compute_reverse_rate — УСТАРЕЛА, не реализовывать]

TD_01 (физика):
  is_dive_mode_active, apply_drilling_velocity,
  lock_roll_for_segment, handle_reverse_state,
  ensure_signal_active, ensure_signal_stopped

TD_04 (навигация, только читает):
  scan_sonar_grid, classify_block_for_sonar, render_sonar_sweep,
  compute_bore_compass, compute_pressure_gauge_needles,
  project_isometric, sort_painters_order,
  manage_saved_data_lifecycle, render_map_background_zones
```

---

## Тик-цикл (единственная точка правды — TD_06 v1.0)

```
Каждый тик (ForgeSablePrePhysicsTickEvent):
  heading = get_heading()
  rotation = check_crown_rotation_consistency()
  dive_active = is_dive_mode_active()

  ВЕТКА A (dive_active == true):
    max_blocks = compute_max_blocks_per_tick()   // из forward_rate ПРОШЛОГО тика
    processed = clear_blocks(max_blocks)
    pending_resistance = update_pending_resistance(found, processed)
    forward_rate = compute_drilling_rate(...)

  ВЕТКА B (dive_active == false && pending_resistance > 0):
    pending_resistance = decay_pending_resistance()
    forward_rate = 0

  // Ветки A и B ВЗАИМОИСКЛЮЧАЮЩИЕ — decay не вызывать в ветке A

  ОБЩИЙ ХВОСТ (если pending_resistance > 0 или dive_active):
    pushback = compute_pushback(pending_resistance)
    net_rate = clamp(forward_rate - pushback, -RATE_MAX, RATE_MAX)
    if net_rate != 0:
      apply_drilling_velocity(net_rate, heading, mass, dive_active)

  if Y <= DEEPSLATE_Y && dive_active:
    ensure_signal_active(...)
  else:
    ensure_signal_stopped(...)
```

`apply_drilling_velocity` вызывается **на верхнем уровне**, не внутри ветки бурения.

---

## Критичные граничные случаи (не выводятся из сигнатур)

**`apply_drilling_velocity` — два режима:**
- `dive_active == true`: режим «задать скорость» — lerp текущей скорости к целевой вдоль heading
- `dive_active == false` (выталкивание): режим «добавить толчок» — lerp **только продольной компоненты**, поперечные и вертикальные составляющие не трогать → гравитация продолжает действовать, штуковина выходит по дуге, не строго назад

**`compute_exterior_surface` — flood-fill строго в локальной системе SubLevel**, не в мировых координатах.

**`compute_drilling_rate` — не содержит PUSHBACK_THRESHOLD/PUSHBACK_FACTOR**, они в отдельной `compute_pushback()`.

**`is_diggable` — горячая функция**, использовать кэш `Map<BlockState, Boolean>`.

**`compute_raw_debuff`:**
- Y=8 (DEEPSLATE_Y) → 1.0
- Y≈−15 → ≈0.5
- Y=−60 (BEDROCK_Y) → ≈0.05, clamp → 0.1 (нижняя граница у `pressure_debuff_effective`)

---

## Структура пакетов

```
com.example.terradiver
├── block/          — Block-классы
├── blockentity/    — BlockEntity-классы
├── item/           — Item-классы
├── physics/        — тик-цикл, apply_drilling_velocity, compute_pushback
├── pressure/       — TD_03: давление, рёбра жёсткости
├── navigation/     — TD_04: приборы, зонд, карта
├── registry/       — DeferredRegister для блоков, предметов, звуков, BlockEntity
├── datagen/        — DataGen: рецепты, модели, loot tables
├── client/         — рендер (BlockEntityRenderer, GUI)
└── config/         — TerrraDiverConfig (NeoForge TOML)
```

---

## Registry IDs (финальные)

```
terra_diver:drill_crown_copper
terra_diver:drill_crown_iron
terra_diver:drill_crown_brass
terra_diver:drill_crown_netherite
terra_diver:crown_bearing_andesite
terra_diver:crown_bearing_sturdy      ← НЕ brass (тир 2 = Прочное/Sturdy)
terra_diver:auger_shaft
terra_diver:pressure_gauge
terra_diver:seismic_probe
terra_diver:cartograph_console
terra_diver:piezo_element             ← предмет
terra_diver:drill_module              ← предмет
```

---

## Зависимости (gradle)

```groovy
// Create
modImplementation "com.simibubi.create:create-${minecraft_version}:${create_version}"
// Create Aeronautics Simulated
modImplementation "com.rabbitminers.aeronautics:aeronautics-simulated-${minecraft_version}:${aeronautics_version}"
// Sable (bundled с Aeronautics, но явная зависимость для компиляции)
compileOnly "sable:sable-${minecraft_version}:${sable_version}"
// Ponder
modImplementation "net.createmod.ponder:ponder-neoforge:${ponder_version}+mc${minecraft_version}"
```

---

## Уроки реализации TD_03/TD_02 (против галлюцинаций)

### Главный паттерн: чистое ядро + тонкий MC-адаптер
Каждую функцию, которой нужны данные мира, резать на две части:
- **Чистое ядро** — принимает обычные данные (`BlockPos`, `Axis`, числа, коллекции), без `Level`/`SubLevel`/живого мира. Сюда вся логика. Покрывается юнит-тестом.
- **MC-адаптер** — единственное место, где читается мир. Тонкий. Помечен `// TODO[API-CHECK]`. Проверяется в игре, не юнит-тестом.

Не тянуться к `Level` из логики — **передавать уже извлечённые данные на вход**. Пример: `compute_exterior_surface(Set<BlockPos> solidFullCubes)`, а извлечение solid-блоков из SubLevel — отдельный стаб-адаптер.

### Сёма для неподтверждённого API (обязательно)
Неподтверждённый MC-вызов прятать за `@FunctionalInterface` + перегрузку: production-метод с дефолтным провайдером, и метод с явным провайдером для тестов. Образцы уже в коде: `IBearingSpeedProvider`, `IWorldBlockReader` (PhysicsUtils), `CrownBuffer` (DrillingUtils). НЕ вызывать неподтверждённый API напрямую из логики.

### Конвенции, не угадывать
- **Твёрдость блока**: `block.getBlock().defaultDestroyTime()` (Bedrock = -1). НИКОГДА `getDestroySpeed(level,pos)` с null — NPE.
- **Жидкость**: `!block.getFluidState().isEmpty()`. Класс `Material` в 1.21 удалён — `getMaterial()` не существует.
- **Кватернион**: `org.joml.Quaternionf`. Старый `com.simibubi.create...Quaternion` удалён ещё в 1.19 — не использовать.
- **Стекинг инвентаря НЕ реимплементировать** — делегировать движку (`IItemHandler`), обернув в сёму. Свой симулятор стекинга запрещён (ломается на обновлениях).
- **Тип пары позиция+состояние**: `PhysicsUtils.BlockStateAtPos` (record), не Tuple.

### Тесты
- Юнит-тесты гоняются БЕЗ Minecraft — никакого `Bootstrap`, реестров, реальных `BlockState`/`ItemStack`.
- `BlockPos`, `Direction`, `Direction.Axis`, `Vec3`, `Quaternionf` — грузятся в тестах, использовать можно.
- `BlockState`/`Block`/`ItemStack` — НЕ конструировать; мокать через Mockito (`mockito-inline` есть, финальный `ItemStack` мокается).
- Если для проверки функции нужен живой мир (инвентарь, чтение блоков SubLevel) — это GameTest, не юнит. Не делать вид, что юнит покрывает MC-вставку.

### Результаты — неизменяемые
Возвращать `Set.copyOf`/`List.copyOf`. Кэш-поля корпуса — `volatile`, обновлять атомарно в конце метода.

### Комментарии
Plain-текст, без Javadoc-HTML (`<p>`, `<ul>`, `<li>`, `<b>`). Не пересказывать TD_06 — ссылаться: `см. TD_06 v1.0, <функция>`. Оставлять только то, чего в TD_06 нет (отступления от спеки, причины неочевидных решений).

### Пакеты
`physics/` — Domain 0 + TD_01. `pressure/` — TD_03. `drilling/` — TD_02. `navigation/` — TD_04. Имя файла обязано точно совпадать с именем public-класса (регистр!).

### Каждой функции — заголовок
Один промпт = одна функция. Сигнатура строго из TD_06 v1.0. Сверять граничные случаи с буквальным текстом TD_06 до принятия.