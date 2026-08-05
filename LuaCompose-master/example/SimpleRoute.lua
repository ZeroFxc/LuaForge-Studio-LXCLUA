local compose = compose
local setContent = compose.setContent
local navigation3 = compose.navigation3
local NavDisplay = navigation3.NavDisplay
local rememberNavBackStack = navigation3.rememberNavBackStack
local entryProvider = navigation3.entryProvider

local material3 = compose.material3
local Text = material3.Text
local Button = material3.Button
local Scaffold = material3.Scaffold

local layout = compose.foundation.layout
local Column = layout.Column
local Spacer = layout.Spacer
local Modifier = compose.Modifier

-- =====================================
-- Welcome Screen (First Page)
-- =====================================
local function WelcomeScreen(backStack)
    Column {
        modifier = Modifier.padding(16),
        content = function()
            Text {
                text = "Welcome to Navigation 3!",
            }
            Spacer { modifier = Modifier.height(20) }
            Button {
                onClick = function()
                    -- Navigate to MainRoute
                    backStack.add("MainRoute")
                end,
                content = function()
                    Text { text = "Go to Main Screen" }
                end
            }
            Spacer { modifier = Modifier.height(10) }
            Button {
                onClick = function()
                    -- Navigate to ShapeRoute with arguments using a Lua table
                    local args = { route = "ShapeRoute", shapeId = 999, title = "Awesome Shape" }
                    backStack.add(args)
                end,
                content = function()
                    Text { text = "Go to Shape Screen (with params)" }
                end
            }
        end
    }
end

-- =====================================
-- Main Screen (Second Page)
-- =====================================
local function MainScreen(backStack)
    Column {
        modifier = Modifier.padding(16),
        content = function()
            Text {
                text = "This is the Main Screen.",
            }
            Spacer { modifier = Modifier.height(20) }
            Button {
                onClick = function()
                    -- Navigate back by removing the last entry in the back stack
                    backStack.remove(backStack.size() - 1)
                end,
                content = function()
                    Text { text = "Back to Welcome" }
                end
            }
        end
    }
end

-- =====================================
-- Shape Screen (Receives parameters)
-- =====================================
local function ShapeScreen(backStack, shapeId, title)
    Column {
        modifier = Modifier.padding(16),
        content = function()
            Text {
                text = "Shape Screen",
            }
            Text {
                text = "Received ID: " .. tostring(shapeId),
            }
            Text {
                text = "Received Title: " .. tostring(title),
            }
            Spacer { modifier = Modifier.height(20) }
            Button {
                onClick = function()
                    -- Navigate back
                    backStack.remove(backStack.size() - 1)
                end,
                content = function()
                    Text { text = "Back" }
                end
            }
        end
    }
end

-- =====================================
-- Set up Navigation 3 App
-- =====================================
setContent(function()
    local backStack = rememberNavBackStack({ "WelcomeRoute" })
    Scaffold {
        content = function(paddingValues)
            NavDisplay {
                modifier = Modifier.padding(paddingValues),
                backStack = backStack,
                entryDecorators = {
                    navigation3.rememberSaveableStateHolderNavEntryDecorator(),
                    navigation3.rememberViewModelStoreNavEntryDecorator()
                },

                entryProvider = entryProvider {
                    ["WelcomeRoute"] = function()
                        WelcomeScreen(backStack)
                    end,
                    ["MainRoute"] = function()
                        MainScreen(backStack)
                    end,
                    ["ShapeRoute"] = function(key)
                        ShapeScreen(backStack, key.shapeId, key.title)
                    end
                }
            }
        end
    }
end)
