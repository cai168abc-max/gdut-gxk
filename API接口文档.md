# 广东工业大学课程评价系统 API 接口文档

## 概述

本系统提供完整的课程评价功能，包括课程列表、详情查看、评论管理、AI对话、数据统计等模块。

## 基础信息

- **基础URL**: `http://localhost:8080/api`
- **响应格式**: JSON
- **字符编码**: UTF-8
- **跨域支持**: 已配置（支持特定域名）

## CORS配置说明

系统提供两种AI对话方案：

### 方案一：基于Session的AI对话（需要Cookie）
- **接口路径**: `/api/ai/*`
- **特点**: 使用HttpSession管理对话上下文
- **CORS配置**: 允许特定域名，支持Cookie
- **适用场景**: 需要用户登录、会话管理的场景

### 方案二：无状态AI对话（无需Cookie）
- **接口路径**: `/api/ai/stateless/*`
- **特点**: 使用contextId管理对话上下文，无需Cookie
- **CORS配置**: 支持通配符域名
- **适用场景**: 无需用户登录、跨域访问的场景

## 统一响应格式

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": 1703123456789
}
```

## 1. 课程列表模块

### 1.1 多条件筛选课程列表
- **接口**: `POST /api/course/list`
- **描述**: 支持关键词搜索、类别筛选、校区筛选、标签筛选、分页查询
- **请求参数**:
```json
{
  "keyword": "Python",
  "category": "自然科学与工程技术类",
  "campus": "龙洞校区",
  "tag": "给分高",
  "pageNum": 1,
  "pageSize": 10
}
```
- **响应数据**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "courseId": "course_id_123",
        "courseName": "Python编程基础",
        "teacherName": "张老师",
        "score": 4.5,
        "tag": "好评,给分高",
        "credit": 3.0
      }
    ],
    "total": 100,
    "size": 10,
    "current": 1,
    "pages": 10
  }
}
```

### 1.2 获取默认课程列表
- **接口**: `GET /api/course/default`
- **描述**: 获取前100门课程（首页默认显示）

### 1.3 简单搜索
- **接口**: `GET /api/course/search`
- **参数**: `keyword`, `pageNum`, `pageSize`

## 2. 课程详情模块

### 2.1 获取课程详情
- **接口**: `GET /api/course/detail/{courseId}`
- **描述**: 获取课程完整信息，包括基础信息、时间安排、学分、总评论数等
- **响应数据**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "courseId": "course_id_123",
    "courseName": "Python编程基础",
    "teacherName": "张老师",
    "score": 4.5,
    "campus": "龙洞校区",
    "college": "计算机学院",
    "aiSummary": "这是一门优秀的编程课程...",
    "tag": "好评,给分高",
    "credit": 3.0,
    "courseTime": "周二第3-4节",
    "courseSchedule": "全学期线下",
    "totalCommentCount": 25
  }
}
```

### 2.2 获取课程评论列表
- **接口**: `GET /api/course/detail/{courseId}/comments`
- **参数**: `pageNum`, `pageSize`
- **响应数据**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "commentId": 1,
        "courseId": "course_id_123",
        "attendanceFrequency": "偶尔",
        "examType": "闭卷",
        "score": 5,
        "content": "这门课程很不错，老师讲解详细",
        "createTime": "2023-12-01T10:00:00"
      }
    ],
    "total": 25,
    "size": 10,
    "current": 1,
    "pages": 3
  }
}
```

### 2.3 新增课程评论
- **接口**: `POST /api/course/detail/{courseId}/comments`
- **请求参数**:
```json
{
  "attendanceFrequency": "偶尔",
  "examType": "闭卷",
  "score": 5,
  "content": "这门课程很不错，老师讲解详细，推荐！"
}
```

### 2.4 删除课程评论
- **接口**: `DELETE /api/course/detail/{courseId}/comments/{commentId}`

## 3. AI对话模块

### 3.1 基于Session的AI对话（需要Cookie）

#### 3.1.1 AI对话
- **接口**: `POST /api/ai/chat`
- **描述**: 与AI进行对话，支持上下文管理（基于HttpSession）
- **请求参数**:
```json
{
  "userInput": "推荐一些Python相关的课程",
  "contextId": "session_id_123",
  "clearHistory": false
}
```

#### 3.1.2 获取对话历史
- **接口**: `GET /api/ai/history`

#### 3.1.3 清除对话历史
- **接口**: `DELETE /api/ai/history`

#### 3.1.4 快速对话
- **接口**: `GET /api/ai/quick`
- **参数**: `question`

#### 3.1.5 获取AI服务状态
- **接口**: `GET /api/ai/status`

### 3.2 无状态AI对话（无需Cookie）

#### 3.2.1 AI对话
- **接口**: `POST /api/ai/stateless/chat`
- **描述**: 与AI进行对话，支持上下文管理（基于contextId）
- **请求参数**:
```json
{
  "userInput": "推荐一些Python相关的课程",
  "contextId": "ctx_abc123def456",
  "clearHistory": false,
  "userId": "user_001"
}
```
- **响应数据**:
```json
{
  "code": 200,
  "message": "AI回复生成成功",
  "data": {
    "aiResponse": "根据您的查询，我为您找到了以下相关信息：\n\n相关课程：\n- Python编程基础（张老师）\n\n学生评价：\n- 这门Python课程很不错，老师讲解很详细，推荐！\n\n注：这是模拟回复，实际AI功能需要接入真实的AI服务。",
    "contextId": "ctx_abc123def456",
    "relatedCourses": [...],
    "relatedComments": [...],
    "processingTime": 150,
    "fromCache": false
  }
}
```

#### 3.2.2 获取对话历史
- **接口**: `GET /api/ai/stateless/history/{contextId}`

#### 3.2.3 清除对话历史
- **接口**: `DELETE /api/ai/stateless/history/{contextId}`

#### 3.2.4 生成新的上下文ID
- **接口**: `POST /api/ai/stateless/context/new`

#### 3.2.5 快速对话
- **接口**: `GET /api/ai/stateless/quick`
- **参数**: `question`

#### 3.2.6 获取AI服务状态
- **接口**: `GET /api/ai/stateless/status`

## 4. 数据统计模块

### 4.1 获取总结统计数据
- **接口**: `GET /api/summary/data`
- **参数**: `campus`, `category`
- **响应数据**:
```json
{
  "code": 200,
  "message": "获取统计数据成功",
  "data": {
    "totalCourseCount": 500,
    "totalCommentCount": 1200,
    "hotCoursesByComment": [...],
    "popularCoursesByScore": [...],
    "topSearchKeywords": [...]
  }
}
```

### 4.2 获取基础统计信息
- **接口**: `GET /api/summary/basic`

### 4.3 获取热门课程
- **接口**: `GET /api/summary/hot-courses`

### 4.4 获取人气课程
- **接口**: `GET /api/summary/popular-courses`

### 4.5 获取高频搜索词
- **接口**: `GET /api/summary/hot-keywords`

### 4.6 获取统计概览
- **接口**: `GET /api/summary/overview`

## 5. 搜索功能模块

### 5.1 记录搜索行为
- **接口**: `POST /api/search/record`
- **参数**: `type`, `value`
- **描述**: 记录用户搜索行为，增加搜索词计数

### 5.2 获取热门搜索词（按类型）
- **接口**: `GET /api/search/hot/{type}`
- **参数**: `limit`

### 5.3 获取所有类型热门搜索词
- **接口**: `GET /api/search/hot/all`

### 5.4 获取搜索统计信息
- **接口**: `GET /api/search/stats`

### 5.5 批量记录搜索行为
- **接口**: `POST /api/search/record/batch`

## 错误码说明

| 错误码 | 说明 |
|--------|------|
| 200 | 操作成功 |
| 400 | 参数错误 |
| 401 | 未授权 |
| 404 | 资源未找到 |
| 500 | 服务器内部错误 |

## 注意事项

1. **AI对话功能**: 当前为模拟实现，需要替换 `AIChatServiceImpl.generateMockAIResponse()` 方法为真实的AI API调用
2. **缓存机制**: 系统使用Redis+Caffeine二级缓存，提高响应速度
3. **分页查询**: 所有列表接口都支持分页，默认每页10条，最大50条
4. **参数校验**: 使用Hibernate Validator进行参数校验
5. **跨域支持**: 
   - 基于Session的AI对话：支持特定域名，需要Cookie
   - 无状态AI对话：支持通配符域名，无需Cookie
6. **异常处理**: 全局异常处理器统一处理各种异常情况
7. **会话管理**: 
   - 方案一：使用HttpSession，适合需要用户登录的场景
   - 方案二：使用contextId，适合无状态、跨域访问的场景

## 部署说明

1. 确保MySQL数据库运行在localhost:3306
2. 确保Redis服务运行在localhost:6379
3. 数据库名称为`gdut_kyx`
4. 启动应用后访问`http://localhost:8080`

## AI功能接入指南

要接入真实的AI服务，需要修改以下文件：

1. **AIChatServiceImpl.java**:
   - 替换 `generateMockAIResponse()` 方法
   - 替换 `extractQueryConditions()` 方法（使用更复杂的NLP处理）
   - 替换 `queryRelatedCourses()` 和 `queryRelatedComments()` 方法（使用实际数据库查询）

2. **添加AI服务配置**:
   - 在application.properties中添加AI服务相关配置
   - 创建AI服务客户端类

3. **示例AI API调用**:
```java
private String callAIService(String userInput, List<CourseBase> courses, List<CourseComment> comments) {
    // 构建AI请求
    AIRequest request = new AIRequest();
    request.setUserInput(userInput);
    request.setContext(courses, comments);
    
    // 调用AI服务
    AIResponse response = aiClient.chat(request);
    
    return response.getContent();
}
```

