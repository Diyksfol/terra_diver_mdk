# Unit Tests — Terra Diver Domain 0

## Статус ✅

**Все юнит-тесты созданы и готовы к запуску:**

### Созданные тест-классы

1. **PhysicsUtilsTest.java** (29 тестов)
   - `get_heading()` — 5 тестов
   - `is_diggable()` — 6 тестов
   - `get_aligned_crowns()` — 6 тестов
   - `check_crown_rotation_consistency()` — 7 тестов
   - `project_crown_front()` — 4 теста
   - Вспомогательные: MockBearing

2. **CrownBlockTest.java** (26 тестов)
   - Constructor & Getters — 6 тестов
   - `getFaceVector()` — 7 тестов (все 6 направлений + нормализация)
   - `isAlignedWithHeading()` — 8 тестов (threshold 0.7, граничные случаи)
   - `toString()` — 1 тест
   - Вспомогательные: MockBearingRef

**Итого: 55 юнит-тестов**

---

## Конфигурация 

### Добавлено в build.gradle

```gradle
// JUnit 5 (Jupiter) для юнит-тестирования
testImplementation 'org.junit.jupiter:junit-jupiter-api:5.9.3'
testImplementation 'org.junit.jupiter:junit-jupiter-engine:5.9.3'

test {
    useJUnitPlatform()
}
```

---

## Запуск тестов

### Все тесты сразу
```powershell
cd C:\VS_Code\Game_projects\Create\ Terra\ Diver\CTD\terra_diver_mdk
.\gradlew.bat test
```

### Конкретный тест-класс
```powershell
# PhysicsUtilsTest
.\gradlew.bat test --tests PhysicsUtilsTest

# CrownBlockTest
.\gradlew.bat test --tests CrownBlockTest
```

### По паттерну
```powershell
# Все get_heading тесты
.\gradlew.bat test --tests "*GetHeading*"

# Все diggable тесты
.\gradlew.bat test --tests "*Diggable*"
```

### С подробным выводом
```powershell
.\gradlew.bat test -i
```

### HTML-отчет
После запуска тестов отчет доступен в:
**`build/reports/tests/test/index.html`**

---

## Покрытие граничных случаев

### PhysicsUtils

| Функция | Граничные случаи |
|---------|-----------------|
| `get_heading()` | null, identity quaternion, 90° rotation, normalization |
| `is_diggable()` | null block, stone, bedrock, water, lava, grass |
| `get_aligned_crowns()` | null input, empty list, null heading, matching/filtering |
| `check_crown_rotation_consistency()` | no aligned, null bearing, CW, CCW, stationary, mixed signs |
| `project_crown_front()` | null input, empty list, layer tolerance |

### CrownBlock

| Метод | Граничные случаи |
|-------|-----------------|
| Constructor | field storage, range validation |
| `getFaceVector()` | все 6 направлений (NORTH/SOUTH/EAST/WEST/UP/DOWN), нормализация |
| `isAlignedWithHeading()` | null heading, perfect (dot=1), opposite (dot=-1), perpendicular (dot=0), threshold (dot=0.7), multiple directions |
| `toString()` | всё содержит необходимую информацию |

---

## Стандарты тестирования

✅ **JUnit 5** с @DisplayName для читаемости  
✅ **AAA паттерн** (Arrange-Act-Assert)  
✅ **Mock-объекты** для зависимостей (MockBearing, MockBearingRef)  
✅ **Специфичные assert методы** (assertEquals, assertTrue, assertFalse, assertNull)  
✅ **@BeforeEach** для инициализации состояния  
✅ **Параметризованные группы** с логичным разделением  

---

## Зависимости от документации

Все тесты соответствуют **TD_06 v1.0, Домен 0**:
- Сжатые описания функций (2-3 строки)
- Ссылки на спецификацию в документации
- Граничные случаи из спецификации явно покрыты

---

Файлы:
- `src/test/java/com/example/terradiver/physics/PhysicsUtilsTest.java`
- `src/test/java/com/example/terradiver/physics/CrownBlockTest.java`
