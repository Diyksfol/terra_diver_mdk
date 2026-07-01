# Create: Terra Diver — Аннотированный список файлов (для ориентировки LLM)
_Ветка `test` · корень пакета `com.example.terradiver`_
_Обновлять при изменении структуры. Точные сигнатуры — дочитывать по raw-ссылке файла._

Легенда: **[V]** — содержимое проверено/написано в этой линии работы; **[S]** — описание по
спецификации TD_06 (роль верна, сигнатуры уточнять в файле).

---

## Вход и конфиг
- **TerraDiver.java** — [S] главный класс мода (`@Mod`). Регистрирует DeferredRegister'ы
  (блоки/предметы/BE/вкладки), конфиг, подписки на шины событий.
- **config/ModConfig.java** — [V-косв] конфиг-константы. Точно используются: `DEEPSLATE_Y`,
  `BEDROCK_Y`(≈-60), `PRESSURE_K`, `PRESSURE_SCALE` (кривая давления), `SUPPORT_STEP` (затухание
  поддержки балок). Плюс пороги бурения/навигации.

## Клиент (`client/`)
- **DrillClientExtensions.java** — [V] рендер буровой короны в руках. Поза рук `DRILL_LIFT`
  (`onRegisterClientExtensions`), бур над головой в 3-м лице (`onRenderPlayerPost`,
  `RenderPlayerEvent.Post`) и в 1-м лице (`onRenderLevelStage`, `RenderLevelStageEvent.AFTER_ENTITIES`,
  мировые координаты, только `isFirstPerson`). Константы `SCALE=0.5`, `CENTER_X=-0.5+0.5=0`,
  над головой `bbHeight+0.4`, поворот `YP(180-bodyYaw)`. 1x1 исключён (держится как обычный блок).
- **DrillOffhandLock.java** — [V] `PlayerTickEvent.Post` (сервер): чистит вторую руку, если в
  основной бур крупнее 1x1. Ловит F-свап (дополняет SlotMixin).
- **TerraDiverArmPoses.java** — [V] `EnumProxy<HumanoidModel.ArmPose>` `DRILL_LIFT` — обе руки
  строго вверх (xRot≈-3.05, zRot=±0.05). Регистрируется через enumextensions.json.
- **ClientEvents.java** — [S] прочие клиентские подписки. ЗАДАЧА: убрать deprecated
  `bus = EventBusSubscriber.Bus.MOD`.

## Миксины (`mixin/`)
- **LevelRendererMixin.java** — [V] client. @Inject HEAD в `destroyBlockProgress(int,BlockPos,int)`:
  зеркалит прогресс трещин на мастер с ОТРИЦАТЕЛЬНЫМ breakerId `-(id+1)` (иначе коллизия в
  destroyingBlocks). Трещины по всей короне.
- **SlotMixin.java** — [V] common. @Inject HEAD cancellable в `Slot.mayPlace`: для SLOT_OFFHAND(40)
  запрещает (1) сам бур >1x1 во вторую руку всегда, (2) любой предмет пока бур в основной руке.
  helper `isLiftedDrill`.

## Мультиблок-корона (`physics/`, наша недавняя работа)
- **DrillCrownStructure.java** — [V] раскладка коллизии, данные СТРОКАМИ (иначе `<clinit>`>64КБ).
  Профиль = измеренный плоский диск 2 блока + скос кромки, зубцы исключены (R=N/2), октант при
  ≥6/8 углов внутри. Счётчики 1/10/30/78/128/194. Методы: `cells`, `depthLayers`, `cellShapeBoxes`,
  `rotate(Direction)`, `worldCells`. Мастер (0,0,0) первый.
- **CrownShapes.java** — [V] сборка `VoxelShape` из боксов ячейки (`cellShapeBoxes`), повёрнутых
  по FACING вокруг центра ячейки. Кэшируется в part-BE.
- **DrillCrownBlock.java** — [S/V] мастер-блок короны. `crownSize()` (напр. "3x3"), форма из
  структуры, рендер OBJ-модели всей короны. `noOcclusion` + `explosionResistance` из BlockRegistry.
- **DrillCrownItem.java** — [V] BlockItem короны. `getBlock()` → DrillCrownBlock (для crownSize).
  Ставит мультиблок при размещении.
- **DrillCrownPartBlock.java** — [S/V] невидимый блок-часть. Форма/коллизия берётся из part-BE
  (октанты ячейки). Ломается вместе с мастером.
- **DrillCrownPartBlockEntity.java** — [V] BE части. КРИТИЧНО: `onDataPacket()` →
  `loadAdditional(tag)` + сброс `cachedShape` (иначе клиент без формы/мастера). `getMaster()`→BlockPos,
  getUpdateTag/getUpdatePacket/sendBlockUpdated.
- **DrillCrownMultiblock.java** — [S] логика сборки/разборки мультиблока: расстановка частей по
  `worldCells(size,facing,master)`, связывание с мастером, снос всей короны. (Зона доработки под
  Create-совместимость #8.)

## Физика — Домен 0 и ядра (`physics/`, TD_06)
- **PhysicsUtils.java** — [V] примитивы Домена 0. `get_heading(Quaternionf)` (нос, не скорость),
  `is_diggable(BlockState)` (hardness≥0 и не жидкость; через `defaultDestroyTime`),
  `get_aligned_crowns`, `check_crown_rotation_consistency` (+overload с провайдером, знак CW/CCW),
  `project_crown_front` (+overload, узкая линия для Bedrock-гейта). Внутр.: IWorldBlockReader,
  BlockStateAtPos. TODO[API]: bearing/subLevel.
- **CrownBlock.java** — [V] дата-класс короны: position, face, materialFactor[1..3], area,
  depthAlongHeading, bearingReference. `getFaceVector`, `isAlignedWithHeading` (dot>0.7).
- **IBearingSpeedProvider.java** — [S] интерфейс `getSpeed(bearing)` (тип bearing зависит от Offroad).
- **ResistanceField.java** — [S] аккумулятор `pending_resistance` («кисель»): накопление/спад.
  `decay_pending_resistance()` вне Dive Mode. См. TD_06 (compute_pushback вне Dive Mode).
- **TickCycle.java** — [S] оркестрация физического тика: порядок вызовов доменов за тик
  (heading → сопротивление → бурение → давление → навигация). Точка, куда заводится bearing BE.
- **DriveContextAdapter.java** — [S] адаптер контекста привода: сенсоры из Offroad/Sable
  (скорость подшипника, ориентация, высота). TODO[API-CHECK] на реальные сигнатуры.
- **RollLock.java** — [S] стабилизация: подавление крена (roll) контрапции.
- **AmbientSignal.java** — [S] громкость фонового сигнала среды (`compute_ambient_signal_volume`,
  TD_03/навигация) — зависит от корпуса/давления.

## Бурение (`drilling/`, TD_02, наши решения из дизайна)
- **DrillMode.java** — [S] enum режимов: продвижение (CW) vs «бур без движения» (CCW — карман
  вокруг венца). См. TD_06 (переосмысление CCW).
- **DrillModeDetector.java** — [S] определение режима по согласованности вращения/знаку подшипника
  (использует `check_crown_rotation_consistency`).
- **DrillingRate.java** — [S] расчёт скорости бурения из materialFactor, площади венца, скорости.
- **DrillingUtils.java** — [S] ядро приложения бурения: `apply_drilling_velocity()` (два режима:
  set-velocity vs add-impulse через dot-product, чтобы сохранить гравитацию), `compute_pushback()`.
- **BlockClearer.java** — [S] удаление/очистка выбуренных блоков мира по фронту продвижения.

## Давление/корпус (`pressure/`, TD_03)
- **PressureUtils.java** — [V] `compute_raw_debuff(y, deepslateY, bedrockY, k, scale)` —
  логарифмическая кривая давления, кламп [0,1]. Stateless.
- **HullCache.java** — [V] кэш корпуса: exteriorSurface, validGirderLines, ribCoverage.
  `invalidate_hull_cache(subLevel, supportStep)` — атомарный пересчёт в строгом порядке
  (поверхность→балки→покрытие). Заглушки compute* — TODO[IMPL] TD_03.
- **GirderLine.java** — [S] тип линии балки жёсткости (для rib_coverage).

## Регистры (`registry/`)
- **BlockRegistry.java** — [V] регистрация блоков. props(): `explosionResistance(1200)` (переживают
  TNT), мастер-короны `noOcclusion()`. Буры 6 размеров + части.
- **BlockEntityRegistry.java** — [S] регистрация BE (DrillCrownPartBlockEntity и др.).
- **ItemRegistry.java** — [S] регистрация предметов (DrillCrownItem по размерам).
- **CreativeTabs.java** — [S] креативная вкладка мода.

## Тесты (`src/test/…`) — JUnit на чистые ядра, без моков MC
- **physics/DrillCrownStructureTest.java** — [V] счётчики (3x3=10,5x5=30,11x11=194), rotationBijective
  (seen=194), masterIsFirst (3x3=10), горизонтальная расстановка (глубина по FACING).
- **physics/PhysicsUtilsTest.java**, **CrownBlockTest.java** — [S] Домен 0 (heading, diggable,
  выравнивание, согласованность вращения через мок-провайдер).
- **physics/ResistanceFieldTest, RollLockTest, TickCycleTest, AmbientSignalTest** — [S] ядра физики.
- **drilling/DrillModeDetectorTest, DrillingRateTest, DrillingUtilsTest, BlockClearerTest** — [S] бурение.
- **pressure/PressureUtilsTest, HullCacheTest, PressureCompensationTest** — [S] давление/корпус.

## Пакет-доки
- **\*/package-info.java** (block, blockentity, client, config, datagen, item, navigation, physics,
  pressure) — маркеры пакетов, описание назначения.

---
### Заметки для навигации
- Активная зона сейчас: мультиблок-корона (коллизия/рендер) — файлы `DrillCrown*`, `CrownShapes`,
  `DrillClientExtensions`, миксины.
- Домены физики/бурения/давления (`PhysicsUtils`, `drilling/`, `pressure/`, `ResistanceField`,
  `TickCycle`) — ядра по TD_06, частично с заглушками TODO[IMPL]/TODO[API-CHECK].
- Для точных сигнатур любого файла — открыть raw-ссылку из FILELIST_TEST.md.
