local compose = compose
local setContent = compose.setContent
local state = compose.state
local delay = compose.delay

local Color = compose.ui.graphics.Color
local Offset = compose.ui.geometry.Offset
local Rect = compose.ui.geometry.Rect
local StrokeCap = compose.ui.graphics.StrokeCap
local Brush = compose.ui.graphics.Brush
local RuntimeShader = compose.ui.graphics.RuntimeShader
local RenderEffect = compose.ui.graphics.RenderEffect

local Modifier = compose.Modifier
local Arrangement = compose.Arrangement
local Alignment = compose.Alignment
local RoundedCornerShape = compose.RoundedCornerShape
local CircleShape = compose.CircleShape

local FontWeight = compose.FontWeight

local Column = compose.foundation.layout.Column
local Row = compose.foundation.layout.Row
local Box = compose.foundation.layout.Box
local BoxWithConstraints = compose.foundation.layout.BoxWithConstraints
local Canvas = compose.foundation.Canvas
local Spacer = compose.foundation.layout.Spacer

local material3 = compose.material3
local Text = material3.Text
local MaterialTheme = material3.MaterialTheme

local animation = compose.animation
local LaunchedEffect = compose.LaunchedEffect
local remember = compose.remember
local rememberCoroutineScope = compose.rememberCoroutineScope
local LocalDensity = compose.LocalDensity
local withFrameNanos = compose.withFrameNanos

local animateColorAsState = animation.animateColorAsState
local animateFloatAsState = animation.animateFloatAsState
local Animatable = animation.core.Animatable
local FastOutLinearInEasing = animation.core.FastOutLinearInEasing
local tween = animation.core.tween
local spring = animation.core.spring
local CubicBezierEasing = animation.core.CubicBezierEasing
local Spring = animation.core.Spring
local ui = compose.ui
local Path = ui.graphics.Path
local PathOperation = ui.graphics.PathOperation
local TextAlign = ui.text.style.TextAlign
local detectDragGestures = compose.gestures.detectDragGestures
local detectTapGestures = compose.gestures.detectTapGestures
local IntOffset = compose.ui.unit.IntOffset
local math = math

local function coerceIn(v, minV, maxV)
    if v < minV then
        return minV
    end
    if v > maxV then
        return maxV
    end
    return v
end

local function lerp(start, stop, fraction)
    return (1 - fraction) * start + fraction * stop
end


--    screenHeightPx: Float,
--    val centerX: Float,
--    bottomOrbRatio: Float,
--    topOrbRatio: Float,
--    textYBottomRatio: Float,
--    textYTopRatio: Float,
--    dragOvershootRatio: Float,
local function BubbleState(args)
    local centerX = args["centerX"]
    local bottomOrbCenterY = args["screenHeightPx"] * args["bottomOrbRatio"]
    local topOrbCenterY = args["screenHeightPx"] * args["topOrbRatio"]
    local midPoint = (bottomOrbCenterY + topOrbCenterY) / 2
    local maxDragY = bottomOrbCenterY + (args["screenHeightPx"] * args["dragOvershootRatio"])

    local orbRange = bottomOrbCenterY - topOrbCenterY
    local textYBottom = args["screenHeightPx"] * args["textYBottomRatio"]
    local textYTop = args["screenHeightPx"] * args["textYTopRatio"]

    local bubblePos = Animatable(Offset(centerX, bottomOrbCenterY), Offset.VectorConverter)
    local deformationAnim = Animatable(Offset.Zero, Offset.VectorConverter)
    local popAnim = Animatable(0)
    local themeRevealProgress = Animatable(1)
    local shaderTime = { 0.0 }

    local progress = function(self)
        return coerceIn(((bottomOrbCenterY - bubblePos.value.y) / orbRange), 0, 1)
    end

    local radiusFor = function(self, maxPx, minPx)
        return lerp(maxPx, minPx, self:progress())
    end

    local textYOffsetPx = function(self)
        return lerp(self.textYBottom, self.textYTop, self:progress())
    end

    local isAtTop = function(self, unlockThresholdPx)
        return self.bubblePos.value.y <= self.topOrbCenterY + unlockThresholdPx
    end

    return {
        centerX = centerX,
        bottomOrbCenterY = bottomOrbCenterY,
        topOrbCenterY = topOrbCenterY,
        midPoint = midPoint,
        maxDragY = maxDragY,

        orbRange = orbRange,
        textYBottom = textYBottom,
        textYTop = textYTop,

        bubblePos = bubblePos,
        deformationAnim = deformationAnim,
        popAnim = popAnim,
        themeRevealProgress = themeRevealProgress,
        shaderTime = shaderTime,

        progress = progress,
        radiusFor = radiusFor,
        textYOffsetPx = textYOffsetPx,
        isAtTop = isAtTop,
    }

end

local function radialBrush(screenWidthPx, screenHeightPx, center, mid1, mid2, edge)
    return Brush.radialGradient(
            {
                [0.0] = center,
                [0.3] = mid1,
                [0.7] = mid2,
                [1.0] = edge
            },
            Offset(screenWidthPx / 2.0, screenHeightPx * 0.4),
            math.huge
    )
end

local function coerceAtMost(self, maximumValue)
    return (self > maximumValue) and maximumValue or self

end


--    stateObj: BubbleState,
--    deformationFactor: Float,
--    deformationClamp: Float,
--    velocitySmoothing: Float,
--    stiffness: Float,
--    damping: Float,
local function DeformationFrameLoop(args)
    local stateObj = args["stateObj"]
    local deformationFactor = args["deformationFactor"]
    local deformationClamp = args["deformationClamp"]
    local velocitySmoothing = args["velocitySmoothing"]
    local stiffness = args["stiffness"]
    local damping = args["damping"]

    LaunchedEffect(
            stateObj,
            deformationFactor,
            deformationClamp,
            velocitySmoothing,
            stiffness,
            damping,
            function()
                local previousActualPos = stateObj.bubblePos.value
                local startTime = 0
                withFrameNanos(function(frameTimeNanos)
                    startTime = frameTimeNanos
                end)
                local lastFrameTime = startTime
                local smoothedVelocity = Offset.Zero
                local defVelocity = Offset.Zero

                while true do
                    local frameTime = 0
                    withFrameNanos(function(frameTimeNanos)
                        frameTime = frameTimeNanos
                    end)
                    local dt = coerceAtMost((frameTime - lastFrameTime) / 1000000000, 0.032)
                    lastFrameTime = frameTime
                    stateObj.shaderTime[1] = (frameTime - startTime) / 1000000000

                    local currentActualPos = stateObj.bubblePos.value
                    local rawVelocity = Offset(currentActualPos.x - previousActualPos.x, currentActualPos.y - previousActualPos.y)

                    smoothedVelocity = Offset(
                        smoothedVelocity.x + (rawVelocity.x - smoothedVelocity.x) * velocitySmoothing,
                        smoothedVelocity.y + (rawVelocity.y - smoothedVelocity.y) * velocitySmoothing
                    )

                    if stateObj.popAnim.value == 0 then
                        local targetDeformation = Offset(
                            coerceIn(smoothedVelocity.x * deformationFactor, -deformationClamp, deformationClamp),
                            coerceIn(smoothedVelocity.y * deformationFactor, -deformationClamp, deformationClamp)
                        )

                        local currentDef = stateObj.deformationAnim.value
                        local forceX = (targetDeformation.x - currentDef.x) * stiffness - defVelocity.x * damping
                        local forceY = (targetDeformation.y - currentDef.y) * stiffness - defVelocity.y * damping

                        defVelocity = Offset(defVelocity.x + forceX * dt, defVelocity.y + forceY * dt)
                        local nextDef = Offset(currentDef.x + defVelocity.x * dt, currentDef.y + defVelocity.y * dt)

                        stateObj.deformationAnim.snapTo(nextDef)
                    else
                        stateObj.deformationAnim.snapTo(Offset.Zero)
                        defVelocity = Offset.Zero
                    end
                    previousActualPos = currentActualPos
                end


            end

    )

end

local UnlockedSnapSpring = spring {
    dampingRatio = 0.45,
    stiffness = Spring.StiffnessLow,
}
local SnapBackSpring = spring {
    dampingRatio = 0.65,
    stiffness = Spring.StiffnessLow,
}

local function hypot(x, y)
    return math.sqrt(x * x + y * y)
end

local function ThemeToggleButton(args)
    local isDarkTheme = args.isDarkTheme
    local sunColor = args.sunColor
    local moonColor = args.moonColor
    local onToggle = args.onToggle
    local modifier = args.modifier or Modifier

    local progress = animateFloatAsState(isDarkTheme and 1.0 or 0.0, spring(0.75, 300, nil))

    local scaleAnim = remember(function()
        return Animatable(1.0)
    end)
    LaunchedEffect(isDarkTheme, function(scope)
        scaleAnim.snapTo(0.85)
        scaleAnim.animateTo {
            targetValue = 1.0,
            animationSpec = spring(0.6, 400, nil)
        }
    end)

    local mainPath = remember(function()
        return Path()
    end)
    local cutoutPath = remember(function()
        return Path()
    end)
    local finalPath = remember(function()
        return Path()
    end)

    Canvas({
        modifier = modifier
                .size(40)
                .graphicsLayer(function(layerScope)
            layerScope.scaleX = scaleAnim.value
            layerScope.scaleY = scaleAnim.value
        end)
                .clip(CircleShape)
                .clickable(onToggle)
                .padding(6),
        onDraw = function(drawScope)
            local center = Offset(drawScope.size.width / 2.0, drawScope.size.height / 2.0)
            local maxRadius = drawScope.size.width / 2.0

            local currentR = sunColor.red + (moonColor.red - sunColor.red) * progress.value
            local currentG = sunColor.green + (moonColor.green - sunColor.green) * progress.value
            local currentB = sunColor.blue + (moonColor.blue - sunColor.blue) * progress.value
            local currentA = sunColor.alpha + (moonColor.alpha - sunColor.alpha) * progress.value
            local currentColor = Color(currentR, currentG, currentB, currentA)

            drawScope.rotate {
                degrees = progress.value * -90,
                pivot = center,
                block = function(drawScope1)
                    local rayAlpha = coerceIn(1.0 - progress.value * 2.5, 0.0, 1.0)
                    if rayAlpha > 0.0 then
                        local rayLength = maxRadius * 0.25
                        local rayOffset = maxRadius * 0.6
                        for i = 0, 7 do
                            drawScope1.rotate {
                                degrees = i * 45,
                                pivot = center,
                                block = function(drawScope2)
                                    drawScope2.drawLine {
                                        color = currentColor.copy({ alpha = rayAlpha }),
                                        start = center.copy({ y = center.y - rayOffset }),
                                        ["end"] = center.copy({ y = center.y - rayOffset - rayLength }),
                                        strokeWidth = maxRadius * 0.15,
                                        cap = StrokeCap.Round,
                                    }
                                end
                            }

                        end -- for

                    end -- if

                    local sunRadius = maxRadius * 0.45
                    local moonRadius = maxRadius * 0.85
                    local currentRadius = sunRadius + (moonRadius - sunRadius) * progress.value

                    mainPath.reset()
                    mainPath.addOval(
                            Rect({
                                left = center.x - currentRadius,
                                top = center.y - currentRadius,
                                right = center.x + currentRadius,
                                bottom = center.y + currentRadius,
                            })
                    )
                    local cutoutStartOffset = Offset(center.x + maxRadius * 2.0, center.y - maxRadius * 2.0)
                    local cutoutEndOffset = Offset(center.x + currentRadius * 0.3, center.y - currentRadius * 0.3)
                    local cutoutX = cutoutStartOffset.x + (cutoutEndOffset.x - cutoutStartOffset.x) * progress.value
                    local cutoutY = cutoutStartOffset.y + (cutoutEndOffset.y - cutoutStartOffset.y) * progress.value
                    local cutoutRadius = currentRadius * 0.95

                    cutoutPath.reset()
                    cutoutPath.addOval(
                            Rect({
                                left = cutoutX - cutoutRadius,
                                top = cutoutY - cutoutRadius,
                                right = cutoutX + cutoutRadius,
                                bottom = cutoutY + cutoutRadius,
                            })
                    )

                    finalPath.reset()
                    finalPath.op(mainPath, cutoutPath, PathOperation.Difference)

                    drawScope1.drawPath({ path = finalPath, color = currentColor })


                end -- rotate
            }
        end
    })
end

function roundToInt(x)
    return math.floor(x + 0.5)
end

function AnimationExample22()
    -- 🎨 ONE-FLAG VISUAL SWITCH — change this number, save, watch the bubble morph.
    --   0 = Soap Bubble    (physics-true thin-film rainbow, default)
    --   1 = Neon Magenta   (solid magenta orb)
    --   2 = Slime Green    (slime / jelly orb)
    --   3 = Psychedelic    (rainbow with rotated hue + brighter)
    --   4 = Ghost          (faint white blob)
    --   5 = Fire Orb       (warm orange/red glow)
    --   6 = Cyber Cyan     (electric cyan)
    local LOOK_PRESET = 3

    local lookValues
    if LOOK_PRESET == 1 then
        lookValues = { 0.0, 1.0, 0.0, 0.8, 0.0 }
    elseif LOOK_PRESET == 2 then
        lookValues = { 0.05, 0.2, 1.0, 0.4, 0.0 }
    elseif LOOK_PRESET == 3 then
        lookValues = { 1.0, 0.45, 0.75, 1.0, 2.5 }
    elseif LOOK_PRESET == 4 then
        lookValues = { 0.4, 0.95, 0.95, 1.0, 0.0 }
    elseif LOOK_PRESET == 5 then
        lookValues = { 0.0, 1.5, 0.5, 0.0, 0.0 }
    elseif LOOK_PRESET == 6 then
        lookValues = { 0.1, 0.0, 1.6, 1.6, 0.0 }
    else
        lookValues = { 1.0, 0.45, 0.75, 1.0, 0.0 }
    end

    local CARD_HEIGHT_DP = 540
    local MAX_ORB_RADIUS_DP = 180
    local MIN_ORB_RADIUS_DP = 88
    local BOTTOM_ORB_RATIO = 0.88
    local TOP_ORB_RATIO = 0.22
    local TEXT_Y_BOTTOM_RATIO = 0.46
    local TEXT_Y_TOP_RATIO = 0.40
    local SNAP_UNLOCK_THRESHOLD_PX = 10
    local DRAG_OVERSHOOT_RATIO = 0.05

    local DEFORMATION_FACTOR = 0.015
    local DEFORMATION_CLAMP = 0.6
    local VELOCITY_SMOOTHING = 0.15
    local SPRING_STIFFNESS = 1500
    local SPRING_DAMPING = 34.8

    local POP_DURATION_MS = 150
    local POP_DELAY_MS = 1500
    local THEME_REVEAL_DURATION_MS = 1100
    local TEXT_ANIM_DURATION_MS = 700

    local LIGHT_CENTER = Color(0xFFFFFFFF)
    local LIGHT_MID1 = Color(0xFFFBF8F6)
    local LIGHT_MID2 = Color(0xFFF5EFEE)
    local LIGHT_EDGE = Color(0xFFEEEAE8)
    local DARK_CENTER = Color(0xFF2A2D34)
    local DARK_MID = Color(0xFF16171B)
    local DARK_EDGE = Color(0xFF0A0B0D)

    local LIGHT_MAIN_TEXT = Color(0xFF4A403A)
    local DARK_MAIN_TEXT = Color(0xFFE5E5EA)
    local LIGHT_TITLE = Color(0xFF1F1A17)
    local DARK_TITLE = Color(0xFFF5F5F7)
    local LIGHT_SUBTITLE = Color(0xFF8A807A)
    local DARK_SUBTITLE = Color(0xFFA1A1A6)

    local SUN_COLOR = Color(0xFFFDB813)
    local MOON_COLOR = Color(0xFFE5E5EA)

    local SHADER_SRC = [[
    // hot-reload trigger: PRESET=$LOOK_PRESET
    uniform shader composable;
    uniform float2 touchCenter;
    uniform float radius;
    uniform float progress;
    uniform float2 deformation;
    uniform float popProgress;
    uniform float sysTime;
    uniform float interferenceAmount;
    uniform float3 baseTint;
    uniform float hueShift;

    float hash(float2 p) {
      return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
    }

    float smoothNoise(float2 p) {
      float2 i = floor(p);
      float2 f = fract(p);
      float2 u = f * f * (3.0 - 2.0 * f);
      return mix(mix(hash(i + float2(0.0, 0.0)), hash(i + float2(1.0, 0.0)), u.x),
                 mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
    }

    half4 main(float2 fragCoord) {
      // Tweak these to taste — they hot-reload because the whole shader source
      // is keyed into remember() in the Kotlin side.
      float THICKNESS_BASE = 300.0;
      float THICKNESS_GRAVITY = 120.0;
      float THICKNESS_SWIRL = 100.0;
      float THICKNESS_DETAIL = 40.0;
      float COLOR_INTENSITY = 2.0;
      float EDGE_FADE_END = 0.20;
      float ENV_REFLECTION_STRENGTH = 0.4;
      float ENV_BLUR_RADIUS = 50.0;

      // Look-mix uniforms (interferenceAmount / baseTint / hueShift) are driven
      // from Kotlin via the LOOK_PRESET flag at the top of AnimationExample22.

      half4 rawBackground = composable.eval(fragCoord);
      if (popProgress >= 1.0) return rawBackground;

      float2 rawUv = fragCoord - touchCenter;
      float speed = length(deformation);
      float2 moveDir = speed > 0.001 ? deformation / speed : float2(0.0, 1.0);

      float parallelDist = dot(rawUv, moveDir);
      float2 perpVector = rawUv - moveDir * parallelDist;

      float stretch = 1.0 + speed;
      float squash = 1.0 / sqrt(stretch);

      float2 uv = (moveDir * (parallelDist / stretch)) + (perpVector / squash);
      float dist = length(uv);
      float activeRadius = radius * (1.0 + popProgress * 1.5);

      if (dist >= activeRadius) {
        return rawBackground;
      }

      float2 nUv = uv / activeRadius;
      float distSq = dot(nUv, nUv);
      float z = sqrt(max(0.0, 1.0 - distSq));
      float3 normal = normalize(float3(nUv, z));
      float3 viewDir = float3(0.0, 0.0, 1.0);
      float NdotV = max(0.0, dot(normal, viewDir));

      float magnification = 0.45;
      float lensDeform = (1.0 - z) * magnification * (1.0 - popProgress);

      float2 refUvR = fragCoord - (nUv * activeRadius * (lensDeform * 0.88));
      float2 refUvG = fragCoord - (nUv * activeRadius * (lensDeform * 1.00));
      float2 refUvB = fragCoord - (nUv * activeRadius * (lensDeform * 1.12));

      half3 bgColor = half3(
        composable.eval(refUvR).r,
        composable.eval(refUvG).g,
        composable.eval(refUvB).b
      );

      float3 reflectionDir = reflect(-viewDir, normal);
      float3 lightDir1 = normalize(float3(0.6, 0.7, 0.8));
      float3 lightDir2 = normalize(float3(-0.5, -0.4, 0.6));
      float lightAlign1 = max(0.0, dot(reflectionDir, lightDir1));
      float lightAlign2 = max(0.0, dot(reflectionDir, lightDir2));

      // Thin-film interference. n_film = 1.33 (soapy water), n_air = 1.0.
      float n_film = 1.33;
      float n_air = 1.0;
      float R0 = pow((n_film - n_air) / (n_film + n_air), 2.0);
      float fresnel = R0 + (1.0 - R0) * pow(1.0 - NdotV, 5.0);

      float sinThetaI = sqrt(max(0.0, 1.0 - NdotV * NdotV));
      float sinThetaT = sinThetaI / n_film;
      float cosThetaT = sqrt(max(0.0, 1.0 - sinThetaT * sinThetaT));

      float swirl = smoothNoise(nUv * 3.0 + sysTime * 0.12);
      float thicknessNoise = smoothNoise(nUv * 5.0 - sysTime * 0.08);
      float baseThickness = THICKNESS_BASE + nUv.y * THICKNESS_GRAVITY;
      float thickness = baseThickness
        + swirl * THICKNESS_SWIRL
        + thicknessNoise * THICKNESS_DETAIL;
      thickness = clamp(thickness, 80.0, 900.0);

      float opd = 2.0 * n_film * thickness * cosThetaT;
      float lambda_R = 650.0;
      float lambda_G = 532.0;
      float lambda_B = 450.0;

      float TWO_PI = 6.2831853;
      float oscR = 0.5 + 0.5 * cos(TWO_PI * opd / lambda_R);
      float oscG = 0.5 + 0.5 * cos(TWO_PI * opd / lambda_G);
      float oscB = 0.5 + 0.5 * cos(TWO_PI * opd / lambda_B);

      half3 interferenceColor = half3(oscR, oscG, oscB);

      // Hue shift via YIQ rotation. 0 = unchanged, 2*PI = full wraparound.
      if (abs(hueShift) > 0.001) {
        float Y = dot(float3(interferenceColor), float3(0.299, 0.587, 0.114));
        float I = dot(float3(interferenceColor), float3(0.596, -0.275, -0.321));
        float Q = dot(float3(interferenceColor), float3(0.212, -0.523, 0.311));
        float c = cos(hueShift);
        float s = sin(hueShift);
        float Inew = I * c - Q * s;
        float Qnew = I * s + Q * c;
        interferenceColor = half3(
          Y + 0.956 * Inew + 0.619 * Qnew,
          Y - 0.272 * Inew - 0.647 * Qnew,
          Y - 1.106 * Inew + 1.703 * Qnew
        );
      }

      // Blend toward a solid base tint. Soap bubble at 1.0, neon orb at 0.0.
      half3 lookColor = mix(half3(baseTint), interferenceColor, interferenceAmount);

      float interferenceStrength = smoothstep(0.0, EDGE_FADE_END, NdotV);
      half3 filmReflection = lookColor * fresnel * COLOR_INTENSITY;
      half3 whiteReflection = half3(fresnel);
      half3 thinFilmColor = mix(whiteReflection, filmReflection, interferenceStrength);

      float spec1 = pow(lightAlign1, 250.0) * 2.5;
      float spec2 = pow(lightAlign2, 60.0) * 0.5;
      half3 highlights = half3(spec1 + spec2);

      float2 reflectOffset = normal.xy * ENV_BLUR_RADIUS;
      float2 envCenter = fragCoord + reflectOffset;
      float blurStep = ENV_BLUR_RADIUS * 0.4;
      half3 envSample = composable.eval(envCenter).rgb * 0.4
        + composable.eval(envCenter + float2(blurStep, 0.0)).rgb * 0.15
        + composable.eval(envCenter - float2(blurStep, 0.0)).rgb * 0.15
        + composable.eval(envCenter + float2(0.0, blurStep)).rgb * 0.15
        + composable.eval(envCenter - float2(0.0, blurStep)).rgb * 0.15;
      half3 envReflection = envSample * fresnel * ENV_REFLECTION_STRENGTH;

      float rimShadow = smoothstep(0.92, 1.0, sqrt(distSq));
      bgColor *= (1.0 - rimShadow * 0.25);

      half3 finalColor = bgColor * (1.0 - half3(fresnel))
        + thinFilmColor
        + envReflection
        + highlights;

      float fadeOut = 1.0 - pow(popProgress, 0.5);
      return half4(mix(rawBackground.rgb, finalColor, fadeOut), rawBackground.a);
    }
]]

    Column {
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12),
        content = function()
            BoxWithConstraints {
                modifier = Modifier
                        .fillMaxWidth()
                        .height(CARD_HEIGHT_DP)
                        .padding({ horizontal = 12 })
                        .clip(RoundedCornerShape(20)),
                content = function(boxWithConstraintsScope)
                    local densityValue = LocalDensity.current.density
                    local widthPx = boxWithConstraintsScope.maxWidth * densityValue
                    local heightPx = boxWithConstraintsScope.maxHeight * densityValue
                    local maxRadiusPx = MAX_ORB_RADIUS_DP * densityValue
                    local minRadiusPx = MIN_ORB_RADIUS_DP * densityValue

                    local stateObj = remember(
                            widthPx,
                            heightPx,
                            BOTTOM_ORB_RATIO,
                            TOP_ORB_RATIO,
                            TEXT_Y_BOTTOM_RATIO,
                            TEXT_Y_TOP_RATIO,
                            DRAG_OVERSHOOT_RATIO,
                            function()
                                return BubbleState {
                                    screenHeightPx = heightPx,
                                    centerX = widthPx / 2,
                                    bottomOrbRatio = BOTTOM_ORB_RATIO,
                                    topOrbRatio = TOP_ORB_RATIO,
                                    textYBottomRatio = TEXT_Y_BOTTOM_RATIO,
                                    textYTopRatio = TEXT_Y_TOP_RATIO,
                                    dragOvershootRatio = DRAG_OVERSHOOT_RATIO,
                                }
                            end
                    )

                    local radiusSpring = remember(
                            function()
                                return spring {
                                    dampingRatio = 0.75,
                                    stiffness = Spring.StiffnessLow
                                }
                            end
                    )

                    local animatedMaxRadiusPx = animateFloatAsState {
                        targetValue = maxRadiusPx,
                        animationSpec = radiusSpring,
                    }

                    local animatedMinRadiusPx = animateFloatAsState {
                        targetValue = minRadiusPx,
                        animationSpec = radiusSpring,
                    }

                    local shader = remember(SHADER_SRC, function()
                        return RuntimeShader(SHADER_SRC)
                        --[[
                         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    RuntimeShader(SHADER_SRC)
                } else {
                    null
                }
                        ]]
                    end)

                    local scope = rememberCoroutineScope()
                    local isDarkTheme = state(false)
                    local previousIsDark = state(false)

                    local lightBrush = remember(widthPx, heightPx, LIGHT_CENTER, LIGHT_MID1, LIGHT_MID2, LIGHT_EDGE, function()
                        return radialBrush(widthPx, heightPx, LIGHT_CENTER, LIGHT_MID1, LIGHT_MID2, LIGHT_EDGE)
                    end)

                    local darkBrush = remember(widthPx, heightPx, DARK_CENTER, DARK_MID, DARK_EDGE, function()
                        return radialBrush(widthPx, heightPx, DARK_CENTER, DARK_MID, DARK_MID, DARK_EDGE)
                    end)

                    local textTween = remember(TEXT_ANIM_DURATION_MS, function()
                        return tween {
                            durationMillis = TEXT_ANIM_DURATION_MS,
                            easing = FastOutLinearInEasing
                        }
                    end)

                    local mainTextColor = animateColorAsState {
                        targetValue = isDarkTheme.value and DARK_MAIN_TEXT or LIGHT_MAIN_TEXT,
                        animationSpec = textTween,
                    }

                    local titleColor = animateColorAsState {
                        targetValue = isDarkTheme.value and DARK_TITLE or LIGHT_TITLE,
                        animationSpec = textTween,
                    }

                    local subtitleColor = animateColorAsState {
                        targetValue = isDarkTheme.value and DARK_SUBTITLE or LIGHT_SUBTITLE,
                        animationSpec = textTween,
                    }

                    DeformationFrameLoop {
                        stateObj = stateObj,
                        deformationFactor = DEFORMATION_FACTOR,
                        deformationClamp = DEFORMATION_CLAMP,
                        velocitySmoothing = VELOCITY_SMOOTHING,
                        stiffness = SPRING_STIFFNESS,
                        damping = SPRING_DAMPING,
                    }

                    local lookSpring = remember(
                            function()
                                return spring {
                                    dampingRatio = 0.85,
                                    stiffness = Spring.StiffnessVeryLow
                                }
                            end
                    )

                    local animInterference = animateFloatAsState {
                        targetValue = lookValues[1],
                        animationSpec = lookSpring,
                    }

                    local animTintR = animateFloatAsState {
                        targetValue = lookValues[2],
                        animationSpec = lookSpring,
                    }

                    local animTintG = animateFloatAsState {
                        targetValue = lookValues[3],
                        animationSpec = lookSpring,
                    }

                    local animTintB = animateFloatAsState {
                        targetValue = lookValues[4],
                        animationSpec = lookSpring,
                    }

                    local animHueShift = animateFloatAsState {
                        targetValue = lookValues[5],
                        animationSpec = lookSpring,
                    }

                    local revealClipPath = remember(
                            function()
                                return Path()
                            end
                    )

                    Box {
                        modifier = Modifier
                                .fillMaxSize()
                        -- bubbleDragInput
                                .pointerInput("bubbleDrag", SNAP_UNLOCK_THRESHOLD_PX, function()
                            local isUnlocked = false
                            detectDragGestures {
                                onDragStart = function()
                                    isUnlocked = stateObj:isAtTop(SNAP_UNLOCK_THRESHOLD_PX)
                                end, -- onDragStart
                                onDragEnd = function()
                                    scope.launch(function()
                                        if isUnlocked then
                                            if stateObj.bubblePos.value.y < stateObj.midPoint then
                                                stateObj.bubblePos.animateTo {
                                                    targetValue = Offset(stateObj.centerX, stateObj.topOrbCenterY),
                                                    animationSpec = UnlockedSnapSpring,
                                                }
                                            else
                                                stateObj.bubblePos.animateTo {
                                                    targetValue = Offset(stateObj.centerX, stateObj.bottomOrbCenterY),
                                                    animationSpec = SnapBackSpring,
                                                }


                                            end

                                        else
                                            local targetY = (stateObj.bubblePos.value.y < stateObj.midPoint) and stateObj.topOrbCenterY or stateObj.bottomOrbCenterY
                                            stateObj.bubblePos.animateTo({
                                                targetValue = Offset(stateObj.centerX, targetY),
                                                animationSpec = SnapBackSpring
                                            })


                                        end

                                    end)
                                end, -- onDragEnd
                                onDrag = function(change, dragAmount)
                                    if stateObj.popAnim.value > 0 then
                                        return
                                    end
                                    change.consume()

                                    local proposedY = stateObj.bubblePos.value.y + dragAmount.y
                                    if not isUnlocked and proposedY <= stateObj.topOrbCenterY then
                                        isUnlocked = true
                                    end
                                    if isUnlocked then
                                        scope.launch(function()
                                            stateObj.bubblePos.snapTo(Offset(stateObj.bubblePos.value.x + dragAmount.x, proposedY))
                                        end)
                                    else
                                        local clampedY = coerceAtMost(proposedY, stateObj.maxDragY)
                                        scope.launch(function()

                                            stateObj.bubblePos.snapTo(Offset(stateObj.centerX, clampedY))
                                        end)
                                    end


                                end -- onDrag


                            } -- detectDragGestures

                        end) -- pointerInput
                        -- bubbleTapInput
                                .pointerInput(
                                "bubbleTap", POP_DURATION_MS, POP_DELAY_MS,
                                function()
                                    detectTapGestures {
                                        onTap = function()
                                            if stateObj.popAnim.value == 0 then
                                                scope.launch(function()
                                                    stateObj.popAnim.animateTo {
                                                        targetValue = 1,
                                                        animationSpec = tween {
                                                            durationMillis = POP_DURATION_MS,
                                                            easing = FastOutLinearInEasing
                                                        }
                                                    }
                                                    delay(POP_DELAY_MS)
                                                    stateObj.popAnim.snapTo(0)
                                                    stateObj.bubblePos.snapTo(Offset(stateObj.centerX, stateObj.bottomOrbCenterY))
                                                end) -- scope

                                            end

                                        end -- onTap

                                    } -- detectTapGestures

                                end) -- pointerInput
                        -- bubbleShaderLayer
                                .graphicsLayer(function(graphicsLayerScope)
                            -- api 33
                            if shader ~= nil then
                                shader.setFloatUniform("touchCenter", stateObj.bubblePos.value.x, stateObj.bubblePos.value.y)
                                shader.setFloatUniform("radius", stateObj:radiusFor(animatedMaxRadiusPx.value, animatedMinRadiusPx.value))
                                shader.setFloatUniform("progress", stateObj:progress())
                                shader.setFloatUniform(
                                        "deformation",
                                        stateObj.deformationAnim.value.x,
                                        stateObj.deformationAnim.value.y
                                )
                                shader.setFloatUniform("popProgress", stateObj.popAnim.value)
                                shader.setFloatUniform("sysTime", stateObj.shaderTime[1])
                                shader.setFloatUniform("interferenceAmount", animInterference.value)
                                shader.setFloatUniform("baseTint", animTintR.value, animTintG.value, animTintB.value)
                                shader.setFloatUniform("hueShift", animHueShift.value)

                                -- [WARN] asComposeRenderEffect 就是 AndroidRenderEffect(this)
                                graphicsLayerScope.renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "composable")


                            end -- if

                        end) -- graphicsLayer
                                .drawBehind(function(drawScope)
                            local revealProgress = stateObj.themeRevealProgress.value
                            local reusablePath = revealClipPath

                            local currentBrush = (isDarkTheme.value) and darkBrush or lightBrush
                            local prevBrush = (previousIsDark.value) and darkBrush or lightBrush

                            drawScope.drawRect {
                                brush = prevBrush
                            }

                            if revealProgress < 1 then
                                local maxRadius = hypot(drawScope.size.width, drawScope.size.height)
                                local currentRevealRadius = revealProgress * maxRadius
                                local epicenter = Offset(drawScope.size.width - 60, 60)
                                reusablePath.reset()
                                reusablePath.addOval {
                                    oval = Rect({
                                        left = epicenter.x - currentRevealRadius,
                                        top = epicenter.y - currentRevealRadius,
                                        right = epicenter.x + currentRevealRadius,
                                        bottom = epicenter.y + currentRevealRadius,
                                    })
                                }

                                drawScope.clipPath {
                                    path = reusablePath,
                                    block = function(drawScope1)
                                        drawScope1.drawRect({ brush = currentBrush })
                                    end
                                }
                            else
                                drawScope.drawRect({ brush = currentBrush })
                            end


                        end), -- drawBehind
                        content = function()
                            ThemeToggleButton {
                                isDarkTheme = isDarkTheme.value,
                                sunColor = SUN_COLOR,
                                moonColor = MOON_COLOR,
                                onToggle = function()
                                    if stateObj.themeRevealProgress.isRunning then
                                        return
                                    end
                                    previousIsDark.value = isDarkTheme.value
                                    isDarkTheme.value = not isDarkTheme.value
                                    scope.launch(function()
                                        stateObj.themeRevealProgress.snapTo(0)
                                        stateObj.themeRevealProgress.animateTo {
                                            targetValue = 1,
                                            animationSpec = tween {
                                                durationMillis = THEME_REVEAL_DURATION_MS,
                                                easing = CubicBezierEasing(0.1, 0.8, 0.2, 1.0),
                                            }

                                        }

                                    end)

                                end,
                                modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding({ top = 16, ["end"] = 16 }),


                            } -- ThemeToggleButton

                            Text {
                                text = "Drag the bubble.",
                                fontSize = 22,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 28,
                                color = mainTextColor.value,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                        .align(Alignment.Center)
                                        .offset({ y = (-50) })
                                        .graphicsLayer(function(graphicsLayerScope)
                                    graphicsLayerScope.alpha = coerceIn(1 - (stateObj:progress() * 4), 0, 1)

                                end),
                            }

                            Box {
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .offset(function()
                                    return IntOffset(0, roundToInt(stateObj:textYOffsetPx()))
                                end),

                                contentAlignment = Alignment.Center,
                                content = function()
                                    Column {
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                                .graphicsLayer(function(graphicsLayerScope)
                                            graphicsLayerScope.alpha = coerceIn(stateObj:progress() * 3, 0, 1)
                                        end),
                                        content = function()
                                            Text {
                                                text = "Thin film",
                                                fontSize = 28,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = (-0.5),
                                                color = titleColor.value,
                                            }

                                            Text {
                                                text = "Real-time interference\non a kinematic spring.",
                                                fontSize = 14,
                                                lineHeight = 18,
                                                textAlign = TextAlign.Center,
                                                color = subtitleColor.value,
                                                modifier = Modifier.padding({ top = 8 }),


                                            }

                                        end

                                    }
                                end
                            }
                        end
                    } -- Box

                    --[[
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                BubbleFallback(state, state.radiusFor(animatedMaxRadiusPx, animatedMinRadiusPx))
            }
                    ]]

                end -- BoxWithConstraints content


            } -- BoxWithConstraints

            Text {
                text = "Soap bubble drag with thin-film interference. Tweak Kotlin radii / spring values for the feel, or edit the AGSL constants (THICKNESS_BASE, COLOR_INTENSITY, EDGE_FADE_END...) inside SHADER_SRC for the optics.",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding({ horizontal = 16 }),
            }

        end -- Column content


    } -- Column

end -- fun22

setContent(AnimationExample22)
