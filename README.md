# 广东工业大学课程评价系统

基于 Spring Boot + Spring AI 的智能课程评价系统，集成阿里云通义千问大模型。

## 技术栈

- **框架**: Spring Boot 3.4.5
- **AI集成**: Spring AI 1.0.0 + Spring AI Alibaba 1.0.0.4
- **数据库**: MySQL 8.0 + Redis
- **ORM**: MyBatis-Plus 3.5.5
- **工具库**: Hutool 5.8.25

## 功能特性

### 核心功能
- ✅ AI智能问答 - 基于通义千问大模型
- ✅ 课程查询 - 支持按关键词、标签搜索课程
- ✅ 课程推荐 - 基于热门、相似、历史记录的推荐算法
- ✅ 课程评论 - 支持评论、点赞功能
- ✅ 搜索热词 - 实时热门搜索关键词统计

### AI能力
- ✅ 上下文管理 - Redis-based 会话记忆
- ✅ 工具调用 - 自动调用课程查询工具
- ✅ 流式响应 - SSE实时流式输出
- ✅ 消息摘要 - 自动总结长对话
- ✅ 敏感词过滤 - 内容安全过滤

### 系统保障
- ✅ 接口限流 - 基于Redis的滑动窗口限流
- ✅ 成本监控 - 多级成本阈值告警
- ✅ 重试机制 - Spring Retry集成
- ✅ 分布式锁 - Redis分布式锁

## 快速开始

### 环境要求
- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+

### 配置步骤

1. **创建数据库**
```sql
CREATE DATABASE gdut_kyx CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. **配置环境变量**
```bash
# Linux/Mac
export SPRING_AI_DASHSCOPE_API_KEY=your-api-key
export SPRING_AI_DASHSCOPE_APP_ID=your-app-id

# Windows
set SPRING_AI_DASHSCOPE_API_KEY=your-api-key
set SPRING_AI_DASHSCOPE_APP_ID=your-app-id
```

3. **修改数据库配置**
编辑 `src/main/resources/application.properties`:
```properties
spring.datasource.username=your-username
spring.datasource.password=your-password
```

4. **启动应用**
```bash
mvn spring-boot:run
```

### API接口

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/ai/chat` | POST | AI聊天接口 |
| `/api/courses` | GET | 获取课程列表 |
| `/api/courses/{id}` | GET | 获取课程详情 |
| `/api/search/hot` | GET | 获取热词排行 |
| `/api/recommend` | GET | 获取推荐课程 |

## 项目结构

```
src/main/java/com/gdut/gxk/
├── controller/          # REST API控制层
├── service/             # 业务逻辑层
│   └── impl/            # 具体实现
├── mapper/              # MyBatis Mapper
├── entity/              # 数据库实体
├── DTO/                 # 数据传输对象
├── VO/                  # 视图对象
├── config/              # 配置类
├── tool/                # AI工具
├── exception/           # 异常处理
└── listener/            # 事件监听器
```

## 安全说明

⚠️ **重要**: 
- API Key 请通过环境变量配置，不要硬编码
- 数据库密码建议使用环境变量
- 生产环境请配置HTTPS

## 许可证

MIT License