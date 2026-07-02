# 动画示例 1:1 复刻修复计划

## 概述
用户反馈示例18（spinner公转）、示例19（拖拽不响应）、示例25（礼花旋转异常）、示例30（下雨诡异）均未正确复刻 Kotlin 原版。需要修复 Kotlin 层 API 缺陷并重写 Lua 示例，实现 1:1 完美复刻。

## 现状分析

### 示例18 Spinner 公转根因
1. **父Box未居中**：Canvas 放在 `fillMaxWidth().height(280)` 的 Box 内，未设置 `contentAlignment = "Center"`，Canvas 位于左上角
2. **旋转实现问题**：`DrawScopeWrapper.rotate(Double, Double, Double)` 使用 `drawContext.transform.translate/rotate/translate` 手动矩阵变换，虽然数学上正确，但缺少 canvas save/restore 保护，且单参数 `rotate(Double)` 绕原点旋转（与 Kotlin `DrawScope.rotate()` 默认绕中心不同）
3. **不必要的 continuousRedraw**：原版 InfiniteTransition 每帧驱动重组，Canvas 自动重绘，不需要额外帧循环
4. **Kotlin原版关键**：父Box `contentAlignment = Alignment.Center`，Canvas 使用 `DrawScope.rotate(degrees) { drawArc }`（默认中心pivot，块形式自动恢复）

### 示例19 Drag 不响应根因
1. **单位不匹配**：`offset(x,y)` 是 dp 单位（`x.dp, y.dp`），而 `dragAmount` 是 px 像素增量。高密度屏幕上 1dp=3px，导致方块要么不动要么飞出屏幕
2. **原版使用px offset**：Kotlin 用 `Modifier.offset { IntOffset(px, px) }`（lambda版，px单位）
3. **居中缺失**：拖拽方块初始位置应居中，不是左上角
4. **修复方案**：使用 `offsetLambda`（px单位）替代 `offset(dp)`

### 示例25 Confetti 礼花旋转异常根因
1. **粒子尺寸dp/px未换算**：`PARTICLE_W_DP=7, PARTICLE_H_DP=14` 直接当像素用，3x密度屏幕上粒子只有7×14像素（应该21×42px）
2. **dt硬编码16ms**：物理更新固定16ms，掉帧时速度不对
3. **旋转恢复不可靠**：用 `rotate(θ,p.x,p.y); draw; rotate(-θ,p.x,p.y)` 手动恢复，不如 canvas save/restore 可靠
4. **原版关键**：粒子尺寸通过 `LocalDensity.current.dp.toPx()` 换算为像素；物理在 LaunchedEffect 中用 `withFrameNanos` 驱动，使用真实dt；旋转使用 `rotate(degrees, pivot) { drawRect }` 块形式自动save/restore

### 示例30 Rainy 下雨诡异根因
1. **ANGLE_DEG完全错误**：Lua用15度，原版是100度（90度=垂直向下，>90度=向左斜）
2. **方向向量错误**：Lua用 `dx=cos(15°), dy=-sin(15°)`，dy为负值向上。原版用 `dirX=cos(100°)≈-0.17, dirY=sin(100°)≈0.98`（向下略向左）
3. **只更新y不更新x**：雨滴只有垂直运动没有水平运动
4. **飞溅位置错误**：在endX/endY（雨线上端）绘制，应该在head（下端触底位置）
5. **缺少渐变背景**：原版有深蓝渐变夜空
6. **缺少渐变拖尾**：原版雨滴线从透明尾部渐变到亮蓝色头部
7. **速度/长度/线宽未做dp→px换算**
8. **初始化硬编码400×400**：未使用spawnDrop函数
9. **飞溅不是单点**：原版只在触底瞬间(headY在[height-2,height+2])绘制一个小圆点，不是4个粒子

## 修复方案

### Kotlin 层修改

#### 1. DrawScopeWrapper.kt — 修复旋转API
- 添加 `save()` / `restore()` 方法，调用 `drawContext.canvas.save()` / `drawContext.canvas.restore()`
- 修改单参数 `rotate(degrees: Double)` 改为绕画布中心旋转（与Kotlin默认一致），实现方式：`translate(w/2, h/2); rotate(degrees); translate(-w/2, -h/2)`
- 但需要知道画布尺寸——在onDraw时传入了size，所以rotate无法直接获取center
- 替代方案：不改变单参数rotate行为，而是添加 `rotateCenter(degrees: Double)` 方法绕中心旋转
- 更优方案：保持API简洁，确保 `rotate(degrees, pivotX, pivotY)` 正确工作，添加save/restore
- **最终决定**：添加 `save()`/`restore()`，保持现有rotate方法签名不变（避免破坏其他代码），让Lua端显式使用pivot旋转+save/restore

#### 2. ModifierChain.kt — 添加 offsetPx 便捷方法
- 添加 `fun offsetPx(x: Float, y: Float): ModifierChain`，内部使用 `modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }`（px单位的lambda版本）
- 这比让Lua用户理解offsetLambda更直观

#### 3. DrawScopeWrapper.kt — 添加带alpha的颜色绘制支持
- 检查现有drawLine/drawRect/drawCircle是否支持颜色alpha——颜色值的高8位就是alpha，传入 `(color & 0x00FFFFFF) | (a << 24)` 即可，toColor()已正确处理Long/Int颜色，所以这已支持
- drawLine渐变版本需要支持alpha——现有7参数版本 `drawLine(sx,sy,ex,ey,c1,c2,strokeWidth)` 中c1/c2的alpha会被toColor正确解析

### Lua 示例层修改

#### 1. 18_loading_spinner.lua 重写
- 父Box添加 `contentAlignment = "Center"`
- 使用 `draw.save(); draw.rotate(rotation, w/2, h/2); draw.drawArcStroke(...); draw.restore()` 替代无保护的rotate
- 去掉 `continuousRedraw = true`（InfiniteTransition已驱动重组）
- 旋转值从InfiniteTransition接收已是Double（之前已修复）
- Canvas使用 `.align("Center")` 或父Box设置contentAlignment

#### 2. 19_spring_drag.lua 重写
- 使用 `compose.LocalDensity.density` 确认单位
- 拖拽偏移使用 `offsetLambda` 或新增的 `offsetPx`（px单位）
- dragAmount是px，直接累加到px偏移量上
- 父Box添加 `contentAlignment = "Center"` 使方块初始居中
- 使用Animatable+snapTo/animateTo（原版方式），但需要通过offsetLambda响应式读取。由于当前架构中Animatable.getValue()不触发重组，需要改用mutableState方案：
  - 拖拽中：用compose.state()存储offset，snapTo语义直接设值
  - 松手回弹：用animateFloatAsState驱动
  - 或者使用offsetLambda回调中读取Animatable.value（需要recomposeTrigger）
- **最优方案**：用 `compose.state()` 存储当前偏移，拖拽时直接更新state值（px），松手时将state设为0但用animateFloatAsState动画到0（与现有方案类似，但offset改用offsetPx/offsetLambda传px值）

#### 3. 25_confetti.lua 重写
- 使用 `compose.LocalDensity.density` 将dp转px（PARTICLE_W_DP*density, PARTICLE_H_DP*density）
- 物理循环使用真实dt（通过记录上次更新时间计算）
- 每个粒子绘制使用 `draw.save(); draw.rotate(p.rotation, p.x, p.y); draw.drawRect(...); draw.restore()`
- 旋转恢复使用save/restore而非反向rotate
- 粒子死亡条件增加y>height+120（原版有）

#### 4. 30_rainy.lua 完全重写
- 参照Kotlin原版AnimationExample21.kt逐行复刻
- ANGLE_DEG=100，dirX=cos(angleRad), dirY=sin(angleRad)
- 使用compose.LocalDensity.density做dp→px换算
- 背景用drawRectVerticalGradient绘制深蓝渐变
- 雨滴同时更新x和y位置
- 雨滴线用7参数drawLine（从透明尾到亮蓝头的渐变）
- 飞溅只在headY在[h-2, h+2]时绘制一个小圆点（不是4个）
- 初始化使用spawnDrop函数正确随机分布
- 生成区域考虑horizontalDrift（斜雨覆盖整个屏幕宽度）
- 使用timeSec计算dt（替代硬编码0.016）
- Canvas用fillMaxSize，外层Box设置正确尺寸

## 文件修改清单

| 文件 | 修改类型 | 说明 |
|------|---------|------|
| `core/.../draw/DrawScopeWrapper.kt` | 修改 | 添加save()/restore()方法 |
| `core/.../modifier/ModifierChain.kt` | 修改 | 添加offsetPx(x,y)方法 |
| `docs/lua_examples/18_loading_spinner.lua` | 重写 | 居中、save/restore、去掉continuousRedraw |
| `docs/lua_examples/19_spring_drag.lua` | 重写 | 使用offsetPx/offsetLambda、居中、px单位 |
| `docs/lua_examples/25_confetti.lua` | 重写 | dp→px、真实dt、save/restore |
| `docs/lua_examples/30_rainy.lua` | 完全重写 | 1:1复刻Kotlin原版 |

## 验证步骤
1. 编译 :core:compileDebugKotlin 确认无错误
2. 部署到设备后逐个测试：
   - 示例18：圆弧绕自身中心旋转+扫角伸缩，无公转，居中显示
   - 示例19：拖拽方块跟手移动，松手弹簧回弹至中心
   - 示例25：点击发射彩色粒子，粒子自转、飘落、渐隐，尺寸正常
   - 示例30：斜向左下的雨，有渐变拖尾，触底有小飞溅点，速度有层次感
