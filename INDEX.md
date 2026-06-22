# Terra Diver Minecraft Mod - Documentation Index

## 🚀 Start Here

### For Quick Start
👉 **[GETTING_STARTED.md](GETTING_STARTED.md)** - 5-minute quick start guide to run the mod

### For Complete Setup
📖 **[SETUP_GUIDE.md](SETUP_GUIDE.md)** - Comprehensive setup and development instructions

---

## 📚 Documentation Structure

### Essential Documents

| Document | Audience | Time |
|----------|----------|------|
| [GETTING_STARTED.md](GETTING_STARTED.md) | Everyone | 5 min |
| [SETUP_GUIDE.md](SETUP_GUIDE.md) | Developers | 15 min |
| [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) | Architects | 10 min |
| [DEPENDENCY_SETUP.md](DEPENDENCY_SETUP.md) | Advanced | 10 min |
| [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md) | Leads | 5 min |

---

## 📋 Document Descriptions

### 🟢 [GETTING_STARTED.md](GETTING_STARTED.md)
**Best for**: First-time setup, quick reference
- IDE setup (IntelliJ, Eclipse, VS Code)
- Running the game in 5 minutes
- First steps to create blocks/items
- Troubleshooting quick fixes
- Common commands cheatsheet

### 🔵 [SETUP_GUIDE.md](SETUP_GUIDE.md)
**Best for**: Complete development setup
- Detailed build and run instructions
- IDE configuration
- Complete project structure explanation
- Mod specifications
- Development workflows
- Troubleshooting guide
- Resource directory structure

### 🟡 [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md)
**Best for**: Understanding architecture
- Project organization
- Package purposes
- File locations
- Current development status
- Development notes

### 🟠 [DEPENDENCY_SETUP.md](DEPENDENCY_SETUP.md)
**Best for**: Adding Create, Railways, Sable
- Remote repository setup
- Local JAR configuration
- Maven repository information
- Version compatibility
- Dependency troubleshooting

### 🔴 [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md)
**Best for**: Project overview
- What's been created
- Project statistics
- Next steps checklist
- Quick reference commands
- Project readiness status

---

## 🎯 By Use Case

### I'm new to modding
1. Start: [GETTING_STARTED.md](GETTING_STARTED.md)
2. Read: [SETUP_GUIDE.md](SETUP_GUIDE.md) → "Development Workflow"
3. Create: Your first block following the guide

### I need to add dependencies
1. Read: [DEPENDENCY_SETUP.md](DEPENDENCY_SETUP.md)
2. Uncomment in `build.gradle`
3. Run: `./gradlew build`

### I'm setting up a dev environment
1. Read: [SETUP_GUIDE.md](SETUP_GUIDE.md) → "Building and Running"
2. Import: Project into your IDE
3. Configure: IDE run configurations

### I need to understand the architecture
1. Read: [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md)
2. Review: Package organization section
3. Explore: Source code structure

### I'm managing this project
1. Read: [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md)
2. Check: "Project Readiness" status
3. Review: "Next Steps" checklist

---

## 📂 Directory Structure

```
terra_diver_mdk/
├── 📄 GETTING_STARTED.md        ← START HERE
├── 📄 SETUP_GUIDE.md            ← Complete guide
├── 📄 PROJECT_STRUCTURE.md      ← Architecture
├── 📄 DEPENDENCY_SETUP.md       ← Dependencies
├── 📄 PROJECT_SUMMARY.md        ← Overview
├── 📄 INDEX.md                  ← You are here
│
├── 📁 src/main/
│   ├── java/com/example/terradiver/
│   │   ├── TerraDiver.java              (Main mod class)
│   │   ├── registry/                    (Registration classes)
│   │   ├── block/                       (Block definitions)
│   │   ├── item/                        (Item definitions)
│   │   ├── blockentity/                 (Block entities)
│   │   ├── physics/                     (Physics systems)
│   │   ├── pressure/                    (Pressure mechanics)
│   │   ├── navigation/                  (Navigation system)
│   │   ├── datagen/                     (Data generation)
│   │   └── client/                      (Client-side code)
│   └── resources/
│       ├── META-INF/neoforge.mods.toml
│       └── assets/terra_diver/
│
├── 📁 gradle/                   (Gradle wrapper files)
├── 📁 libs/                     (Local mod dependencies)
│
├── build.gradle                 (Build configuration)
├── gradle.properties            (Mod metadata)
├── settings.gradle              (Gradle settings)
├── gradlew                      (Linux/Mac script)
├── gradlew.bat                  (Windows script)
│
└── README.md                    (Original MDK README)
```

---

## 🔄 Reading Guide Path

### Path 1: Quick Start (20 minutes)
```
GETTING_STARTED.md → Open IDE → Run Game → Done!
```

### Path 2: Complete Setup (1 hour)
```
SETUP_GUIDE.md → PROJECT_STRUCTURE.md → Create First Block → Test
```

### Path 3: Professional Setup (2-3 hours)
```
PROJECT_SUMMARY.md 
  → SETUP_GUIDE.md 
  → PROJECT_STRUCTURE.md 
  → DEPENDENCY_SETUP.md 
  → Create Blocks & Items 
  → Git Setup
  → Full Test
```

### Path 4: Advanced (Project Lead)
```
PROJECT_SUMMARY.md → Check Readiness → DEPENDENCY_SETUP.md → Plan Development
```

---

## 📊 Project Information

| Property | Value |
|----------|-------|
| **Mod ID** | terra_diver |
| **Minecraft** | 1.21.1 |
| **NeoForge** | 21.1.234 |
| **Java** | 21+ |
| **Package** | com.example.terradiver |
| **Status** | Skeleton - Ready for Development |
| **Create Date** | 2026-06-22 |

---

## ✅ Project Status

- ✓ Mod framework configured
- ✓ Package structure created
- ✓ Main mod class ready
- ✓ Registry classes set up
- ✓ Documentation complete
- ⚠️ Optional dependencies commented (needs manual enable)
- ⚠️ No game logic (by design)
- ⚠️ Assets directory empty

---

## 🆘 Quick Troubleshooting

| Problem | Solution |
|---------|----------|
| IDE doesn't see classes | Refresh Gradle project |
| Game won't launch | Check Java 21 installed |
| Build fails | Run `./gradlew clean` |
| Can't find docs | Look at [GETTING_STARTED.md](GETTING_STARTED.md) |

See [SETUP_GUIDE.md](SETUP_GUIDE.md#troubleshooting) for more troubleshooting.

---

## 🔗 External Resources

- **[NeoForge Documentation](https://docs.neoforged.net/)** - Official docs
- **[Minecraft Wiki](https://minecraft.wiki/)** - Game mechanics
- **[Create Mod GitHub](https://github.com/Creators-of-Create/Create)** - Create mod source
- **[Parchment Mappings](https://parchmentmc.org/)** - Method names and documentation

---

## 📝 Notes for Developers

1. **Main Entry Point**: [TerraDiver.java](src/main/java/com/example/terradiver/TerraDiver.java)
2. **Add Blocks**: Create class in `block/` → Register in [BlockRegistry.java](src/main/java/com/example/terradiver/registry/BlockRegistry.java)
3. **Add Items**: Register in [ItemRegistry.java](src/main/java/com/example/terradiver/registry/ItemRegistry.java)
4. **Client Code**: Add to [client/](src/main/java/com/example/terradiver/client/) package
5. **Assets**: Put in `src/main/resources/assets/terra_diver/`

---

## 🎓 Learning Order (Recommended)

1. Read: [GETTING_STARTED.md](GETTING_STARTED.md)
2. Setup: IDE following the guide
3. Run: `./gradlew runClient`
4. Create: Your first block
5. Read: [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md)
6. Expand: Add more blocks/items
7. Advanced: Read [DEPENDENCY_SETUP.md](DEPENDENCY_SETUP.md)

---

## 💬 Support

For issues or questions:
1. Check relevant documentation file
2. Search NeoForge docs
3. Review example mods
4. Check NeoForge forums

---

**Version**: 1.0  
**Last Updated**: 2026-06-22  
**Status**: Ready for Development ✅

[Go to Getting Started →](GETTING_STARTED.md)
