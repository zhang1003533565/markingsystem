# 成绩管理系统

## 项目简介

这是一个前后端分离的成绩管理系统，包含用户认证、角色权限管理、学生信息管理、成绩录入与查询等功能。

> 📘 详细的项目技术文档（发展助手 / AI 能力矩阵 / 个性化引擎）见 [docs/project-document.md](docs/project-document.md)。

## 技术栈

- **前端**: React + Vite
- **后端**: Spring Boot + JPA + MySQL

## 快速开始

### 前端启动

```bash
cd Frontend
npm install
npm run dev
```

### 后端启动

```bash
cd Backend
./mvnw spring-boot:run
```

## 编码校验

本项目已加入项目级编码校验规则，规则说明见 [docs/code-check.md](docs/code-check.md)。

```bash
# 前后端完整校验
npm run check

# 单独校验前端或后端
npm run check:frontend
npm run check:backend
```

## 关于 mvnw（Maven Wrapper）

`mvnw`（Maven Wrapper）是 Maven 包装器，作用是让项目在没有预先安装 Maven 的情况下也能构建和运行。

| 文件       | 用途                             |
| ---------- | -------------------------------- |
| `mvnw`     | Unix/Linux/Mac 使用的 shell 脚本 |
| `mvnw.cmd` | Windows 使用的批处理脚本         |

### 工作原理

1. 自动检测本地是否有 Maven
2. 如果没有，自动下载指定版本的 Maven 到 `~/.m2/wrapper/`
3. 使用下载的 Maven 执行命令

### 使用方式对比

```bash
# 使用 mvnw（推荐）
./mvnw spring-boot:run

# 使用系统安装的 mvn（需要预先安装 Maven）
mvn spring-boot:run
```

### 注意事项

- 团队成员无需手动安装 Maven，直接用 `./mvnw` 即可
- 确保所有人使用相同版本的 Maven，避免版本不一致问题
- 方便 CI/CD 环境部署

## 数据库配置

启动前需要在 MySQL 中创建数据库：

```sql
CREATE DATABASE marking CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

数据库连接配置位于 `Backend/src/main/resources/application.yml`。
