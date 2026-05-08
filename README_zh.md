<h1 align="center">Epsilon</h1>
<h4 align="center">
    <p>
        <a href="./README.md">English</a> |
        <b>中文</b>
    </p>
</h4>

<p align="center">
  <a href="https://github.com/NekoyaHouse/Epsilon/actions"><img alt="构建" src="https://img.shields.io/badge/build-gradle-4c1?style=flat-square"></a>
  <a href="LICENSE"><img alt="许可证" src="https://img.shields.io/badge/license-Apache%202.0-blue?style=flat-square"></a>
  <img alt="加载器" src="https://img.shields.io/badge/loaders-NeoForge%20%26%20Fabric-6a5acd?style=flat-square">
  <a href="https://discord.gg/vYbaae3X7e"><img alt="Discord" src="https://img.shields.io/badge/Discord-加入社区-5865F2?style=flat-square&logo=discord&logoColor=white"></a>
</p>

## 📌 项目简介
基于 NeoForge & Fabric 构建的多加载器现代化 Minecraft 辅助客户端，拥有先进的渲染系统和模块化架构。

## 🚀 插件系统
[Epsilon 插件模板](https://github.com/NekoyaHouse/Epsilon-Addon-Template)

[Addon 开发文档](docs/addon-development.md)

## 🎨 渲染系统

Lumin 渲染系统提供自定义渲染管线，支持：
- 矩形与圆角矩形
- 阴影与模糊效果
- TTF 字体渲染
- 纹理渲染
- 自定义顶点格式

详见 [渲染系统文档](src/main/java/com/github/epsilon/graphics/README_zh.md)

## ⚙️ 构建与运行

```bash
# 构建模组
./gradlew build

# 运行客户端
./gradlew runClient
```

## 📝 许可证

本项目采用多许可证模式分发：

- **项目核心**: 遵循 [Apache License 2.0](LICENSE) 许可证
- **渲染系统**: 核心渲染组件（位于 `src/main/java/com/github/epsilon/graphics/`）
  遵循 [MIT License](src/main/java/com/github/epsilon/graphics/LICENSE) 许可证

---

版权所有 © 2026 NekoyaHouse.
