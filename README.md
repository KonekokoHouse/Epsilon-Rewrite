<h1 align="center">Epsilon</h1>
<h4 align="center">
    <p>
        <b>English</b> |
        <a href="./README_zh.md">中文</a>
    </p>
</h4>

<p align="center">
  <a href="https://github.com/NekoyaHouse/Epsilon/actions"><img alt="Build" src="https://img.shields.io/badge/build-gradle-4c1?style=flat-square"></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/license-Apache%202.0-blue?style=flat-square"></a>
  <img alt="Loaders" src="https://img.shields.io/badge/loaders-NeoForge%20%26%20Fabric-6a5acd?style=flat-square">
  <a href="https://discord.gg/vYbaae3X7e"><img alt="Discord" src="https://img.shields.io/badge/Discord-Join%20Server-5865F2?style=flat-square&logo=discord&logoColor=white"></a>
</p>

## 📌 Overview
A modern multi loader Minecraft utility client built on NeoForge & Fabric with advanced rendering system and modular architecture.

## 🚀 Addon System
[Epsilon Addon Template](https://github.com/NekoyaHouse/Epsilon-Addon-Template)

[Addon Development Guide](docs/addon-development.md)

## 🎨 Graphics System

The Lumin rendering system provides custom render pipelines for:
- Rectangles & Round Rectangles
- Shadows & Blur effects
- TTF Font rendering
- Texture rendering
- Custom vertex formats

See [Lumin Graphics README](src/main/java/com/github/epsilon/graphics/README.md) for details.

## ⚙️ Build & Run

```bash
# Build the mod
./gradlew build

# Run client
./gradlew runClient
```

## 📝 License

This project is distributed under a multi-license model:

- **Project Core**: Licensed under the [Apache License 2.0](LICENSE).
- **Graphics**: The core rendering components (located in `src/main/java/com/github/epsilon/graphics/`)
  are licensed under the [MIT License](src/main/java/com/github/epsilon/graphics/LICENSE).

---

Copyright © 2026 NekoyaHouse.
