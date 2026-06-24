# AGENTS.md

本文件是本项目给 AI 编程 Agent 使用的通用协作规范。业务需求、详细设计、接口细节以 `docs/` 下的文档和用户最新指令为准。

## 项目概况

- 项目类型：AI 智能客服后端与原生前端工作台。
- 后端：Spring Boot、Spring AI、MyBatis-Plus。
- 数据库：MySQL、PostgreSQL + pgvector。
- 前端：HTML、CSS、JavaScript。
- 构建工具：Maven。

Agent 开始任务前，通常先查看：

- `pom.xml`
- `src/main/resources/application.yml`
- `src/main/java/com/example/aics`
- `src/main/resources/static`
- `sql/`
- `docs/`

## 工作原则

- 先读现有代码，再修改。
- 优先做最小可用改动，不做无关重构。
- 保持现有技术栈，除非用户明确要求替换。
- 不覆盖用户已有修改，不回滚无关文件。
- 需求还在设计阶段时，只输出设计，不写代码。
- 修改接口、表结构或重要流程时，同步更新相关文档或 SQL。

## 代码规范

### 后端

- Controller 只处理请求、校验和响应，不写复杂业务。
- Service 放业务编排和事务边界。
- DTO 用于请求和响应，避免随意暴露 Entity。
- 数据访问优先使用已有 Mapper 和 MyBatis-Plus 风格。
- 统一返回 `ApiResponse<T>`。
- 异常要有清晰错误信息，不要吞异常。

### 前端

- 继续使用原生 HTML/CSS/JS。
- 页面要有基本的 loading、empty、error、success 状态。
- 不主动引入 Vue、React 或大型 UI 框架。
- 文案和文件统一使用 UTF-8。

### 配置

- 不提交真实 API Key、数据库密码或本地密钥。
- 本地敏感配置放在 `config/local-secrets.yml`。
- 不把 `config/local-secrets.yml` 内容写入文档或日志。

## 数据库与 AI 约定

- MySQL 保存业务数据。
- PostgreSQL pgvector 保存向量数据。
- 修改 MySQL 表时，同步更新 `sql/mysql.sql`、Entity、Mapper。
- 修改向量维度时，同步检查 embedding 模型、`sql/postgres-vector.sql` 和相关配置。
- Prompt 模板放在 `src/main/resources/prompts`。
- RAG 回答应优先依据检索上下文，检索不到时不要编造。

## 禁止事项

除非用户明确要求，不要：

- 执行 `git reset --hard` 或删除用户文件。
- 大范围格式化无关文件。
- 引入微服务、多租户、复杂 RBAC、消息队列等重架构。
- 擅自切换数据库、模型供应商或前端技术栈。
- 把同步文档解析直接改成复杂异步架构。
- 为单个功能引入大量新依赖。

## Git 规范

- 不要在用户未明确要求时主动提交 commit。
- 提交前先查看 `git status`，确认只包含本次任务相关文件。
- 新增代码、文档、SQL 或配置模板文件时，如果属于本次任务，应主动执行 `git add <file>` 加入暂存区记录。
- 修改已有文件时，除非用户要求提交或暂存，否则不强制 `git add`。
- 不要回滚、覆盖或格式化用户已有的无关修改。
- 如果工作区存在无关变更，只处理自己的改动，并在回复中说明。
- Commit message 使用简洁中文或英文，推荐格式：`类型: 简短说明`。
- 常用类型：`feat`、`fix`、`docs`、`refactor`、`test`、`chore`。
- 完成任务后只生成建议提交信息，不自动提交。

示例：

```text
docs: 精简 Agent 协作规范
feat: 增加知识库管理页面
fix: 修复文档上传解析失败提示
```

## 验证要求

代码修改后尽量运行：

```powershell
mvn test
```

如果测试不完整或耗时过长，至少运行：

```powershell
mvn -DskipTests compile
```

前端修改后应检查：

- 页面能否打开。
- 控制台是否有明显 JS 错误。
- 关键流程是否可操作。

接口修改后应检查：

- 成功响应。
- 参数缺失响应。
- 异常响应。
- 数据库记录是否符合预期。

## 交付说明

完成任务后，简要说明：

- 改了哪些文件。
- 做了什么验证。
- 是否有未完成事项或风险。

如果没有运行测试，要说明原因。
