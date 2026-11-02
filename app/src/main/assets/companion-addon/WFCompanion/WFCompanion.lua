-- WFCompanion: writes character state as a JSON string into SavedVariables.
-- The Android companion screen reads WTF/Account/<ACCOUNT>/SavedVariables/WFCompanion.lua.
-- Classic flushes SavedVariables on /reload or logout, so data refreshes then.

local POWER_NAMES = { [0] = "Mana", [1] = "Rage", [2] = "Focus", [3] = "Energy", [4] = "Happiness" }

-- Minimal JSON string encoder (no external libs in the WoW Lua sandbox).
local function jsonEscape(s)
  s = tostring(s)
  s = string.gsub(s, "\\", "\\\\")
  s = string.gsub(s, '"', '\\"')
  s = string.gsub(s, "\n", "\\n")
  s = string.gsub(s, "\r", "\\r")
  s = string.gsub(s, "\t", "\\t")
  return s
end

local function jstr(v) return '"' .. jsonEscape(v) .. '"' end
local function jnum(v) return tostring(v or 0) end

-- Compatibility shims across vanilla (UnitMana) and Classic (UnitPower).
local function getPower()
  if UnitPower then return UnitPower("player"), UnitPowerMax("player") end
  return UnitMana("player"), UnitManaMax("player")
end

local function getPosition()
  local x, y = 0, 0
  if SetMapToCurrentZone then pcall(SetMapToCurrentZone) end
  if GetPlayerMapPosition then
    local ok, px, py = pcall(GetPlayerMapPosition, "player")
    if ok and px then x, y = px * 100, py * 100 end
  end
  return x, y
end

local function collectBags()
  local parts = {}
  local free = 0
  for bag = 0, 4 do
    local slots = GetContainerNumSlots(bag) or 0
    for slot = 1, slots do
      local link = GetContainerItemLink(bag, slot)
      if link then
        local _, count = GetContainerItemInfo(bag, slot)
        local name = string.match(link, "%[(.-)%]") or "Item"
        parts[#parts + 1] = "{" .. '"name":' .. jstr(name) .. ',"count":' .. jnum(count or 1) .. "}"
      else
        free = free + 1
      end
    end
  end
  return "[" .. table.concat(parts, ",") .. "]", free
end

local function publish()
  if not UnitName then return end
  local name = UnitName("player")
  if not name then return end
  local _, classToken = UnitClass("player")
  local className = UnitClass("player")
  local race = UnitRace("player")
  local level = UnitLevel("player")
  local hp, hpMax = UnitHealth("player"), UnitHealthMax("player")
  local power, powerMax = getPower()
  local ptype = UnitPowerType and UnitPowerType("player") or 0
  local zone = (GetRealZoneText and GetRealZoneText()) or (GetZoneText and GetZoneText()) or ""
  local x, y = getPosition()
  local money = GetMoney and GetMoney() or 0
  local bagsJson, free = collectBags()
  local when = date and date("%H:%M:%S") or ""

  local fields = {
    '"name":' .. jstr(name),
    '"level":' .. jnum(level),
    '"class":' .. jstr(className or classToken or ""),
    '"className":' .. jstr(className or classToken or ""),
    '"race":' .. jstr(race or ""),
    '"health":' .. jnum(hp),
    '"healthMax":' .. jnum(hpMax),
    '"power":' .. jnum(power),
    '"powerMax":' .. jnum(powerMax),
    '"powerType":' .. jstr(POWER_NAMES[ptype] or "Power"),
    '"zone":' .. jstr(zone),
    '"x":' .. jnum(x),
    '"y":' .. jnum(y),
    '"money":' .. jnum(money),
    '"freeSlots":' .. jnum(free),
    '"bags":' .. bagsJson,
    '"time":' .. jstr(when),
  }
  WFCompanionJSON = "{" .. table.concat(fields, ",") .. "}"
end

-- Throttled refresh of the in-memory global; the file itself writes on reload/logout.
local frame = CreateFrame("Frame")
local accum = 0
frame:RegisterEvent("PLAYER_ENTERING_WORLD")
frame:RegisterEvent("PLAYER_LEVEL_UP")
frame:RegisterEvent("PLAYER_MONEY")
frame:RegisterEvent("BAG_UPDATE")
frame:RegisterEvent("ZONE_CHANGED_NEW_AREA")
frame:RegisterEvent("PLAYER_LOGOUT")
frame:SetScript("OnEvent", function() publish() end)
frame:SetScript("OnUpdate", function(_, elapsed)
  -- Classic passes elapsed as arg2; vanilla 1.12 exposes it as the global arg1.
  accum = accum + (elapsed or arg1 or 0)
  if accum >= 2 then accum = 0; publish() end
end)

SLASH_WFCOMPANION1 = "/wfc"
SlashCmdList["WFCOMPANION"] = function()
  publish()
  if ReloadUI then ReloadUI() end
end
