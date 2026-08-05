local compose = compose
local setContent = compose.setContent
local state = compose.state
local withScope = compose.with

local Column = compose.foundation.layout.Column
local Row = compose.foundation.layout.Row
local Box = compose.foundation.layout.Box
local Spacer = compose.foundation.layout.Spacer
local RoundedCornerShape = compose.RoundedCornerShape
local Arrangement = compose.Arrangement
local Alignment = compose.Alignment

local material3 = compose.material3
local Text = material3.Text
local MaterialTheme = material3.MaterialTheme

local animation = compose.animation
local SharedTransitionLayout = animation.SharedTransitionLayout
local AnimatedContent = animation.AnimatedContent
local rememberSharedContentState = animation.rememberSharedContentState
local fadeIn = animation.fadeIn
local fadeOut = animation.fadeOut
local tween = animation.core.tween
local FastOutSlowInEasing = animation.core.FastOutSlowInEasing

local Color = compose.ui.graphics.Color

local function findCard(cards, id)
    for _, model in ipairs(cards) do
        if model.id == id then
            return model
        end
    end
    return nil
end

local function boundsTween(durationMillis, easing)
    return tween({
        durationMillis = durationMillis,
        easing = easing,
    })
end

local function CardList(args)
    local scope = args.scope
    local visibilityScope = args.visibilityScope
    local cards = args.cards
    local boundsDurationMs = args.boundsDurationMs
    local boundsEasing = args.boundsEasing
    local onSelect = args.onSelect

    Column({
        verticalArrangement = Arrangement.spacedBy(12),
        content = function()
            for _, model in ipairs(cards) do
                withScope(scope, function()
                    Row({
                        modifier = Modifier
                                .fillMaxWidth()
                                .height(80)
                                .sharedElement({
                            sharedContentState = rememberSharedContentState("card-" .. tostring(model.id)),
                            animatedVisibilityScope = visibilityScope,
                            boundsTransform = function(_, _)
                                return boundsTween(boundsDurationMs, boundsEasing)
                            end,
                        })
                                .background({
                            color = model.accent,
                            shape = RoundedCornerShape(16),
                        })
                                .clickable(function()
                            onSelect(model.id)
                        end)
                                .padding(16),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12),
                        content = function()
                            Box({
                                modifier = Modifier
                                        .size(40)
                                        .background(
                                        Color.White.copy({ alpha = 0.3 }),
                                        RoundedCornerShape(8)
                                ),
                            })
                            Text({
                                text = model.title,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                            })
                        end,
                    })
                end)
            end
        end,
    })
end

local function CardDetail(args)
    local scope = args.scope
    local visibilityScope = args.visibilityScope
    local model = args.model
    local detailHeightDp = args.detailHeightDp
    local boundsDurationMs = args.boundsDurationMs
    local boundsEasing = args.boundsEasing
    local onClose = args.onClose

    withScope(scope, function()
        Column({
            modifier = Modifier
                    .fillMaxWidth()
                    .height(detailHeightDp)

                    .sharedElement({
                sharedContentState = rememberSharedContentState("card-" .. tostring(model.id)),
                animatedVisibilityScope = visibilityScope,
                boundsTransform = function(_, _)
                    return boundsTween(boundsDurationMs, boundsEasing)
                end,
            })
                    .background({
                color = model.accent,
                shape = RoundedCornerShape(24),
            })
                    .clickable(function()
                onClose()
            end)
                    .padding(24),
            verticalArrangement = Arrangement.spacedBy(16),
            content = function()
                Text({
                    text = model.title,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge,
                })
                Spacer({ modifier = Modifier.height(4) })
                Text({
                    text = model.body,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                })
                Box({
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomEnd,
                    content = function()
                        Text({
                            text = "Tap to close",
                            color = Color.White.copy({ alpha = 0.85 }),
                            style = MaterialTheme.typography.labelLarge,
                        })
                    end,
                })
            end,
        })
    end)
end

function AnimationExample12()
    local BOUNDS_DURATION_MS = 500
    local BOUNDS_EASING = FastOutSlowInEasing
    local DETAIL_HEIGHT_DP = 260

    local CARDS = {
        {
            id = "a",
            title = "Card A",
            accent = Color(0xFFEF5350),
            body = "This card expands using shared bounds. Tap to collapse back to the list.",
        },
        {
            id = "b",
            title = "Card B",
            accent = Color(0xFF42A5F5),
            body = "Try changing BOUNDS_DURATION_MS or BOUNDS_EASING above and watch the timing morph live.",
        },
        {
            id = "c",
            title = "Card C",
            accent = Color(0xFF66BB6A),
            body = "SharedTransitionLayout ties matching keys across AnimatedContent states for smooth bounds animation.",
        },
    }

    local selectedId = state(nil)

    Column({
        modifier = Modifier.fillMaxWidth().padding(16),
        verticalArrangement = Arrangement.spacedBy(16),
        content = function()
            Text({
                text = "Shared Bounds Expansion",
                style = MaterialTheme.typography.titleMedium,
            })
            Text({
                text = "Tap a card to expand into a detail view.",
                style = MaterialTheme.typography.bodySmall,
            })
            SharedTransitionLayout({
                modifier = Modifier.fillMaxWidth(),
                content = function(sharedTransitionScope)
                    AnimatedContent({
                        targetState = selectedId.value,
                        transitionSpec = function()
                            return fadeIn(boundsTween(BOUNDS_DURATION_MS, BOUNDS_EASING))
                                    :togetherWith(
                                    fadeOut(boundsTween(BOUNDS_DURATION_MS, BOUNDS_EASING))
                            )
                        end,
                        label = "shared-bounds",
                        content = function(animatedContentScope, current)
                            if current == nil then
                                CardList({
                                    scope = sharedTransitionScope,
                                    visibilityScope = animatedContentScope,
                                    cards = CARDS,
                                    boundsDurationMs = BOUNDS_DURATION_MS,
                                    boundsEasing = BOUNDS_EASING,
                                    onSelect = function(id)
                                        selectedId.value = id
                                    end,
                                })
                            else
                                local model = findCard(CARDS, current)
                                if model ~= nil then
                                    CardDetail({
                                        scope = sharedTransitionScope,
                                        visibilityScope = animatedContentScope,
                                        model = model,
                                        detailHeightDp = DETAIL_HEIGHT_DP,
                                        boundsDurationMs = BOUNDS_DURATION_MS,
                                        boundsEasing = BOUNDS_EASING,
                                        onClose = function()
                                            selectedId.value = nil
                                        end,
                                    })
                                end
                            end
                        end,
                    })
                end,
            })
        end,
    })
end

setContent(AnimationExample12)
