# Getting Started with Terra Diver

## ⚡ Quick Start (5 Minutes)

### 1. Extract/Clone the Project
```bash
cd terra_diver_mdk
```

### 2. Open in Your IDE

**IntelliJ IDEA:**
1. File → Open
2. Select `terra_diver_mdk` folder
3. Click "Open"
4. Wait for Gradle to sync
5. In Gradle panel on right → Select `Tasks → NeoForge → runClient`

**Eclipse:**
1. File → Import
2. Gradle → Existing Gradle Project
3. Select `terra_diver_mdk` folder
4. Finish

**VS Code:**
1. Open folder
2. Install Extension Pack for Java
3. Gradle for Java extension
4. Done!

### 3. Run the Mod

In Gradle Tasks panel:
```
NeoForge → runClient      # Play the game
NeoForge → runServer      # Start a server
```

## 📁 Project Layout

```
terra_diver_mdk/
├── src/main/java/com/example/terradiver/  ← Your mod code
│   ├── TerraDiver.java          (Main mod class - START HERE)
│   ├── registry/                (Where you register blocks/items)
│   ├── block/                   (Block classes)
│   ├── item/                    (Item classes)
│   └── ... other packages
├── src/main/resources/          (Textures, models, etc.)
├── build.gradle                 (Dependencies and build settings)
└── gradle.properties            (Mod version info)
```

## 🎯 First Steps

### Step 1: Understand the Main Class
Open: `src/main/java/com/example/terradiver/TerraDiver.java`

This is your mod's entry point. When Minecraft loads, it looks for classes with `@Mod("terra_diver")`.

### Step 2: Create Your First Block

1. Create file: `src/main/java/com/example/terradiver/block/TerraBlock.java`
```java
package com.example.terradiver.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class TerraBlock extends Block {
    public TerraBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE));
    }
}
```

2. Register it in: `src/main/java/com/example/terradiver/registry/BlockRegistry.java`
```java
public static final DeferredBlock<Block> TERRA_BLOCK = 
    BLOCKS.register("terra_block", TerraBlock::new);
```

### Step 3: Create Your First Item

1. Register in: `src/main/java/com/example/terradiver/registry/ItemRegistry.java`
```java
public static final DeferredItem<Item> TERRA_ITEM = 
    ITEMS.register("terra_item", () -> new Item(new Item.Properties()));
```

2. Run the game: `./gradlew runClient`

Your items will appear in creative mode!

## 📚 Documentation Files

| File | Purpose |
|------|---------|
| [SETUP_GUIDE.md](SETUP_GUIDE.md) | Complete setup instructions |
| [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) | Detailed architecture |
| [DEPENDENCY_SETUP.md](DEPENDENCY_SETUP.md) | Adding Create, Railways, Sable |
| [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md) | Project overview |

## 🔧 Useful Gradle Commands

```bash
# Build the mod JAR (outputs to build/libs/)
./gradlew build

# Run game client
./gradlew runClient

# Run dedicated server
./gradlew runServer

# Generate data files (recipes, models, etc.)
./gradlew runData

# Clean build cache
./gradlew clean

# View dependency tree
./gradlew dependencies
```

## 🐛 Troubleshooting

### Game won't launch
- Check Java 21 is installed: `java -version`
- Check Gradle: `./gradlew --version`
- Run: `./gradlew clean`

### IDE doesn't recognize classes
- Refresh Gradle: Right-click project → Gradle → Refresh Gradle Project
- Restart IDE
- Delete `.idea` folder, re-open project

### Compilation errors
- Check package names match folder structure
- Check import statements
- View build output: `./gradlew build --info`

## 📖 Learning Path

1. **First**: Read [SETUP_GUIDE.md](SETUP_GUIDE.md) - 10 minutes
2. **Then**: Create 1 block + 1 item - 30 minutes
3. **Next**: Check [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) - 15 minutes
4. **Finally**: Start implementing your features

## 🎮 Game Testing Tips

**Finding Your Items:**
- Creative Mode: Type "terra_diver" in search
- Survival Mode: `/give @s terra_diver:terra_item`

**Commands to Use:**
```
/gamemode creative        # Creative mode
/gamemode survival        # Survival mode
/say Testing             # Test chat
```

## 📝 Common Tasks

### Add a new block
1. Create class in `block/` package
2. Register in `BlockRegistry.java`
3. Add model/texture in assets folder
4. Run datagen: `./gradlew runData`

### Add a new item
1. Register in `ItemRegistry.java`
2. Add texture in `assets/terra_diver/textures/item/`

### Use Create mod features
1. Enable in `build.gradle` (see [DEPENDENCY_SETUP.md](DEPENDENCY_SETUP.md))
2. Import Create classes:
   ```java
   import com.simibubi.create.foundation.block.BlockStressValues;
   ```

## 🔗 Useful Links

- **NeoForge Docs**: https://docs.neoforged.net/
- **Minecraft Wiki**: https://minecraft.wiki/
- **Create Mod**: https://github.com/Creators-of-Create/Create
- **Parchment Mappings**: https://parchmentmc.org/

## 💡 Pro Tips

1. **Use Git**: Version control from the start
   ```bash
   git add .
   git commit -m "Initial Terra Diver setup"
   ```

2. **Organize Code**: Keep similar classes together
   - All blocks in `block/` package
   - All items in `item/` package
   - Complex logic in separate files

3. **Use Logging**: 
   ```java
   TerraDiver.LOGGER.info("Important info");
   ```

4. **Read Examples**: Look at Create or other NeoForge mods

## ❓ Need Help?

1. Check the docs in this folder
2. Search NeoForge documentation
3. Look at example mods on GitHub
4. Check Minecraft Forge/NeoForge forums

---

**You're ready to mod! 🚀**

Start with `./gradlew runClient` and create something amazing!
