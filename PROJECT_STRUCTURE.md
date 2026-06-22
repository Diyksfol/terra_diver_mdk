# Terra Diver - Minecraft Mod

A Minecraft mod built for NeoForge 1.21.1 that brings underwater exploration and pressure dynamics.

## Project Structure

### Gradle Configuration
- **build.gradle**: Main build configuration with NeoForge and mod dependencies
- **gradle.properties**: Version and identification properties for the mod
- **settings.gradle**: Gradle project settings

### Source Code Structure (`src/main/java/com/example/terradiver/`)

#### Core
- **TerraDiver.java**: Main mod class with `@Mod("terra_diver")` annotation

#### Package Organization
- **block/**: Block class definitions
- **blockentity/**: Block entity (tile entity) implementations
- **item/**: Custom item classes
- **physics/**: Physics system implementations
- **pressure/**: Pressure mechanics
- **navigation/**: Navigation system
- **registry/**: Registration classes using DeferredRegister
  - BlockRegistry: Block registration
  - BlockEntityRegistry: Block entity registration
  - ItemRegistry: Item registration
- **datagen/**: Data generation classes
- **client/**: Client-side only code
  - ClientEvents: Client-side event handlers

### Resources (`src/main/resources/`)
- **META-INF/neoforge.mods.toml**: Mod metadata and configuration
- **assets/terra_diver/**: Game assets (textures, models, etc.)

## Dependencies

### Included Mods
- **NeoForge 1.21.1**: Minecraft modding framework
- **Create 0.5.1.i**: Contraptions and automation mod
- **Create: Railways 0.6.0**: Train system extension for Create
- **Sable 1.0.0**: Utility mod

## Building the Project

To build the project:

```bash
./gradlew build
```

For development in IDE:

```bash
./gradlew runClient    # Run client
./gradlew runServer    # Run server
./gradlew runData      # Generate data
```

## Mod Information

- **Mod ID**: terra_diver
- **Package**: com.example.terradiver
- **Java Version**: 21
- **Minecraft Version**: 1.21.1
- **NeoForge Version**: 21.1.234

## Current Status

This is a skeleton project with no game logic implemented. All classes are empty and ready for development.

## Development Notes

- All registration classes use `DeferredRegister` for thread-safe registration
- Client events are handled in `ClientEvents.java` with proper `@OnlyIn(Dist.CLIENT)` annotations
- Package structure is organized by functional domain for maintainability
