# AI Intelligent Customer Service

基于 Spring Boot、Spring AI、MyBatis-Plus、MySQL、PostgreSQL + pgvector、Ollama Embedding 的 RAG 智能客服后端和静态客服工作台。

## 功能

- 知识库创建与列表查询
- PDF、DOCX、TXT、Markdown 文档上传
- 文档解析、切分、Ollama 本地 embedding
- pgvector 相似度检索
- Spring AI ChatClient 生成客服回答
- 会话与消息历史保存
- 静态客服工作台页面：`http://localhost:8080/`

## 本地依赖

- JDK 17+
- Maven 3.9+
- MySQL 8+
- PostgreSQL 15+ 和 pgvector
- Ollama

拉取本地 embedding 模型：

```powershell
ollama pull nomic-embed-text
```

## 本地敏感配置

项目默认启用 `dev` profile。`application-dev.yml` 会可选导入项目根目录下的 `config/local-secrets.yml`。

第一次本地运行时复制示例文件：

```powershell
Copy-Item config/local-secrets.example.yml config/local-secrets.yml
```

然后只修改 `config/local-secrets.yml` 里的本地数据库密码、API Key、模型地址等配置。

`config/local-secrets.yml` 已加入 `.gitignore`，不要提交到 GitHub。GitHub 只提交 `config/local-secrets.example.yml` 作为模板。

如果暂时不创建 `config/local-secrets.yml`，项目仍可启动，但 ChatModel 会使用占位 API Key；访问 `/api/chat` 进行真实问答时仍需要在本地密钥文件或环境变量中配置真实 `spring.ai.openai.api-key`。

示例：

```yaml
spring:
  datasource:
    mysql:
      jdbc-url: jdbc:mysql://localhost:3306/ai_customer_service?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
      username: root
      password: your-mysql-password
    vector:
      jdbc-url: jdbc:postgresql://localhost:5432/ai_customer_vector
      username: postgres
      password: your-postgres-password
  ai:
    openai:
      api-key: your-api-key
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      chat:
        completions-path: /chat/completions
        options:
          model: qwen-plus
    ollama:
      base-url: http://localhost:11434
      embedding:
        options:
          model: nomic-embed-text

rag:
  file-storage-dir: ./data/uploads
```

如果使用智谱，把 Chat 配置切换为：

```yaml
spring:
  ai:
    openai:
      api-key: your-zhipu-api-key
      base-url: https://open.bigmodel.cn/api/paas/v4
      chat:
        completions-path: /chat/completions
        options:
          model: glm-4-plus
```

环境变量仍然可用，适合 CI/CD 或临时覆盖配置。

如果使用 OpenAI 官方接口，通常可以配置：

```yaml
spring:
  ai:
    openai:
      base-url: https://api.openai.com
      chat:
        completions-path: /v1/chat/completions
```

## 初始化数据库

MySQL：

```powershell
mysql -u你的用户名 -p < sql/mysql.sql
```

PostgreSQL：

```powershell
psql -U 你的用户名 -f sql/postgres-vector.sql
```

如果 `ai_customer_vector` 数据库已经存在，只需要连接到该库后执行 `create extension`、建表和索引部分。

## 启动

```powershell
mvn spring-boot:run
```

健康检查：

```powershell
curl http://localhost:8080/api/health
```

客服工作台：

```text
http://localhost:8080/
```

## 快速验收

创建知识库：

```powershell
curl -X POST http://localhost:8080/api/knowledge-bases `
  -H "Content-Type: application/json" `
  -d "{\"name\":\"售后知识库\",\"description\":\"售后政策、维修和退款说明\"}"
```

上传文档：

```powershell
curl -X POST http://localhost:8080/api/knowledge-bases/1/documents `
  -F "file=@D:\path\售后政策.md"
```

RAG 问答：

```powershell
curl -X POST http://localhost:8080/api/chat `
  -H "Content-Type: application/json" `
  -d "{\"knowledgeBaseId\":1,\"question\":\"过保产品还能维修吗？\"}"
```

查询会话消息：

```powershell
curl http://localhost:8080/api/conversations/1/messages
```

## 重要说明

- ChatModel 使用 Spring AI OpenAI-compatible 客户端，方便切换通义千问或智谱。
- EmbeddingModel 使用 Ollama 本地模型。
- 当前 pgvector SQL 按 `nomic-embed-text` 的 768 维设置，如果更换 embedding 模型，需要同步修改 `sql/postgres-vector.sql` 里的 `vector(768)`。
