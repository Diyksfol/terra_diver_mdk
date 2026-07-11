# Create: Terra Diver — Аннотированный список файлов (для ориентировки LLM)
_Ветка `test` · корень пакета `com.example.terradiver`_
_Обновлять при изменении структуры. Точные сигнатуры — дочитывать по raw-ссылке файла._

Легенда: **[V]** — содержимое проверено/написано; **[S]** — описание по спецификации TD_06 (роль
верна, сигнатуры уточнять в файле). **[★]** — создано/изменено в сессии рецептов+кинетики+контраптий.

---

## Вход и конфиг
- **TerraDiver.java** — [★V] главный класс мода (`@Mod`). Регистрирует DeferredRegister'ы, конфиг,
  подписки. **`commonSetup`** регистрирует `BlockMovementChecks` (attach/necessary/allowed) — короны
  склеиваются друг с другом и захватываются контраптиями Create целиком; см. handoff v2, раздел 4.3.
- **config/ModConfig.java** — [V-косв] конфиг-константы: `DEEPSLATE_Y`, `BEDROCK_Y`(≈-60), `PRESSURE_K`,
  `PRESSURE_SCALE`, `SUPPORT_STEP` + пороги бурения/навигации.

## Клиент (`client/`)
- **DrillClientExtensions.java** — [★V] рендер короны в руках. Поза `DRILL_LIFT`, бур над головой в
  3-м и 1-м лице. **Изменено:** `ABOVE_HEAD=0.275` (−2px, финал); в 1-м лице модель якорится к
  СГЛАЖЕННОЙ высоте камеры (`HEAD_ABOVE_EYE=0.18`), чтобы не влезала в голову при приседании. 1x1 исключён.
- **ClientEvents.java** — [★V] клиентские подписки. **Регистрирует рендер подшипника:**
  `EntityRenderersEvent.RegisterRenderers`→`BearingRenderer::new`; `FMLClientSetupEvent`→
  `SimpleBlockEntityVisualizer.builder(CROWN_BEARING).factory(BearingVisual::new).skipVanillaRender(false).apply()`
  (вращающаяся шапка + вал снизу). Плюс placeholder `RegisterKeyMappingsEvent`. (Депрекейт `bus=…MOD` оставлен.)
- **ClientCrownEffects.java** — [★V] клиентская зачистка «прогресса ломания» (трещин) на ячейках короны
  при установке (`clearBreakProgress`, через `Minecraft.levelRenderer.destroyBlockProgress(id,pos,-1)`).
  Причина: у ведомых есть коллизия → ломают их, сервер исключает ломающего из рассылки очистки.
- **DrillOffhandLock.java** — [V] `PlayerTickEvent.Post` (сервер) — добивает F-свап (чистит вторую руку,
  если в основной бур). 4-й слой защиты второй руки (см. ContainerOffhandMixin/SlotMixin).
- **TerraDiverArmPoses.java** — [V] `EnumProxy<HumanoidModel.ArmPose>` `DRILL_LIFT` (обе руки вверх).

## Кинетика/подшипник (`kinetics/`) — ★ новое в этой сессии
- **CrownBearingBlock.java** — [★V] буровой подшипник = наследник `MechanicalBearingBlock` Create.
  Оба подшипника (andesite/sturdy) — этот класс. ПКМ-сборка/разборка, «крутится только когда собрано»,
  вал с тыла — от родителя. НЕ переобъявляет `IBE<>`, только `getBlockEntityType()`→CROWN_BEARING.
  `appendHoverText` — тултип «собрать бур».
- **CrownBearingBlockEntity.java** — [★V] наследник `MechanicalBearingBlockEntity`.
  `calculateStressApplied()` СУММИРУЕТ короны (собрано → из контраптии `movedContraption.getContraption()
  .getBlocks()`; в покое → BFS корон в мире `worldCrownStress()`). Таблица нагрузки
  **8/24/48/64/80/96** (`stressForSide`). `assemble()` требует корону. `addBehaviours()` убирает
  настройку «Режим движения». `addToTooltip()` прячет create-хинт `empty_bearing`, ставит свой (2 строки).

## Миксины (`mixin/`)
- **ContainerOffhandMixin.java** — [★V] common. @Inject HEAD cancellable в `AbstractContainerMenu.clicked`:
  отменяет своп F во вторую руку (button 40 = SLOT_OFFHAND) для буров >1x1 / пока бур в основной руке —
  внутри ЛЮБОГО контейнера (инвентарь/сундук). 3-й слой защиты второй руки.
- **LevelRendererMixin.java** — [V] client. @Inject HEAD в `destroyBlockProgress`: зеркалит прогресс
  трещин на мастер с отрицательным breakerId. (Сосуществует с ClientCrownEffects — при доработке #8 свериться.)
- **SlotMixin.java** — [V] common. @Inject HEAD cancellable в `Slot.mayPlace`: SLOT_OFFHAND — бур >1x1
  во вторую руку нельзя; пока бур в основной — во вторую ничего. helper `isLiftedDrill`.

## Мультиблок-корона (`physics/`)
- **DrillCrownStructure.java** — [V] раскладка коллизии, данные СТРОКАМИ (иначе `<clinit>`>64КБ).
  Профиль = плоский диск 2 блока + скос, октант при ≥6/8 углов внутри. Счётчики 1/10/30/78/128/194.
  Методы `cells`, `depthLayers`, `cellShapeBoxes`, `rotate(Direction)`, `worldCells`. Мастер (0,0,0) первый.
- **CrownShapes.java** — [V] сборка `VoxelShape` из боксов ячейки, повёрнутых по FACING вокруг центра.
- **DrillCrownBlock.java** — [★V] мастер-блок. `crownSize()`, рендер OBJ всей короны.
  **Изменено:** `onPlace` строит ведомые (`!isMoving`) + гасит трещины на клиенте; `onRemove` сносит
  структуру (`!moved` — при захвате контраптией не сносим); `getLightBlock`→0 + `propagatesSkylightDown`→true
  (не затеняет соседей); `initializeClient`→`addDestroyEffects`→true (свои частицы, кроме 1x1);
  `playerWillDestroy` дропает предмет + гасит прогресс ломания по всем ячейкам.
- **DrillCrownItem.java** — [★V] BlockItem короны. Ставит мастер; ведомые строит `onPlace` мастера
  (общая точка для игрока и разборки контраптии — раньше было только в предмете).
- **DrillCrownPartBlock.java** — [★V] невидимый блок-часть. `getShape`=форма ячейки из part-BE;
  **`getCollisionShape`** = полноширинная нижняя плита + реальный верх («ступень», не выталкивает);
  `getLightBlock`→0/`propagatesSkylightDown`→true; `initializeClient` подавляет ванильные частицы;
  `getCloneItemStack`→предмет короны (pick-block); `playerWillDestroy` дроп + гасит прогресс.
  `pushReaction(BLOCK)` (в BlockRegistry).
- **DrillCrownPartBlockEntity.java** — [★V] BE части. КРИТИЧНО `onDataPacket()`→loadAdditional (иначе
  клиент без формы). **Изменено:** `getShape()` читает сторону мастера ЖИВЬЁМ (`currentFacing`).
  ⚠ ЗАМЕЧАНИЕ: для крена бесполезно (facing при вращении не меняется) — можно откатить, см. #3.
- **DrillCrownMultiblock.java** — [★V] логика мультиблока. **`buildParts(level,master,size,facing)`** —
  постройка ведомых (из предмета и onPlace). `dissolve(...)` эмитит частицы (3/ячейку `PARTICLES_PER_CELL`,
  цвет по материалу мастера, по всем ячейкам включая сломанную). Интерфейс `Master` (`crownSize`,`crownFacing`).
  `dropCrownItem`, `worldCells`-обёртки, флаг `DISSOLVING`.

## Физика — Домен 0 и ядра (`physics/`, TD_06) — без изменений этой сессии
- **PhysicsUtils.java** — [V] примитивы: `get_heading`, `is_diggable`, `get_aligned_crowns`,
  `check_crown_rotation_consistency`, `project_crown_front`. TODO[API]: bearing/subLevel.
- **CrownBlock.java** — [V] дата-класс короны (position/face/materialFactor/area/depth/bearingRef).
- **IBearingSpeedProvider.java** — [S] `getSpeed(bearing)`.
- **ResistanceField.java** — [S] аккумулятор `pending_resistance` («кисель»), `decay_*` вне Dive Mode.
- **TickCycle.java** — [S] оркестрация тика (heading→сопротивление→бурение→давление→навигация). Точка для bearing BE.
- **DriveContextAdapter.java** — [S] сенсоры из Offroad/Sable. TODO[API-CHECK].
- **RollLock.java** — [S] подавление крена. **AmbientSignal.java** — [S] громкость фонового сигнала.

## Бурение (`drilling/`, TD_02) — спека, следующий крупный кусок
- **DrillMode.java** [S] · **DrillModeDetector.java** [S] · **DrillingRate.java** [S] ·
  **DrillingUtils.java** [S] (`apply_drilling_velocity`, `compute_pushback`) · **BlockClearer.java** [S].

## Давление/корпус (`pressure/`, TD_03) — без изменений
- **PressureUtils.java** [V] (`compute_raw_debuff`) · **HullCache.java** [V] (TODO[IMPL]) · **GirderLine.java** [S].

## Регистры (`registry/`)
- **BlockRegistry.java** — [★V] `props()`=`explosionResistance(1200)`. `masterProps()` = noOcclusion +
  noLootTable + **`lightLevel(CROWN_GLOW=4)`** (эксперимент против затемнения) + **`pushReaction(BLOCK)`**.
  Части — тоже `pushReaction(BLOCK)`. Подшипники = `new CrownBearingBlock(...)`.
- **BlockEntityRegistry.java** — [★V] добавлен **`CROWN_BEARING`** (один тип на оба подшипника,
  конструктор через лямбду-переходник `(pos,state)->new CrownBearingBlockEntity(TYPE.get(),pos,state)`).
- **ItemRegistry.java** — [★V] предметы устройств (`crown_bearing_andesite/sturdy`, `pressure_gauge`,
  `terradar`, `cartograph_console`, `drill_module`, `piezo_element`) + **`incomplete_drill_module`**
  (transitional для sequenced_assembly). DrillCrownItem по размерам.
- **CreativeTabs.java** — [S] креативная вкладка.

## Данные/ассеты (data/ · assets/) — ★ новое/изменённое
- **data/terra_diver/recipe/*.json** — [★V] 31 рецепт: 24 буровые коронки (база 1x1 верстак,
  наращивание `create:mechanical_crafting` 5×5-без-углов, незеритовое напыление) + устройства
  (`crown_bearing_andesite/sturdy`, `pressure_gauge`, `terradar`, `cartograph_console`) +
  `piezo_element` (mechanical) + `drill_module` (`create:sequenced_assembly`). **mechanical_crafting
  требует `accept_mirrored`**; drill_module — filling зельем `create:potion`+`minecraft:oozing` и наждачку по тегу.
- **data/terra_diver/tags/item/sandpaper.json** — [★V] `#terra_diver:sandpaper` = `#create:sandpaper`
  (+ опционально `createaddition:dimond_grit_sandpaper`, required:false).
- **assets/…/models/item/drill_crown_*.json** — [★V] display: gui `0.625/N` (заполняет ячейку),
  ground `0.25` (выпавший бур = 1/4 модели). Раздача — ЗДЕСЬ (блок-модели `drill_<size>_<metal>` не трогать).
- **assets/…/models/block/crown_bearing_andesite.json** — [★V] корпус без шапки (текстуры Create:
  `mechanical_bearing_side`, `bearing_top`, `gearbox`). **item-модель** — полная (с шапкой).
- **assets/…/blockstates/crown_bearing_andesite.json** — [★V] 6-way FACING, скопировано 1:1 с Create
  (up:{}, down:x180, north:x90, south:x90 y180, east:x90 y90, west:x90 y270).
- **assets/…/lang/{en_us,ru_ru}.json** — [★V] ключи `tooltip.terra_diver.crown_bearing`,
  `hint.terra_diver.crown_bearing.{title,line1,line2}`, `item.terra_diver.incomplete_drill_module`.

## Тесты (`src/test/…`) — JUnit на чистые ядра
- **DrillCrownStructureTest.java** — [V] счётчики (3x3=10,5x5=30,11x11=194), rotationBijective (194),
  masterIsFirst. При смене раскладки — обновлять. Прочие ядра (PhysicsUtils/Resistance/Drilling/Pressure) — [S].

---
### Заметки для навигации
- **Активная зона:** кинетика/контраптии (`kinetics/`, `TerraDiver.commonSetup`, `ClientEvents`) +
  жизненный цикл ведомых (`DrillCrown*`). Рецепты — готовы (`data/…/recipe`).
- **Следующий крупный кусок:** БУРЕНИЕ (`drilling/`) — вычистка породы перед короной (TD_01/03/06).
  Корона по TD_06 стр.49 призрачна к БЛОКАМ, но твёрдая к СУЩНОСТЯМ (уточнение пользователя).
- **Открытая развилка #3** (коллизия при вращении/крене: A симметричная / B снап / C оставить) — см. handoff v2, раздел 6.
- Полный контекст сессии — в **TERRA_DIVER_HANDOFF_v2.md**. Точные сигнатуры — raw-ссылки из FILELIST_TEST.md.