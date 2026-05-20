# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 构建 / 测试

```bash
mvn compile              # 编译主源码 (Java 1.8)
mvn test                 # 运行所有测试 (JUnit 5)
mvn test -pl . -Dtest=AuthServiceTest    # 运行单个测试类
mvn package              # 生成 target/comsol-ext-1.0.0.jar
```

目标平台 Java 1.8，兼容 COMSOL 6.3。编译时不依赖 COMSOL API — JAR 嵌入在 `.mph` 文件中，在 COMSOL Desktop 内运行，运行时 COMSOL 类可用。

## 产品概述

本产品是**磁性器件全自动化电磁场仿真平台**，基于 COMSOL Multiphysics 二次开发。支持电感器和变压器的全参数化、自动化有限元仿真，涵盖 PQ、EE、RING 等磁芯类型，圆形/方形/螺旋线/空间螺旋线绕组类型，以及稳态/频域/瞬态三种求解器。

## 架构

### 整体结构

```
comsol_claude/
├── src/main/java/             # [可写] 外部 JAR 源码，AI 直接编辑
├── docs/mph-methods/*.java    # [可写] COMSOL Method 手写源码，人工从 App Builder 复制
├── _mph_extract/dmodel.xml    # [只读] .mph 解压出的模型 XML（参数/几何/物理/材料/Method名称）
├── _pdfs/                     # [只读] COMSOL 编程参考文档
├── docs/guides/               # [可写] AI 输出的修改指南+验证报告
└── Aonesoft_App_Demo_V1.1.mph # COMSOL App 文件（ZIP 格式，内含 dmodel.xml + 嵌入 JAR）
```

### 信息源与 AI 操作权限

| 信息源 | 内容 | AI 操作 |
|--------|------|---------|
| `_mph_extract/dmodel.xml` | 参数/几何/物理/材料/Method 名称/事件绑定 | 读取、diff |
| `docs/mph-methods/*.java` | COMSOL Method 的 Java 源码 | 读取 |
| `src/main/java/` | 外部 JAR Java 源码 | 读取、编辑 |
| `_pdfs/` | COMSOL API 参考文档 | 查 API |





## 开发工作流（AI-人工协作）

参考 `docs/superpowers/specs/2026-05-15-comsol-ai-workflow-design.md`：

1. **AI 读取现状** — 解压 .mph → dmodel.xml，读 src/ + mph-methods/
2. 理解_pdfs\02_Comsol_Help_PDF下个文档
3. **AI 输出修改指南** → `docs/guides/<日期>-<主题>.md`（格式：位置 | 操作 | 预期结果）
4. **人工 COMSOL GUI 操作** + 复制 method 源码到 mph-methods/
5. **AI 编辑 Java 代码** (src/main/java/)
6. **AI 重新解压 .mph → diff dmodel.xml** → 验证参数/控件/事件绑定一致性
7. **通过 → 输出验证报告** / **不通过 → 输出修正指南**

### COMSOL Method 源码维护规则

- Method 逻辑尽量薄，委托给 `AppEntry` 静态方法
- Method 在 App Builder 中编辑，编译为字节码存入 .mph
- AI 通过 mph-methods/ 理解 method 逻辑，通过 dmodel.xml 中 `Core_tyep` 等引用了解调用链
- `.mph` 解压：`unzip -o Aonesoft_App_Demo_V1.1.mph -d _mph_extract/`

### COMSOL API 使用规范

**禁止编造 API。** 输出任何 Method 代码、修改方案时，调用的每个方法必须在以下参考文档中有依据。

#### _pdfs/02_Comsol_Help_PDF 文档索引

| 文档 | 用途 | 日常使用 |
|------|------|---------|
| `COMSOL_ProgrammingReferenceManual.pdf` | Java API 完整参考 — model 节点树所有方法签名 | **核心 — 查 API** |
| `COMSOL_ApplicationBuilderManual.pdf` | App Builder 手册 — Form/控件/事件/Method/声明 | **核心 — 查 UI API** |
| `COMSOL_ReferenceManual.pdf` | 操作界面功能参考 | 查功能位置 |
| `ApplicationProgrammingGuide.pdf` | 外部 Java 调用 COMSOL API | 不常用 |
| `IntroductionToApplicationBuilder.pdf` | App Builder 入门 | 入门参考 |
| `IntroductionToCOMSOLMultiphysics.pdf` | COMSOL 入门教程 | 不常用 |
| `ACDCModuleUsersGuide.pdf` | AC/DC 模块电磁仿真案例 | 物理参考 |
| `COMSOL_PostprocessingAndVisualization.pdf` | 后处理与可视化 | 结果绘图时参考 |
| `MaterialLibraryUsersGuide.pdf` | 材料库说明 | 配置材料时参考 |
| 其余文档 | 安装/许可/发布说明/CFD/CAD/ModelManager | 不需要 |

#### API 验证优先级

1. **首选** — 查 `_mph_extract/dmodel.xml` 中的 `<actions>` 历史记录。每次 COMSOL GUI 操作会留下精确的 API 调用路径（`t(s(...))` 节点遍历 + `m(s(...))` 方法调用），可直接翻译为 Java 代码
2. **次选** — 查已有 Method 源码（`docs/mph-methods/` 和 dmodel.xml 中 `p:code`），已验证可用
3. **兜底** — 查 `_pdfs/` 中的 `COMSOL_ProgrammingReferenceManual.pdf`

#### dmodel.xml history → Java API 映射示例

```
t(s("/component/comp1/physics/mf/feature/als1/selection[geom1]"))
m(s("named")) s("geom1_pi1_sel1_dom")
→ model.component("comp1").physics("mf").feature("als1").selection().named("geom1_pi1_sel1_dom")

t(s("/component/comp1/geom/geom1")) m(s("run")) s("fin")
→ model.component("comp1").geom("geom1").run("fin")

t(s("/component/comp1/geom/geom1/feature/pi1")) m(s("set")) s("part") s("part2")
→ model.component("comp1").geom("geom1").feature("pi1").set("part", "part2")
```

## 测试模式

所有测试使用 JUnit 5。无需 COMSOL 运行时 — 测试使用 `ModelAccess` / `CoreModelAccess` 的 stub 实现，记录调用以供验证。

Java 侧：JUnit 5 + stub 接口，无 COMSOL 运行时依赖。
.mph 侧：人工在 COMSOL Desktop 中打开验证（表单交互、几何刷新、求解器运行）。
一致性：AI diff dmodel.xml 检查参数/控件/事件绑定。

## 产品功能模块（需求文档 v2）

### 模块一：产品选择
- 主界面选择电感器/变压器
- 子界面含菜单栏、功能区、设置栏、图形窗口、日志

### 模块二：结构参数化
- **磁芯类型**：下拉框，PQ/EE/EI 等（目标 20+ 种），切换时 UI 配图 + 三维模型联动
- **绕组类型**：下拉框，圆形/方形/螺旋线/空间螺旋线（4 种）
- **导线类型**：下拉框，单导线/均匀多匝/均质化利兹线，影响"线圈→导线模型"设置
- **装配参数**：控制绕组与磁芯相对位置
- 参数错误或模型干涉时需报错并禁止计算

### 模块三：求解器设置
- 稳态求解器：电压幅值/电流幅值 → 稳态研究步骤
- 频域求解器：电流 & 频率 / 电压 & 频率 → 频域研究步骤
- 瞬态求解器：时变信号 + 时间设置 → 瞬态研究步骤
- 激励类型：电流/电压下拉切换

### 模块四：材料库
- 磁性材料库（磁芯）：BH 曲线、BP 曲线，内置算法计算有效 BH 曲线、Steinmetz 拟合系数
- 金属材料库（绕组）：电导率等
- 支持自定义材料和库中已有材料

### 模块五：结果呈现
- 功能区按钮：几何、网格设置、网格、网格评估、计算、结果处理（云图）
- 选择集、重置窗口布局、清除网格、清除所有解

### 软件加密
- COMSOL Compiler 编译加密
- 硬件指纹验证（MAC + CPU + HDD），启动时运行，不匹配则弹窗关闭

## 该项目无需git管理
