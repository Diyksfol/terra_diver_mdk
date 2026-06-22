# Dependency Management for Terra Diver

## Using Remote Repositories

To use Create, Create: Aeronautics Simulated (Railways), and Sable from Maven repositories:

1. Uncomment the corresponding dependency lines in `build.gradle`
2. Ensure the repositories in `build.gradle` include the necessary Maven repos
3. Run `./gradlew build` to fetch and compile

## Using Local JARs

If dependencies are not available in remote repositories:

1. **Download the mod JARs:**
   - Create: Download from [Modrinth](https://modrinth.com/mod/create) or [CurseForge](https://www.curseforge.com/minecraft/mods/create)
   - Railways: Download from [Modrinth](https://modrinth.com/mod/railways)
   - Sable: Download from appropriate source

2. **Place JARs in `libs/` folder:**
   ```
   libs/
   ├── create-neoforge-1.21.1-0.5.1.i.jar
   ├── railways-1.21.1-0.6.0.jar
   └── sable-1.21.1-1.0.0.jar
   ```

3. **Enable local JARs in build.gradle:**
   
   Uncomment in `dependencies` block:
   ```gradle
   // If using local mod JARs
   implementation files("libs/create-neoforge-1.21.1-0.5.1.i.jar")
   implementation files("libs/railways-1.21.1-0.6.0.jar")
   implementation files("libs/sable-1.21.1-1.0.0.jar")
   ```

4. **Also configure flatDir in repositories block:**
   ```gradle
   flatDir {
       dirs 'libs'
   }
   ```

## Maven Repositories Setup

The following repositories are configured in `build.gradle`:
- `https://maven.theillusivec4.com/` - The Illusory C4's Maven
- `https://modmaven.k02c.dev/` - Mod Maven
- `https://jab125.github.io/maven/` - Custom Java Maven
- `https://maven.blamej.com/` - BLAMEJ Maven

Add more repositories as needed based on where your dependencies are hosted.

## Checking Dependency Availability

To verify that dependencies are accessible:

1. Run: `./gradlew dependencies`
2. Check the dependency tree output
3. Look for any UNRESOLVED dependencies

## Troubleshooting

### Dependency Resolution Failures

If a dependency cannot be resolved:

1. Check that the correct version is specified for MC 1.21.1
2. Verify repository URLs are accessible
3. Clear Gradle cache: `./gradlew clean`
4. Use local JARs as fallback

### Version Compatibility

Ensure all mod versions are compatible with:
- Minecraft 1.21.1
- NeoForge 21.1.234
- Java 21+

### Building Without Optional Dependencies

If you want to build without certain dependencies commented in:

```gradle
implementation(project(path: ':terraform', configuration: 'namedElements')) {
    transitive = false
}
```

This prevents transitive dependencies from being pulled in.
