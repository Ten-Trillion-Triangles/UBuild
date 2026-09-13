**[Ten Trillion Triangles](https://www.tentrilliontriangles.com)** — **[TPipe](https://www.tentrilliontriangles.com)**

# UBuild

UBuild is a command-line build automation tool specifically designed for Unreal Engine projects. It provides a unified interface for building, packaging, and managing Unreal Engine projects across different platforms (Windows, Linux, macOS) with support for multiple engine versions and project configurations.

## Features

- **Multi-Engine Support**: Manage multiple Unreal Engine installations and switch between them
- **Project Management**: Configure and manage multiple Unreal Engine projects with custom build settings
- **Cross-Platform**: Works on Windows, Linux, and macOS
- **Build Automation**: Automate building, packaging, and deployment of Unreal Engine projects
- **Plugin Support**: Build and package Unreal Engine plugins
- **Flag Aliases**: Create reusable build flag configurations
- **Interactive CLI**: Both command-line arguments and interactive wizard modes
- **Configuration Management**: JSON-based configuration system with project templates
- **Full UAT and UBT Support**: UBuild supports all of the features provided by UAT and UBT surpassing what IDE's and UnrealEditor provides for building and packaging projects.

## Architecture

UBuild is built as a multi-module Kotlin project using Ktor framework:

- **Core Module**: Contains shared interfaces and data models (RPC service definitions)
- **Server Module**: Main application logic, CLI parser, and build automation
- **Client Module**: Extensions for making requests to the server (future web interface support)

## Requirements

### System Requirements
- **JDK 21** to run the Gradle 8.5 wrapper
- **Gradle 8.5**, supplied by the checked-in Gradle Wrapper; no system Gradle installation is required
- **Kotlin Gradle plugin 2.1.0**, resolved automatically by the build

### Unreal Engine Requirements
- Unreal Engine installation (any version 4.x or 5.x)
- Access to Unreal Build Tool (UBT) and Unreal Automation Tool (UAT)
- Platform-specific build tools (Visual Studio on Windows, Xcode on macOS, etc.)

## Building

### Prerequisites
1. Install JDK 21
2. Ensure `JAVA_HOME` is set correctly
3. Clone the repository; the checked-in wrapper downloads Gradle 8.5 on first use

### Build Commands

| Command | Description |
|---------|-------------|
| `./gradlew build` | Build all modules |
| `./gradlew :server:buildFatJar` | Build executable JAR with all dependencies |
| `./gradlew :server:run` | Run the application in development mode |
| `./gradlew test` | Run all tests |

### Docker build and test

The Docker image uses JDK 21 and runs `./gradlew --no-daemon clean build`, compiling all modules and running the test suite. Build it with:

```bash
docker build --tag ubuild-build .
```

The first build needs network access to download the Gradle distribution and dependencies.

### Creating Distribution
```bash
# Build the fat JAR
./gradlew :server:buildFatJar

# The executable JAR will be created at:
# server/build/libs/server-all.jar
```

## Installation

### Option 1: Using Pre-built Scripts
After building, use the provided scripts:
- **Linux/macOS**: `./ubuild.sh [commands]`
- **Windows**: `ubuild.bat [commands]`

### Option 2: Direct JAR Execution
```bash
java -jar server/build/libs/server-all.jar [commands]
```

### Option 3: System Installation
```bash
# Run the install command to create a deployment-ready structure
./ubuild.sh install
```

## Configuration

UBuild stores its configuration in `~/.ubuild/config.json`. The configuration includes:
- Engine installations and paths
- Project configurations
- Build flag aliases
- Launch shortcuts

## Command Line Interface

### Interactive Mode
Run without arguments to enter interactive wizard mode:
```bash
./ubuild.sh
```

### Command Mode
Run with arguments for direct command execution:
```bash
./ubuild.sh [command] [arguments...]
```

## Core Commands

### Engine Management
```bash
# Set up engine configuration (required first step)
./ubuild.sh set-engine [engine-root-path]

# Switch between engine configurations
./ubuild.sh swap [engine-alias]

# Set default engine configuration
./ubuild.sh default [engine-alias]

# Remove engine configuration
./ubuild.sh remove-engine [engine-alias]
```

### Project Management
```bash
# Configure a new project
./ubuild.sh set-project [alias] [project-root] [project-target] [archive-path] [flag-alias]

# Remove project configuration
./ubuild.sh remove-project [project-alias]

# Import project configuration as template
./ubuild.sh import

# Update project archive path
./ubuild.sh path
```

### Building & Packaging
```bash
# Build project (equivalent to IDE build)
./ubuild.sh build [project-alias] [target] [config] [platform] [extra-flags]

# Package project for distribution
./ubuild.sh package [project-alias] [target] [config] [platform] [flag-alias]

# Build specific module
./ubuild.sh module [module-name] [platform] [config] [extra-flags]

# Build plugin
./ubuild.sh plugin [project-alias] [plugin-name] [target] [config] [platform]

# Generate project files
./ubuild.sh generate [project-alias]
```

### Utility Commands
```bash
# Switch project engine version
./ubuild.sh switch [project-alias]

# Build Unreal Engine from source
./ubuild.sh make [build-config]

# Register engine with UAT
./ubuild.sh register

# Convert line endings (Unix to DOS)
./ubuild.sh unix2dos [root-directory]
```

### Flag Management
```bash
# Create/update flag alias
./ubuild.sh flag-alias [alias-name] [flags...]

# Merge two flag aliases
./ubuild.sh merge-flags [new-alias] [alias-a] [alias-b]

# List all flag aliases
./ubuild.sh list-flags
```

### Launch Configurations
```bash
# Set launch shortcut
./ubuild.sh set-launch

# Run saved launch configuration
./ubuild.sh run

# Set UAT automation alias
./ubuild.sh set-uat

# List all launch configurations
./ubuild.sh list-run
```

### Information & Help
```bash
# Show help and available commands
./ubuild.sh help

# Show configuration information
./ubuild.sh config
./ubuild.sh config-verbose

# Show useful resources and links
./ubuild.sh info
```

## Example Workflows

### Initial Setup
```bash
# 1. Set up engine configuration
./ubuild.sh set-engine /path/to/UnrealEngine

# 2. Configure your first project
./ubuild.sh set-project MyGame /path/to/MyGameProject MyGame /path/to/builds basic

# 3. Generate project files
./ubuild.sh generate MyGame
```

### Development Build
```bash
# Build for development
./ubuild.sh build MyGame Editor Development

# Package for testing
./ubuild.sh package MyGame MyGame Development Win64 basic
```

### Shipping Build
```bash
# Package for shipping
./ubuild.sh package MyGame MyGame Shipping Win64 shipping
```

### Multi-Platform Build
```bash
# Package for Windows
./ubuild.sh package MyGame MyGame Shipping Win64

# Package for Linux
./ubuild.sh package MyGame MyGame Shipping Linux

# Package for Android
./ubuild.sh package MyGame MyGame Shipping Android android-shipping
```

## Default Flag Aliases

UBuild comes with predefined flag aliases:
- **basic**: `-prereqs -stage -pak -CrashReporter`
- **shipping**: `-prereqs -stage -pak -CrashReporter -nodebuginfo`
- **android**: `-prereqs -stage -pak -CrashReporter -UpdateIfNeeded`
- **android-shipping**: `-prereqs -stage -pak -CrashReporter -UpdateIfNeeded -nodebuginfo`
- **empty**: No flags

## Advanced Features

### Fast Clean Operations
UBuild provides faster alternatives to UBT's clean operations:
- `-fastclean`: Delete Binaries and Intermediate folders (project + plugins)
- `-lightclean`: Delete only Intermediate folders
- `-fastrebuild`: Fast clean + build
- `-lightrebuild`: Light clean + build

### Multiple Engine Support
Manage multiple Unreal Engine versions:
```bash
# Set up multiple engines
./ubuild.sh set-engine /path/to/UE5.3
./ubuild.sh swap UE53
./ubuild.sh set-engine /path/to/UE5.4
./ubuild.sh swap UE54

# Switch between them
./ubuild.sh swap UE53
./ubuild.sh default UE54
```

### Project Templates
Import existing project configurations:
```bash
./ubuild.sh import
# Follow prompts to create new project based on existing one
```

---

**Made with [TPipe](https://www.tentrilliontriangles.com) by [Ten Trillion Triangles](https://tentrilliontriangles.com)**





## What's New in v2 (Gradle + Colossal Support)

v2 extends UBuild from an Unreal-only CLI to a multi-engine build automation tool that natively handles **Gradle projects**, **Unreal projects** (existing), and **Colossal 1** (the current TPipe + Autogenesis stack) as a gradle project with engine-specific defaults. **Colossal 2** is registered as a stub that fails loud when run.

### Automatic config migration (v1 → v2)

The on-disk config schema changed in v2: project types are now a sealed hierarchy (`unreal`, `gradle`, `colossal`) and every project entry carries a `type` discriminator. Existing v1 configs are upgraded **transparently** the first time you run v2. The original `~/.ubuild/config.json` is backed up to `~/.ubuild/config.json.v1.bak` and rewritten in the v2 form. **No user action is required** — the migration is fully automatic and idempotent (running it twice on a v2 file is a no-op).

### Gradle commands

```bash
# Scaffold a new task into an existing build.gradle.kts
ubuild gradle init-task MyGame myTask build "Run my task" --depends-on compile

# Scaffold a new subproject + register in settings.gradle.kts
ubuild gradle init-subproject MyGame server kotlinJvm

# Scaffold a brand-new standalone gradle project
ubuild gradle init-project MyGame /home/cage/proj/MyGame kotlinJvm

# Discover all gradle tasks
ubuild gradle list-tasks MyGame

# Introspect a gradle project (properties + env + static source parse)
ubuild gradle info MyGame

# Run a gradle task
ubuild gradle run MyGame build
ubuild gradle test MyGame
ubuild gradle clean MyGame
```

The top-level `ubuild build MyGame` and `ubuild package MyGame` commands auto-dispatch by project type. For gradle projects, `package` shows a stage-task picker filtered from the project's task list (`install*`, `dist*`, `stage*`, etc.); the user can also pass `--stage-task <name>` to skip the picker.

### Colossal commands

```bash
# Register a colossal-1 project (TPipe + Autogenesis stack). The detector looks for
# the Autogenesis settings.gradle.kts fingerprint + a TPipe sibling directory.
ubuild colossal register /path/to/Autogenesis/Autogenesis --alias Autogenesis

# Register a colossal-2 stub (engine does not exist yet; all commands refuse to run).
ubuild colossal register /some/path --version colossal-2 --alias FutureEngine

# Show colossal-specific info for a registered project.
ubuild colossal info Autogenesis
```

### Code style

All new Kotlin code follows the TTT Kotlin Style Guide (newline-brace for paren-bearing constructs, no space between keyword and `(`, `val name: Type` with no space before colon, KDoc on every top-level public `fun`, no banned identifiers like `tmp` or `result`). A CI style check (`Style/TttStyleTest.kt`) is wired into `./gradlew :server:check` and blocks merges on violations. The check is scoped to v2 source files so pre-existing UE code does not fail the build.

### Project file structure (v2)

- `Config/Project.kt` — sealed `Project` interface
- `Config/UnrealProject.kt`, `Config/GradleProject.kt`, `Config/ColossalProject.kt` — concrete subtypes
- `Config/Migration.kt` — v1 → v2 migration with byte-identical backup
- `Config/ConfigFile.kt` — adds `configVersion: Int = 2` to drive the migration
- `Gradle/TaskDiscovery.kt` — shells out to `./gradlew tasks --all`, parses output
- `Gradle/ProjectIntrospector.kt` — static parse of `build.gradle.kts` + env files
- `Gradle/StageFilter.kt` — picks stage-like gradle tasks for the package picker
- `Gradle/BuildFilePatcher.kt` — appends a new `tasks.register { ... }` block
- `Gradle/SubprojectScaffolder.kt` — adds a new subproject + `include(...)` line
- `Gradle/ProjectScaffolder.kt` — scaffolds a brand-new standalone gradle project
- `Colossal/ColossalDetector.kt` — fingerprint detector for the colossal-1 stack
- `Colossal/RunColossalTask.kt` — shared gradle task runner used by colossal projects
- `Parser/GradleSubcommand.kt`, `Parser/ColossalSubcommand.kt` — subcommand dispatchers
- `Style/TttStyleTest.kt` — CI style check scoped to v2 source files

## License

UBuild is licensed under the [MIT License](LICENSE). Third-party components, including the Gradle wrapper, remain under their respective licenses.
