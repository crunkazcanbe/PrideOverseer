#!/usr/bin/env python3
"""Pride Overseer art: hand-built Blockbench-style models (Java block/item JSON, open them in Blockbench) and
painted 32px textures for the Command Baton, the War Table and the Rally Flag.
Run from the mod folder:  python3 tools/gen_assets.py"""
import json, math, os, random
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "prideoverseer")
BB = os.path.join(os.path.dirname(__file__), "..", "blockbench")
TX = os.path.join(ROOT, "textures", "blocks")
TI = os.path.join(ROOT, "textures", "items")
for d in (TX, TI, BB, os.path.join(ROOT, "models", "block"), os.path.join(ROOT, "models", "item"), os.path.join(ROOT, "blockstates"), os.path.join(ROOT, "lang")):
    os.makedirs(d, exist_ok=True)

R = random.Random(7)
PRIDE = [(228, 3, 3), (255, 140, 0), (255, 237, 0), (0, 128, 38), (36, 64, 142), (115, 41, 130)]
TRANS = [(91, 206, 250), (245, 169, 184), (255, 255, 255), (245, 169, 184), (91, 206, 250)]

def clamp(v): return max(0, min(255, int(v)))
def shade(c, k): return tuple(clamp(x * k) for x in c[:3]) + ((c[3],) if len(c) > 3 else (255,))
def noise_fill(img, base, amt=0.12, seed=1):
    r = random.Random(seed)
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            px[x, y] = shade(base, 1 + (r.random() - 0.5) * amt * 2)

# ------------------------------------------------------------------ textures (32x32, painted)

def wood(name, base=(118, 74, 42), seed=3):
    im = Image.new("RGBA", (32, 32))
    px = im.load()
    r = random.Random(seed)
    for y in range(32):
        for x in range(32):
            grain = math.sin((x * 0.9 + math.sin(y * 0.35) * 3) * 0.8) * 0.08
            px[x, y] = shade(base, 1 + grain + (r.random() - 0.5) * 0.08)
    for y in (7, 15, 23, 31):                 # plank seams
        for x in range(32): px[x, y] = shade(base, 0.62)
    for _ in range(5):                        # knots
        kx, ky = r.randrange(2, 30), r.randrange(2, 30)
        for dx in range(-1, 2):
            for dy in range(-1, 1): px[(kx + dx) % 32, (ky + dy) % 32] = shade(base, 0.7)
    im.save(os.path.join(TX, name + ".png"))

def metal(name, base, seed=5):
    im = Image.new("RGBA", (32, 32))
    px = im.load()
    for y in range(32):
        for x in range(32):
            k = 1 + 0.25 * math.cos((x + y) * 0.22) + (random.Random(seed * 1000 + x * 37 + y).random() - 0.5) * 0.08
            px[x, y] = shade(base, k)
    d = ImageDraw.Draw(im)
    d.line([(2, 4), (12, 4)], fill=shade(base, 1.6)); d.line([(18, 20), (28, 20)], fill=shade(base, 1.5))
    im.save(os.path.join(TX, name + ".png"))

def gem(name):
    """a cut crystal: pink -> sky-blue glow from the centre, diagonal facet edges, a bright highlight streak"""
    im = Image.new("RGBA", (32, 32))
    px = im.load()
    for y in range(32):
        for x in range(32):
            dx, dy = (x - 15.5) / 16, (y - 15.5) / 16
            rr = min(1, math.sqrt(dx * dx + dy * dy))
            a = (x + (31 - y)) / 62.0
            c = (int(250 * (1 - a) + 120 * a), int(175 * (1 - a) + 215 * a), int(200 * (1 - a) + 255 * a))
            k = 1.35 - rr * 0.55
            if (x + y) % 11 == 0 or (x - y) % 11 == 0: k *= 0.8          # facet edges
            px[x, y] = shade(c + (255,), k)
    d = ImageDraw.Draw(im)
    d.line([(5, 9), (12, 3)], fill=(255, 255, 255, 255), width=2)          # shine
    d.point([(22, 22), (23, 21), (8, 24)], fill=(255, 255, 255, 255))
    im.save(os.path.join(TX, name + ".png"))

def parchment_map(name):
    """the War Table's map: parchment, sea, land, rivers, roads, little village marks and a compass rose"""
    S = 64
    im = Image.new("RGBA", (S, S), (222, 198, 150, 255))
    px = im.load()
    r = random.Random(11)
    def h(x, y):
        return (math.sin(x * 0.13) + math.cos(y * 0.11) + math.sin((x + y) * 0.07) * 1.3 + math.sin(x * 0.31 + y * 0.17) * 0.4)
    for y in range(S):
        for x in range(S):
            v = h(x, y)
            if v < -0.9: c = (120, 170, 200)
            elif v < -0.6: c = (170, 200, 205)
            elif v < 0.9: c = (196, 192, 128)
            elif v < 1.6: c = (150, 170, 100)
            else: c = (140, 120, 90)
            c = tuple(clamp(c[i] * 0.55 + (222, 198, 150)[i] * 0.45 + (r.random() - 0.5) * 14) for i in range(3))
            px[x, y] = c + (255,)
    d = ImageDraw.Draw(im)
    pts = [(4, 50), (16, 44), (25, 38), (34, 36), (45, 26), (60, 20)]
    d.line(pts, fill=(96, 130, 170), width=2)                                    # river
    d.line([(10, 10), (24, 22), (40, 22), (52, 40), (58, 58)], fill=(130, 90, 60), width=1)   # road
    for (vx, vy, col) in ((24, 22, (228, 3, 3)), (52, 40, (36, 64, 142)), (12, 34, (0, 128, 38))):
        d.rectangle([vx - 2, vy - 2, vx + 2, vy + 2], outline=(70, 40, 30), fill=col)
    cx, cy = 52, 10                                                              # compass rose
    d.polygon([(cx, cy - 6), (cx + 2, cy), (cx, cy + 6), (cx - 2, cy)], fill=(90, 50, 40))
    d.polygon([(cx - 6, cy), (cx, cy - 2), (cx + 6, cy), (cx, cy + 2)], fill=(150, 100, 60))
    for i in range(S):                                                           # burnt edge
        for e in range(2):
            for (x, y) in ((i, e), (i, S - 1 - e), (e, i), (S - 1 - e, i)):
                px[x, y] = shade(px[x, y], 0.75 - e * 0.1)
    im.save(os.path.join(TX, name + ".png"))

def cloth(name, stripes, vertical=False):
    im = Image.new("RGBA", (32, 32))
    px = im.load()
    n = len(stripes)
    for y in range(32):
        for x in range(32):
            i = (x if vertical else y) * n // 32
            wave = 1 + 0.12 * math.sin(x * 0.5) * (1 if not vertical else 0) + 0.06 * math.sin(y * 0.7)
            px[x, y] = shade(stripes[i] + (255,), wave)
    im.save(os.path.join(TX, name + ".png"))

def stone(name):
    im = Image.new("RGBA", (32, 32))
    noise_fill(im, (128, 124, 136, 255), 0.14, 9)
    d = ImageDraw.Draw(im)
    for y in (0, 10, 21):
        d.line([(0, y), (31, y)], fill=(86, 82, 96))
    for (x, y) in ((8, 0), (24, 10), (14, 21)):
        d.line([(x, y), (x, y + 10)], fill=(86, 82, 96))
    im.save(os.path.join(TX, name + ".png"))

def figures(name):
    """tiny painted pieces: little soldier / villager tokens"""
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for i, col in enumerate(PRIDE + TRANS[:2]):
        x, y = (i % 4) * 8, (i // 4) * 8
        d.rectangle([x, y, x + 7, y + 7], fill=col + (255,))
        d.rectangle([x + 2, y + 1, x + 5, y + 3], fill=(240, 205, 170, 255))      # face
        d.line([(x, y + 7), (x + 7, y + 7)], fill=shade(col + (255,), 0.6))
    for i in range(4):                                                           # wax / candle colours
        d.rectangle([i * 8, 16, i * 8 + 7, 23], fill=(244, 236, 214, 255))
        d.rectangle([i * 8 + 3, 16, i * 8 + 4, 18], fill=(255, 190, 60, 255))
    d.rectangle([0, 24, 31, 31], fill=(214, 196, 160, 255))                     # scroll paper
    for x in range(0, 32, 4): d.line([(x, 26), (x + 2, 26)], fill=(120, 100, 80, 255))
    im.save(os.path.join(TX, name + ".png"))

wood("war_table_wood", (110, 70, 40), 3)
wood("baton_wood", (78, 44, 30), 4)
metal("gold", (226, 178, 54))
metal("iron", (176, 176, 186))
gem("gem")
parchment_map("war_map")
cloth("pride_cloth", PRIDE)
cloth("trans_cloth", TRANS)
stone("flag_stone")
figures("figures")

# ------------------------------------------------------------------ models

def face_all(tex, uv=(0, 0, 16, 16)):
    return {f: {"texture": tex, "uv": list(uv)} for f in ("north", "south", "east", "west", "up", "down")}

def el(name, a, b, tex, uv=None, rot=None, shade_=True, faces=None):
    e = {"name": name, "from": list(a), "to": list(b), "faces": faces or face_all(tex, uv or (0, 0, 16, 16))}
    if rot: e["rotation"] = rot
    if not shade_: e["shade"] = False
    return e

def save_model(kind, name, model):
    with open(os.path.join(ROOT, "models", kind, name + ".json"), "w") as f: json.dump(model, f, indent=1)
    with open(os.path.join(BB, name + ".json"), "w") as f: json.dump(model, f, indent=1)   # open in Blockbench (Java model)

# War Table: thick top with a map inset, carved apron, four turned legs with gold feet, little figures, flags, a candle
els = []
els.append(el("top", (0, 13, 0), (16, 15, 16), "#wood"))
els.append(el("map", (1.5, 15, 1.5), (14.5, 15.2, 14.5), "#map", faces={"up": {"texture": "#map", "uv": [0, 0, 16, 16]}, "north": {"texture": "#map", "uv": [0, 0, 13, 0.2]},
          "south": {"texture": "#map", "uv": [0, 0, 13, 0.2]}, "east": {"texture": "#map", "uv": [0, 0, 13, 0.2]}, "west": {"texture": "#map", "uv": [0, 0, 13, 0.2]}}))
for (x, z) in ((0.5, 0.5), (0.5, 15.5 - 1), (15.5 - 1, 0.5), (15.5 - 1, 15.5 - 1)):
    pass
els.append(el("rim_n", (0, 15, 0), (16, 15.6, 1), "#wood"))
els.append(el("rim_s", (0, 15, 15), (16, 15.6, 16), "#wood"))
els.append(el("rim_w", (0, 15, 1), (1, 15.6, 15), "#wood"))
els.append(el("rim_e", (15, 15, 1), (16, 15.6, 15), "#wood"))
els.append(el("apron_n", (1.5, 11, 1.5), (14.5, 13, 2.5), "#wood"))
els.append(el("apron_s", (1.5, 11, 13.5), (14.5, 13, 14.5), "#wood"))
els.append(el("apron_w", (1.5, 11, 2.5), (2.5, 13, 13.5), "#wood"))
els.append(el("apron_e", (13.5, 11, 2.5), (14.5, 13, 13.5), "#wood"))
for i, (x, z) in enumerate(((1, 1), (1, 13), (13, 1), (13, 13))):
    els.append(el("leg%d" % i, (x + 0.5, 1, z + 0.5), (x + 1.5, 11, z + 1.5), "#wood"))
    els.append(el("knee%d" % i, (x + 0.25, 7, z + 0.25), (x + 1.75, 8, z + 1.75), "#gold", (0, 0, 4, 4)))
    els.append(el("foot%d" % i, (x, 0, z), (x + 2, 1, z + 2), "#gold", (0, 0, 4, 4)))
els.append(el("brace_w", (1.6, 3, 2.5), (2.4, 4, 13.5), "#wood"))      # H-brace joining the legs
els.append(el("brace_e", (13.6, 3, 2.5), (14.4, 4, 13.5), "#wood"))
els.append(el("brace_mid", (2.4, 3.1, 7.6), (13.6, 3.9, 8.4), "#wood"))
els.append(el("brace_knob", (7.4, 2.8, 7.4), (8.6, 4.2, 8.6), "#gold", (0, 0, 3, 3)))
# little figures (8 colours), standing on the map
spots = [(4, 4), (6, 9), (10, 5), (11, 11), (3, 12), (8, 3)]
for i, (x, z) in enumerate(spots):
    u = (i % 4) * 4, (i // 4) * 4
    els.append(el("figure%d" % i, (x, 15.2, z), (x + 1, 16.7, z + 1), "#fig", (u[0], u[1], u[0] + 4, u[1] + 4)))
    els.append(el("head%d" % i, (x + 0.15, 16.7, z + 0.15), (x + 0.85, 17.3, z + 0.85), "#fig", (u[0] + 1, u[1], u[0] + 3, u[1] + 1.5)))
# two flags on pins
for i, (x, z, cl) in enumerate(((12.5, 3, "#pride"), (5, 6.5, "#trans"))):
    els.append(el("pin%d" % i, (x, 15.2, z), (x + 0.3, 19, z + 0.3), "#gold", (0, 0, 1, 8)))
    els.append(el("pennant%d" % i, (x + 0.3, 17.4, z), (x + 2.6, 18.9, z + 0.2), cl, (0, 0, 16, 16)))
# candle + flame, scroll
els.append(el("candle", (13, 15.2, 12.5), (14, 17.2, 13.5), "#fig", (0, 8, 4, 12)))
els.append(el("flame", (13.3, 17.2, 12.8), (13.7, 17.9, 13.2), "#fig", (12, 8, 14, 10), shade_=False))
els.append(el("scroll", (2, 15.2, 2), (6, 15.9, 3), "#fig", (0, 12, 16, 16), rot={"origin": [4, 15.5, 2.5], "axis": "y", "angle": 22.5}))
wt = {"credit": "Pride Overseer — made with the Blockbench model format", "parent": "block/block", "ambientocclusion": False,
      "textures": {"particle": "prideoverseer:blocks/war_table_wood", "wood": "prideoverseer:blocks/war_table_wood", "map": "prideoverseer:blocks/war_map",
                   "gold": "prideoverseer:blocks/gold", "fig": "prideoverseer:blocks/figures", "pride": "prideoverseer:blocks/pride_cloth", "trans": "prideoverseer:blocks/trans_cloth"},
      "elements": els}
save_model("block", "war_table", wt)

# Rally Flag: stone base, iron pole with gold rings, gold finial, a big pride banner with a swallowtail, trans ribbon
els = []
els.append(el("base", (4, 0, 4), (12, 1.5, 12), "#stone"))
els.append(el("base2", (5.5, 1.5, 5.5), (10.5, 2.5, 10.5), "#stone"))
els.append(el("pole", (7.4, 2.5, 7.4), (8.6, 31, 8.6), "#iron", (0, 0, 2, 16)))
for y in (6, 18, 29):
    els.append(el("ring%d" % y, (7.1, y, 7.1), (8.9, y + 0.6, 8.9), "#gold", (0, 0, 4, 2)))
els.append(el("finial", (7, 31, 7), (9, 32, 9), "#gold", (0, 0, 4, 4)))
els.append(el("crossbar", (8.6, 29.4, 7.6), (16, 30, 8.4), "#gold", (0, 0, 16, 2)))
els.append(el("banner", (8.6, 17, 7.85), (16, 29.4, 8.15), "#pride", faces={"north": {"texture": "#pride", "uv": [0, 0, 16, 16]}, "south": {"texture": "#pride", "uv": [16, 0, 0, 16]},
          "east": {"texture": "#pride", "uv": [0, 0, 1, 16]}, "west": {"texture": "#pride", "uv": [0, 0, 1, 16]}, "up": {"texture": "#pride", "uv": [0, 0, 16, 1]}, "down": {"texture": "#pride", "uv": [0, 15, 16, 16]}}))
els.append(el("tail_l", (8.6, 15, 7.85), (11.6, 17, 8.15), "#pride", (0, 14, 6, 16)))
els.append(el("tail_r", (13, 15, 7.85), (16, 17, 8.15), "#pride", (10, 14, 16, 16)))
els.append(el("ribbon", (8.8, 24, 8.2), (9.6, 28.5, 8.5), "#trans", (0, 0, 2, 16), rot={"origin": [9, 28, 8.3], "axis": "z", "angle": -22.5}))
rf = {"credit": "Pride Overseer — made with the Blockbench model format", "parent": "block/block", "ambientocclusion": False,
      "textures": {"particle": "prideoverseer:blocks/pride_cloth", "stone": "prideoverseer:blocks/flag_stone", "iron": "prideoverseer:blocks/iron",
                   "gold": "prideoverseer:blocks/gold", "pride": "prideoverseer:blocks/pride_cloth", "trans": "prideoverseer:blocks/trans_cloth"},
      "elements": els,
      "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, -3, 0], "scale": [0.42, 0.42, 0.42]},
                  "ground": {"translation": [0, 3, 0], "scale": [0.3, 0.3, 0.3]},
                  "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
                  "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.3, 0.3, 0.3]}}}
save_model("block", "rally_flag", rf)

# Command Baton: dark wood shaft with a gold grip spiral, gold collar, a faceted pride gem held by four gold claws
els = []
els.append(el("shaft", (7.25, 0, 7.25), (8.75, 12, 8.75), "#wood", (0, 0, 3, 16)))
for i, y in enumerate((1, 3, 5, 7)):
    els.append(el("wrap%d" % i, (7, y, 7), (9, y + 0.6, 9), "#gold", (0, 0, 4, 1), rot={"origin": [8, y, 8], "axis": "y", "angle": 22.5 if i % 2 else -22.5}))
els.append(el("pommel", (6.75, -1, 6.75), (9.25, 0, 9.25), "#gold", (0, 0, 5, 2)))
els.append(el("collar", (6.5, 12, 6.5), (9.5, 13, 9.5), "#gold", (0, 0, 6, 2)))
els.append(el("gem", (6.75, 13.2, 6.75), (9.25, 15.7, 9.25), "#gem", rot={"origin": [8, 14.5, 8], "axis": "y", "angle": 45}, shade_=False))
els.append(el("gem_tip", (7.4, 15.7, 7.4), (8.6, 16.6, 8.6), "#gem", rot={"origin": [8, 16, 8], "axis": "y", "angle": 45}, shade_=False))
for i, (dx, dz) in enumerate(((-1, 0), (1, 0), (0, -1), (0, 1))):
    x, z = 8 + dx * 1.45, 8 + dz * 1.45
    els.append(el("claw%d" % i, (x - 0.3, 12.8, z - 0.3), (x + 0.3, 15, z + 0.3), "#gold", (0, 0, 1, 4)))
els.append(el("ribbon", (8.75, 9, 7.9), (9.25, 12, 8.1), "#pride", (0, 0, 2, 16)))
bt = {"credit": "Pride Overseer — made with the Blockbench model format", "parent": "item/handheld",
      "textures": {"particle": "prideoverseer:blocks/baton_wood", "wood": "prideoverseer:blocks/baton_wood", "gold": "prideoverseer:blocks/gold",
                   "gem": "prideoverseer:blocks/gem", "pride": "prideoverseer:blocks/pride_cloth"},
      "elements": els,
      "display": {"gui": {"rotation": [0, 0, -45], "translation": [0, 0, 0], "scale": [1.1, 1.1, 1.1]},
                  "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
                  "fixed": {"rotation": [0, 0, -45], "scale": [1, 1, 1]},
                  "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 3, 1], "scale": [0.85, 0.85, 0.85]},
                  "thirdperson_lefthand": {"rotation": [0, -90, 0], "translation": [0, 3, 1], "scale": [0.85, 0.85, 0.85]},
                  "firstperson_righthand": {"rotation": [0, -90, 20], "translation": [1, 3, 1], "scale": [0.68, 0.68, 0.68]},
                  "firstperson_lefthand": {"rotation": [0, 90, -20], "translation": [1, 3, 1], "scale": [0.68, 0.68, 0.68]}}}
save_model("item", "command_baton", bt)

# item models for the blocks + blockstates
for b in ("war_table", "rally_flag"):
    with open(os.path.join(ROOT, "models", "item", b + ".json"), "w") as f: json.dump({"parent": "prideoverseer:block/" + b}, f, indent=1)
with open(os.path.join(ROOT, "blockstates", "war_table.json"), "w") as f:
    json.dump({"variants": {"facing=north": {"model": "prideoverseer:war_table"}, "facing=east": {"model": "prideoverseer:war_table", "y": 90},
                            "facing=south": {"model": "prideoverseer:war_table", "y": 180}, "facing=west": {"model": "prideoverseer:war_table", "y": 270}}}, f, indent=1)
with open(os.path.join(ROOT, "blockstates", "rally_flag.json"), "w") as f:
    json.dump({"variants": {"normal": {"model": "prideoverseer:rally_flag"}}}, f, indent=1)

with open(os.path.join(ROOT, "lang", "en_us.lang"), "w") as f:
    f.write("itemGroup.prideoverseer=Pride Overseer\n"
            "item.prideoverseer.command_baton.name=Command Baton\n"
            "tile.prideoverseer.war_table.name=War Table\n"
            "tile.prideoverseer.rally_flag.name=Rally Flag\n"
            "key.categories.Pride Overseer=Pride Overseer\n")
print("assets written")
