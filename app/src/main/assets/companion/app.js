"use strict";

// ---- tabs ----
document.querySelectorAll(".tab").forEach(function (t) {
  t.addEventListener("click", function () {
    document.querySelectorAll(".tab").forEach(function (x) { x.classList.remove("active"); });
    document.querySelectorAll(".panel").forEach(function (x) { x.classList.remove("active"); });
    t.classList.add("active");
    document.getElementById(t.dataset.tab).classList.add("active");
  });
});

document.getElementById("refresh").addEventListener("click", function () {
  if (window.WFHost && WFHost.refresh) WFHost.refresh();
});

// ---- static reference data ----
var ZONES = {
  "Eastern Kingdoms": ["Elwynn Forest", "Westfall", "Redridge", "Duskwood", "Stranglethorn Vale",
    "Hillsbrad Foothills", "Arathi Highlands", "Western Plaguelands", "Eastern Plaguelands", "Burning Steppes"],
  "Kalimdor": ["Durotar", "Mulgore", "The Barrens", "Ashenvale", "Thousand Needles",
    "Tanaris", "Feralas", "Un'Goro Crater", "Silithus", "Winterspring"]
};

var TALENTS = {
  Warrior: ["Arms", "Fury", "Protection"],
  Paladin: ["Holy", "Protection", "Retribution"],
  Hunter: ["Beast Mastery", "Marksmanship", "Survival"],
  Rogue: ["Assassination", "Combat", "Subtlety"],
  Priest: ["Discipline", "Holy", "Shadow"],
  Shaman: ["Elemental", "Enhancement", "Restoration"],
  Mage: ["Arcane", "Fire", "Frost"],
  Warlock: ["Affliction", "Demonology", "Destruction"],
  Druid: ["Balance", "Feral Combat", "Restoration"]
};

(function renderZones() {
  var el = document.getElementById("zone-list");
  Object.keys(ZONES).forEach(function (cont) {
    var div = document.createElement("div");
    div.className = "zone-group";
    var b = document.createElement("b");
    b.textContent = cont;
    div.appendChild(b);
    var s = document.createElement("span");
    s.textContent = ZONES[cont].join(" · ");
    div.appendChild(s);
    el.appendChild(div);
  });
})();

// ---- character update, called from native via window.__wfUpdate(jsonString) ----
function esc(s) {
  var d = document.createElement("div");
  d.textContent = s == null ? "" : String(s);
  return d.innerHTML;
}

window.__wfUpdate = function (jsonString) {
  var data;
  try { data = JSON.parse(jsonString); } catch (e) { return; }
  if (!data || !data.name) return;

  document.getElementById("char-empty").classList.add("hidden");
  document.getElementById("char-body").classList.remove("hidden");

  document.getElementById("c-name").textContent = data.name;
  document.getElementById("c-level").textContent = "Level " + (data.level || "?");
  document.getElementById("c-race").textContent = data.race || "";
  document.getElementById("c-class").textContent = data.className || data.class || "";
  document.getElementById("c-zone").textContent = data.zone || "Unknown";

  var coords = (typeof data.x === "number" && typeof data.y === "number")
    ? "(" + data.x.toFixed(1) + ", " + data.y.toFixed(1) + ")" : "";
  document.getElementById("c-coords").textContent = coords;

  setBar("hp", data.health, data.healthMax, data.health + " / " + data.healthMax);
  if (data.powerMax && data.powerMax > 0) {
    setBar("mp", data.power, data.powerMax, (data.powerType || "Power") + " " + data.power + " / " + data.powerMax);
    document.querySelector(".bar.mp").style.display = "";
  } else {
    document.querySelector(".bar.mp").style.display = "none";
  }

  var money = data.money || 0; // copper
  document.getElementById("c-gold").textContent = Math.floor(money / 10000);
  document.getElementById("c-silver").textContent = Math.floor((money % 10000) / 100);
  document.getElementById("c-copper").textContent = money % 100;

  var items = data.bags || [];
  document.getElementById("c-slots").textContent = (data.freeSlots != null ? data.freeSlots : "?");
  var ul = document.getElementById("c-items");
  ul.innerHTML = "";
  items.forEach(function (it) {
    var li = document.createElement("li");
    li.innerHTML = "<span>" + esc(it.name) + "</span><span class='q'>x" + (it.count || 1) + "</span>";
    ul.appendChild(li);
  });

  document.getElementById("c-stamp").textContent =
    data.time ? data.time : new Date().toLocaleTimeString();

  // maps live position
  document.getElementById("m-zone").textContent = data.zone || "Unknown";
  document.getElementById("m-coords").textContent = coords || "—";

  // talents for class
  renderTalents(data.className || data.class);
};

function setBar(kind, cur, max, text) {
  var pct = (max && max > 0) ? Math.max(0, Math.min(100, (cur / max) * 100)) : 0;
  document.getElementById("c-" + kind + "-fill").style.width = pct + "%";
  document.getElementById("c-" + kind + "-text").textContent = text || "";
}

function renderTalents(cls) {
  var head = document.getElementById("t-head");
  var wrap = document.getElementById("t-trees");
  wrap.innerHTML = "";
  var trees = TALENTS[cls];
  if (!trees) { head.textContent = "Talents"; return; }
  head.textContent = cls + " talents";
  trees.forEach(function (name) {
    var d = document.createElement("div");
    d.className = "tree";
    d.innerHTML = "<b>" + esc(name) + "</b>";
    wrap.appendChild(d);
  });
}

// ---- notes (localStorage) ----
var NOTES_KEY = "wf_companion_notes";
function loadNotes() {
  try { return JSON.parse(localStorage.getItem(NOTES_KEY) || "[]"); } catch (e) { return []; }
}
function saveNotes(n) {
  try { localStorage.setItem(NOTES_KEY, JSON.stringify(n)); } catch (e) {}
}
function renderNotes() {
  var notes = loadNotes();
  var ul = document.getElementById("note-list");
  ul.innerHTML = "";
  notes.forEach(function (n, i) {
    var li = document.createElement("li");
    if (n.done) li.className = "done";
    var cb = document.createElement("input");
    cb.type = "checkbox";
    cb.checked = !!n.done;
    cb.addEventListener("change", function () { notes[i].done = cb.checked; saveNotes(notes); renderNotes(); });
    var span = document.createElement("span");
    span.textContent = n.text;
    var del = document.createElement("button");
    del.className = "del";
    del.innerHTML = "&times;";
    del.addEventListener("click", function () { notes.splice(i, 1); saveNotes(notes); renderNotes(); });
    li.appendChild(cb); li.appendChild(span); li.appendChild(del);
    ul.appendChild(li);
  });
}
document.getElementById("note-add").addEventListener("click", addNote);
document.getElementById("note-input").addEventListener("keydown", function (e) {
  if (e.key === "Enter") addNote();
});
function addNote() {
  var input = document.getElementById("note-input");
  var text = input.value.trim();
  if (!text) return;
  var notes = loadNotes();
  notes.push({ text: text, done: false });
  saveNotes(notes);
  input.value = "";
  renderNotes();
}

renderNotes();

// tell native the DOM is ready to receive data
if (window.WFHost && WFHost.ready) WFHost.ready();
