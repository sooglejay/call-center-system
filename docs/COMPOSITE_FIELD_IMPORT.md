# 复合字段导入功能说明

## 功能概述

智能识别和拆分 CSV/Excel 中的复合字段（单元格内包含多个值，用分隔符分隔），自动映射到系统字段。

## 典型场景

用户数据格式如下：

| First Name | Last Name | Mobile Phone | E-mail Address |
|------------|-----------|--------------|----------------|
| 01 | 悟空美購商城,徐思懿,Siyi Xu | 13800138001 | siyi@example.com |
| 01 | 悟空美購商城,嚴嚴舟,yan yan zhou | 13800138002 | yan@example.com |

其中 `Last Name` 列是一个复合字段，包含了：
- 第1部分：机构名称（"悟空美購商城"）
- 第2部分：中文姓名（"徐思懿"）
- 第3部分：英文姓名（"Siyi Xu"）

## 自动识别逻辑

系统会自动检测：
1. **分隔符类型**：逗号、分号、竖线等
2. **子字段数量**：所有行的部分数量是否一致
3. **子字段类型**：
   - **机构名**：所有行的值完全相同
   - **中文姓名**：2-10个汉字
   - **英文姓名**：只包含英文字母和空格
   - **邮箱**：符合邮箱格式
   - **电话**：符合电话号码格式

## 使用流程

### 1. 上传文件

支持格式：
- CSV（UTF-8 编码）
- Excel (.xls, .xlsx)

### 2. 预览与识别

系统自动分析并显示：
```
检测到复合字段：
- Last Name 包含 3 个子字段（分隔符: ","）
  [公司/机构] 悟空美購商城
  [姓名] 徐思懿
  [英文名] Siyi Xu
```

### 3. 配置映射

系统提供智能建议，也可手动选择：
- 姓名 ← Last Name_part_2（自动建议）
- 电话 ← Mobile Phone
- 邮箱 ← E-mail Address
- 公司 ← Last Name_part_1（自动建议）

映射选项包括：
- 普通列（如 Mobile Phone）
- 复合字段的子字段（标记为"拆分"）

### 4. 执行导入

确认映射后执行导入，系统会：
- 自动拆分复合字段
- 提取每个子字段的值
- 存储到对应的系统字段

## API 接口

### 预览接口

```http
POST /api/data-import/preview
Authorization: Bearer {token}
Content-Type: multipart/form-data

file: {csv/excel文件}
```

**响应示例：**
```json
{
  "columns": ["First Name", "Last Name", "Mobile Phone", "E-mail Address"],
  "preview": [...],
  "composite_fields": {
    "Last Name": {
      "separator": ",",
      "partCount": 3,
      "subFields": [
        {
          "key": "Last Name_part_1",
          "label": "公司/机构",
          "type": "company",
          "samples": ["悟空美購商城", "悟空美購商城"]
        },
        {
          "key": "Last Name_part_2",
          "label": "姓名",
          "type": "name",
          "samples": ["徐思懿", "嚴嚴舟"]
        },
        {
          "key": "Last Name_part_3",
          "label": "英文名",
          "type": "name_en",
          "samples": ["Siyi Xu", "yan yan zhou"]
        }
      ]
    }
  },
  "suggestions": {
    "name": "Last Name_part_2",
    "phone": "Mobile Phone",
    "email": "E-mail Address",
    "company": "Last Name_part_1"
  }
}
```

### 导入接口

```http
POST /api/data-import/import
Authorization: Bearer {token}
Content-Type: multipart/form-data

file: {csv/excel文件}
column_mapping: {
  "name": "Last Name_part_2",
  "phone": "Mobile Phone",
  "email": "E-mail Address",
  "company": "Last Name_part_1"
}
composite_fields: {
  "Last Name": {
    "separator": ",",
    "partCount": 3
  }
}
data_source: real
```

**响应示例：**
```json
{
  "message": "导入完成",
  "summary": {
    "total": 5,
    "imported": 5,
    "duplicates": 0,
    "errors": 0
  }
}
```

## 支持的分隔符

- 逗号 `,`
- 中文逗号 `，`
- 分号 `;`
- 中文分号 `；`
- 竖线 `|`
- 斜杠 `/`
- 反斜杠 `\`
- 制表符 `\t`

## 注意事项

### CSV 格式要求

如果单元格内容包含分隔符，必须用引号包裹：

**正确格式：**
```csv
First Name,Last Name,Mobile Phone,E-mail Address
01,"悟空美購商城,徐思懿,Siyi Xu",13800138001,siyi@example.com
```

**错误格式：**
```csv
First Name,Last Name,Mobile Phone,E-mail Address
01,悟空美購商城,徐思懿,Siyi Xu,13800138001,siyi@example.com
```
（这会导致列数不一致，无法正确解析）

### 数据一致性

所有行的复合字段必须：
- 包含相同数量的部分
- 使用相同的分隔符

否则系统不会将其识别为复合字段。

## 示例代码

### cURL 测试

```bash
# 1. 登录获取 token
TOKEN=$(curl -s -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.token')

# 2. 预览文件
curl -X POST \
  -F "file=@test-composite.csv" \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8081/api/data-import/preview | jq '.'

# 3. 导入数据
curl -X POST \
  -F "file=@test-composite.csv" \
  -F 'column_mapping={"name":"Last Name_part_2","phone":"Mobile Phone","email":"E-mail Address","company":"Last Name_part_1"}' \
  -F 'composite_fields={"Last Name":{"separator":",","partCount":3}}' \
  -F "data_source=real" \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8081/api/data-import/import | jq '.'
```

## 实现原理

### 后端实现

1. **CSV 解析**：使用 `csv-parser` 库，自动处理引号包裹的字段
2. **复合字段检测**：
   - 遍历每列的前10行数据
   - 检查是否所有行都包含相同的分隔符
   - 检查拆分后的部分数量是否一致
3. **子字段类型识别**：
   - 检查值是否完全相同（机构名特征）
   - 正则匹配中文姓名、英文姓名、邮箱、电话
4. **导入处理**：
   - 根据列映射配置，判断是否为复合字段的子字段
   - 按分隔符拆分父字段值
   - 提取指定索引的子值

### 前端实现

1. **预览展示**：
   - Alert 提示检测到的复合字段
   - 显示子字段标签和示例值
2. **映射配置**：
   - Select 组件支持选择普通列或复合字段的子字段
   - 子字段选项标记为"拆分"
   - 自动建议映射
3. **导入请求**：
   - 发送列映射配置
   - 发送复合字段配置（分隔符和部分数）

## 扩展性

可以轻松扩展支持：
- 更多分隔符类型
- 更多的子字段类型识别规则
- 自定义字段映射规则
- 导入模板配置
