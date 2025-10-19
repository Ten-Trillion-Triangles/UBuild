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
- **Java 24** or higher (JVM target)
- **Kotlin 2.2.0** or higher
- **Gradle 8.x** for building from source

### Unreal Engine Requirements
- Unreal Engine installation (any version 4.x or 5.x)
- Access to Unreal Build Tool (UBT) and Unreal Automation Tool (UAT)
- Platform-specific build tools (Visual Studio on Windows, Xcode on macOS, etc.)

## Building

### Prerequisites
1. Install Java 24 or higher
2. Ensure `JAVA_HOME` is set correctly
3. Clone the repository

### Build Commands

| Command | Description |
|---------|-------------|
| `./gradlew build` | Build all modules |
| `./gradlew :server:buildFatJar` | Build executable JAR with all dependencies |
| `./gradlew :server:run` | Run the application in development mode |
| `./gradlew test` | Run all tests |

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


