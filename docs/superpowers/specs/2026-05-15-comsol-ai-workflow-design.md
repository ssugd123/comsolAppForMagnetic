# COMSOL 6.4 二次开发 AI 辅助工作流设计

## 背景

COMSOL 二次开发模式：App 开发器 + 外部 Java JAR，产出一个 `.mph` 文件 + 一份 Java 源码。

**痛点**：现有 AI 工具只能管理 Java 代码（`src/`），无法管理 `.mph` 内部的模型、几何、参数、表单、method。

**目标**：建立一套方法，让 AI 能读懂 `.mph` 完整内容、指导人工修改、自动验证修改结果。

## 项目结构

```
comsol_claude/
├── _mph_extract/dmodel.xml    # [只读] .mph 解压出的模型 XML，AI 读取+diff
├── docs/mph-methods/*.java    # [可写] 手写 method 源码，人工复制，AI 读取
├── src/main/java/             # [可写] 外部 JAR 源码，AI 直接编辑
├── _pdfs/                     # [只读] COMSOL 编程参考文档
└── docs/guides/               # [可写] AI 输出的修改指南+验证报告
```

## 信息模型

| 信息源 | 内容 | AI 操作 | 维护方式 |
|--------|------|---------|----------|
| `_mph_extract/dmodel.xml` | 参数/几何/物理/材料/网格/求解器/表单/事件绑定/method 名称 | 读取、diff | AI 自动解压 .mph 刷新 |
| `docs/mph-methods/*.java` | 手写 method 的 Java 源码 | 读取 | 人工从 App Builder 复制 |
| `src/main/java/` | 外部 JAR Java 源码 | 读取、编辑 | 正常开发流程 |
| `_pdfs/` | COMSOL API 参考文档 | 查 API | 手动存放 |

### dmodel.xml 可读性

.mph 文件是 ZIP 包。解压得 `dmodel.xml`（约 5.7 万行），包含：

- **全局参数**：`<expressions name="CORE_EE_D1" expr="11.15" descr="D1(mm)">`
- **几何体**：`<GeomFeature op="Block" tag="blk1">` 含尺寸、位置
- **物理场**：`<PhysicsFeature op="AmperesLawSolid" tag="als1">` 含域选择
- **材料**：`<MaterialList>` 含 BH 曲线表
- **表单控件**：`<propertyValue name="p:onDataChange" Reference="Core_tyep">`
- **Method 名称**：`<classNames>builder.Core_tyep</classNames>`
- **Method 源码**：`<classByteCode>` base64 JVM 字节码 — **不可读**，需人工复制源码到 `docs/mph-methods/`

## 开发工作流

### 职责划分

| 任务 | 执行者 |
|------|--------|
| 解压 .mph → 读取 dmodel.xml | AI |
| 读 src/ + mph-methods/ + PDF | AI |
| 查 COMSOL API 用法 | AI |
| 输出修改指南 | AI |
| COMSOL Desktop GUI 操作 | 人工 |
| 编辑 Java 代码 | AI（Claude Code） |
| 复制 method 源码到 mph-methods/ | 人工 |
| 重新解压 .mph → diff 验证 | AI |
| 输出验证报告 | AI |

### 流程图

```
需求
  │
  ▼
AI: 读取现状
  - 解压 .mph → dmodel.xml
  - 读 src/ + mph-methods/ + _pdfs/
  - 理解当前模型/表单/参数/方法
  │
  ▼
AI: 输出《修改指南》→ docs/guides/<日期>-<主题>.md
  格式：位置 | 操作 | 预期结果
  │
  ▼
人工: COMSOL GUI 操作 + 复制 method 源码
AI:  编辑 Java 代码
  │
  ▼
AI: 重新解压 .mph → dmodel.xml
  │
  ▼
AI: diff dmodel.xml + 检查一致性
  │
  ├── 通过 → 输出验证报告 ✅
  └── 不通过 → 输出修正指南 → 回到人工执行
```

## 修改指南格式

每次需求，AI 输出一份指南，分三栏：

```markdown
# 修改指南: <主题>

## COMSOL Desktop 操作

### 全局参数
| 位置 | 操作 | 预期 |
|------|------|------|
| Model Builder → 参数 → 参数组 EE | 新增参数 CORE_EE_D9 = 8.0[mm] | 参数列表出现 CORE_EE_D9 |

### 几何 Part
| 位置 | 操作 | 预期 |
|------|------|------|
| 几何 → Part → ee_core | 修改 Block 尺寸绑定 CORE_EE_D9 | 几何渲染到位 |

### 表单
| 位置 | 操作 | 预期 |
|------|------|------|
| App Builder → Core_parameters → CardStack EE | 新增 TextFieldWidget，绑定 D9 | 表单控件出现 |
| TextFieldWidget D9 | onDataChange → Core_param_changed | 事件绑定正确 |

## Java 代码改动

### src/main/java/.../CoreTypeDescriptors.java
- 行X：新增 `CORE_EE_D9` 到 EE 参数列表

### docs/mph-methods/Core_param_changed.java
- 行Y：新增 D9 的 setEntry
```

## 验证规则

AI 执行验证时检查：

1. **参数完整性**：dmodel.xml 中新增参数存在，expr 值正确
2. **表单控件**：TextFieldWidget 出现，name 正确
3. **事件绑定**：onDataChange Reference 指向正确的 method
4. **几何一致性**：Part 参数绑定名与实际参数名匹配
5. **Java 对应**：CoreTypeDescriptors 的参数列表与 dmodel.xml 参数组一致
6. **Method 对应**：mph-methods/ 源码与 dmodel.xml 中 className 一致

验证通过标准：全部检查项为 ✅。

## Method 源码维护

- Method 在 COMSOL Application Builder 中编辑，编译为字节码存入 .mph
- 每次新建或修改 method 后，人工将源码复制到 `docs/mph-methods/<method名>.java`
- Method 逻辑尽量薄，委托给外部 JAR 的 `AppEntry` 静态方法
- AI 通过读取 mph-methods/ 理解 method 逻辑，通过 dmodel.xml 的事件引用了解调用关系

## .mph 解压操作

```bash
# .mph 是 ZIP 格式，解压即可
unzip -o Aonesoft_App_Demo_V1.1.mph -d _mph_extract/
# dmodel.xml 在 _mph_extract/ 根目录
# 嵌入的 JAR 在 _mph_extract/resources/
```
AI 在 Step 1（读取现状）和 Step 5（验证）时执行此操作。

## 测试策略

- **Java 侧**：JUnit 5 + stub 接口（ModelAccess、CoreModelAccess），无 COMSOL 运行时依赖
- **.mph 侧**：人工在 COMSOL Desktop 中打开验证（表单交互、几何刷新、求解器运行）
- **一致性**：AI diff dmodel.xml 检查参数/控件/事件绑定
