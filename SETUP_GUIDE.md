# Terra Diver Minecraft Mod

**A NeoForge 1.21.1 mod bringing underwater exploration and pressure mechanics to Minecraft**

## Quick Start

### Requirements
- Java 21 or later
- Git
- Gradle (included via wrapper)

### Project Setup

1. **Clone or extract the project:**
   ```bash
   cd terra_diver_mdk
   ```

2. **Import into IDE:**
   - **IntelliJ IDEA**: Open as project, let Gradle sync
   - **Eclipse**: Import as Gradle project
   - **Visual Studio Code**: Install Gradle extension

3. **Configure IDE run configurations:**
   ```bash
   ./gradlew genEclipseLaunches  # For Eclipse
   ./gradlew idea                 # For IntelliJ (already included)
   ```

## Building and Running

### Build the mod JAR:
```bash
./gradlew build
```
Output: `build/libs/terra_diver-1.0.0.jar`

### Development Environment:

| Command | Purpose |
|---------|---------|
| `./gradlew runClient` | Run Minecraft client in dev mode |
| `./gradlew runServer` | Run Minecraft server in dev mode |
| `./gradlew runData` | Generate data files (recipes, models, etc.) |

### IDE Debug Configuration:
For IntelliJ: Use the auto-generated run configurations or create new ones pointing to Gradle tasks above.

## Project Structure

```
terra_diver_mdk/
├── src/main/
│   ├── java/com/example/terradiver/
│   │   ├── TerraDiver.java           # Main mod class (@Mod)
│   │   ├── block/                    # Block definitions
│   │   ├── blockentity/              # Block entities (TileEntities)
│   │   ├── item/                     # Custom items
│   │   ├── physics/                  # Physics systems
│   │   ├── pressure/                 # Pressure mechanics
│   │   ├── navigation/               # Navigation system
│   │   ├── registry/                 # DeferredRegister classes
│   │   ├── datagen/                  # Data generation
│   │   └── client/                   # Client-side code
│   └── resources/
│       ├── META-INF/neoforge.mods.toml
│       └── assets/terra_diver/       # Game assets
├── libs/                             # Local mod dependencies
├── gradle/                           # Gradle wrapper files
├── build.gradle                      # Main build config
├── gradle.properties                 # Mod metadata
└── settings.gradle                   # Gradle settings
```

## Mod Specifications

| Property | Value |
|----------|-------|
| Mod ID | `terra_diver` |
| Main Class | `com.example.terradiver.TerraDiver` |
| Namespace | `com.example.terradiver` |
| Minecraft Version | 1.21.1 |
| NeoForge Version | 21.1.234 |
| Java Version | 21 |
| License | All Rights Reserved |
| Version | 1.0.0 |

## Dependencies

### Required
- **NeoForge**: Minecraft modding framework

### Planned Optional Dependencies
- **Create** (0.5.1.i+): Contraptions and automation
- **Create: Railways** (0.6.0+): Train systems
- **Sable** (1.0.0+): Utility mod

See [DEPENDENCY_SETUP.md](DEPENDENCY_SETUP.md) for dependency configuration.

## Package Organization

### `block/`
Custom block classes extending `Block` or `DirectionalBlock`

### `blockentity/`
Custom BlockEntity implementations for complex block behavior

### `item/`
Custom item classes extending `Item`

### `physics/`
Physics engine and collision detection systems

### `pressure/`
Pressure mechanics and depth system

### `navigation/`
Navigation and pathfinding utilities

### `registry/`
Contains `DeferredRegister` classes:
- `BlockRegistry`: Registers all blocks
- `BlockEntityRegistry`: Registers all block entities
- `ItemRegistry`: Registers all items

### `datagen/`
Data generators for recipes, models, loot tables, etc.

### `client/`
Client-side only code:
- `ClientEvents.java`: Client event handlers
- Custom renderers
- Key bindings

## Development Workflow

### Adding a New Block

1. Create class in `block/` package:
   ```java
   public class MyBlock extends Block {
       // Block logic here
   }
   ```

2. Register in `BlockRegistry.java`:
   ```java
   public static final DeferredBlock<Block> MY_BLOCK = 
       BLOCKS.register("my_block", () -> new MyBlock(...));
   ```

### Adding a New Item

1. Create class in `item/` package (or use `Item` directly)

2. Register in `ItemRegistry.java`:
   ```java
   public static final DeferredItem<Item> MY_ITEM = 
       ITEMS.register("my_item", () -> new Item(...));
   ```

### Adding Data Generation

1. Create data generator class in `datagen/` package

2. Register in `build.gradle` if needed

## Configuration

### Changing Mod Metadata

Edit `gradle.properties`:
```properties
mod_id=terra_diver              # Unique mod identifier
mod_name=Terra Diver            # Display name
mod_version=1.0.0               # Semantic versioning
mod_group_id=com.example.terradiver
```

### Logging

Use `TerraDiver.LOGGER` for logging:
```java
TerraDiver.LOGGER.info("Custom message");
TerraDiver.LOGGER.debug("Debug info");
TerraDiver.LOGGER.error("Error occurred", exception);
```

## Resources

- [NeoForge Documentation](https://docs.neoforged.net/)
- [Minecraft Modding Wiki](https://minecraft.wiki/)
- [Create Mod GitHub](https://github.com/Creators-of-Create/Create)
- [Parchment Mappings](https://parchmentmc.org/)

## Troubleshooting

### Gradle Build Fails
```bash
./gradlew clean
./gradlew build
```

### IDE Not Recognizing Classes
- Refresh Gradle project
- Rebuild IDE indices
- Clear IDE cache

### NullPointerException on Game Start
- Check event bus registration in `TerraDiver.java`
- Verify all DeferredRegister objects are properly initialized

### Assets Not Loading
- Ensure `src/main/resources/assets/terra_diver/` structure is correct
- Check `neoforge.mods.toml` has correct modid

## License

All Rights Reserved - Modify as needed for your project

## Support & Contribution

This is a skeleton project template. For issues:
1. Check the [Troubleshooting](#troubleshooting) section
2. Review NeoForge documentation
3. Check example mods in NeoForge repositories

---

**Last Updated**: 2026-06-22  
**NeoForge Version**: 21.1.234  
**Minecraft Version**: 1.21.1
