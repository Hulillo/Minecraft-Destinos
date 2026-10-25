"""Genera el datapack y el resource pack de idiomas de Destinos: Reinos v0.1."""
import json, os, shutil, sys, zipfile
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from culturas import CULTURAS, ORDEN, BIOMAS

# Uso: python build.py <salida> [--mod <carpeta generated del mod>]
OUT = sys.argv[1]
MOD = sys.argv[sys.argv.index("--mod") + 1] if "--mod" in sys.argv else None
DP = os.path.join(OUT, "destinos_reinos")
RP = os.path.join(OUT, "destinos_reinos_idiomas")
NS = os.path.join(DP, "data", "destinos")

# Constantes compartidas con sim.py (si cambias una, cambia la otra)
C = dict(S_D=1500, S_K=6000, WARP=1200, D_FRONTERA=2800, P_FRONTERA=45, P_SALVAJE=4,
         K_JZ=7919, K_REINO=104729, K_SALV=1299709, K_WARP=15485863)
SALES = ["NR", "Casa", "Lema", "Rasgo", "Color", "ND", "Tipo", "Gen", "Sen", "Fama", "Geo", "Mar"]  # sal + 1..12
N_LEMAS, N_RASGOS, N_FAMAS, N_GEO = 12, 5, 6, 5
COLORES = ["#c0392b", "#2e5fa8", "#2e8b3a", "#7b3fa0", "#c9a227", "#d8d8d8", "#a0582c", "#138d90", "#b0306a", "#5b6f7c"]

files = {}
def fn(path, lines):
    files[f"function/{path}.mcfunction"] = "\n".join(l for l in lines if l is not None) + "\n"
def js(path, obj, base=NS):
    files_json.append((os.path.join(base, path), obj))
files_json = []
def op(a, o, b, ca="dr.calc", cb="dr.calc"):
    return f"scoreboard players operation {a} {ca} {o} {b} {cb}"

# ---------------------------------------------------------------- init
fn("init/load", [
    "# Destinos: Reinos v0.1 · carga",
    *[f"scoreboard objectives add {o} dummy" for o in
      ["dr.calc", "dr.const", "dr.dx", "dr.dz", "dr.kx", "dr.kz", "dr.w", "dr.init", "dr.id", "dr.hud", "dr.mar"]],
    "scoreboard objectives add destinos.donde trigger",
    "scoreboard objectives add destinos.hud trigger",
    "scoreboard objectives add destinos.mapa trigger",
    "tag @a remove destinos.mapa",
    *[f"scoreboard players set #{n} dr.const {n}" for n in (2, 3, 4, 5, 6, 8, 10, 12, 16, 24, 100, 65536)],
    "scoreboard players set #-1 dr.const -1",
    "scoreboard players set #P1 dr.const 73856093",
    "scoreboard players set #P2 dr.const 19349663",
    "scoreboard players set #LCG_A dr.const 1103515245",
    "scoreboard players set #LCG_C dr.const 12345",
    *[f"scoreboard players set #{k} dr.const {v}" for k, v in C.items() if k != "D_FRONTERA"],
    f"scoreboard players set #2WARP dr.const {2 * C['WARP']}",
    f"scoreboard players set #DFR2 dr.const {C['D_FRONTERA'] ** 2}",
    "execute unless data storage destinos:mundo sal run function destinos:init/sal",
    "execute store result score #sal dr.const run data get storage destinos:mundo sal",
    *sum([[op(f"#sal{s}", "=", "#sal", "dr.const", "dr.const"),
           f"scoreboard players add #sal{s} dr.const {i + 1}"] for i, s in enumerate(SALES)], []),
    *sum([[op(f"#sal{n}", "=", "#sal", "dr.const", "dr.const"), op(f"#sal{n}", "+=", f"#K_{n}", "dr.const", "dr.const")]
          for n in ("JZ", "REINO", "SALV", "WARP")], []),
    "scoreboard players operation #salWZ dr.const = #salWARP dr.const",
    "scoreboard players operation #salWZ dr.const += #K_JZ dr.const",
    "function destinos:init/tablas",
    "schedule function destinos:bucle 20t replace",
])
fn("init/sal", [
    "# Primera carga en este mundo: la sal sale de una secuencia sembrada con la semilla del mundo",
    "execute store result storage destinos:mundo sal int 1 run random value 1..2000000000 destinos:sal",
    "data modify storage destinos:mundo version set value 1",
    "data modify storage destinos:mundo reinos set value {}",
    "data modify storage destinos:mundo ducados set value {}",
])
tablas = {k: {c: v[c] for c in ("pre", "mid", "suf", "m", "f", "casas")} for k, v in CULTURAS.items()}
fn("init/tablas", [
    "# Tablas de nombres por cultura (generado por build.py, no editar a mano)",
    *[f"data modify storage destinos:tablas {k} set value {json.dumps(v, ensure_ascii=False)}" for k, v in tablas.items()],
    f"data modify storage destinos:tablas colores set value {json.dumps(COLORES)}",
])

# ---------------------------------------------------------------- bucle
fn("bucle", [
    "schedule function destinos:bucle 20t replace",
    "execute as @a unless score @s dr.id matches 1.. run function destinos:jugador/nuevo",
    "scoreboard players enable @a destinos.donde",
    "scoreboard players enable @a destinos.hud",
    "scoreboard players enable @a destinos.mapa",
    "execute as @a[scores={destinos.mapa=1..}] at @s run function destinos:jugador/mapa",
    "execute as @a[scores={destinos.hud=1..}] run function destinos:jugador/hud",
    "execute as @a[gamemode=!spectator] at @s if dimension minecraft:overworld run function destinos:jugador/comprobar",
    "execute as @a[scores={destinos.donde=1..}] run function destinos:jugador/donde",
])
fn("jugador/nuevo", [
    "scoreboard players add #sig dr.const 1",
    "scoreboard players operation @s dr.id = #sig dr.const",
    "scoreboard players set @s dr.hud 0",
])

# ---------------------------------------------------------------- geo
fn("geo/hash", [
    "# Entrada: #hx #hz #hs · Salida: #h en [0, 32768]. Idéntico a hash_() de sim.py",
    op("#h", "=", "#hx"), op("#h", "*=", "#P1", cb="dr.const"),
    op("#t", "=", "#hz"), op("#t", "*=", "#P2", cb="dr.const"),
    op("#h", "+=", "#t"), op("#h", "+=", "#hs"),
    op("#h", "*=", "#h"), op("#h", "+=", "#hs"),
    op("#h", "*=", "#LCG_A", cb="dr.const"), op("#h", "+=", "#LCG_C", cb="dr.const"),
    op("#h", "/=", "#65536", cb="dr.const"),
    "execute if score #h dr.calc matches ..-1 run " + op("#h", "*=", "#-1", cb="dr.const"),
])
vor = [
    "# Entrada: #vx #vz #S #vsal · Salida: #wcx #wcz (celda), #wctx #wctz (centro), #best (distancia²)",
    op("#cx", "=", "#vx"), op("#cx", "/=", "#S"),
    op("#cz", "=", "#vz"), op("#cz", "/=", "#S"),
    op("#j0", "=", "#S"), op("#j0", "/=", "#8", cb="dr.const"),
    op("#jr", "=", "#S"), op("#jr", "*=", "#3", cb="dr.const"), op("#jr", "/=", "#4", cb="dr.const"),
    "scoreboard players set #best dr.calc 2147483647",
]
for ox in (-1, 0, 1):
    for oz in (-1, 0, 1):
        vor += [f"scoreboard players set #ox dr.calc {ox}", f"scoreboard players set #oz dr.calc {oz}",
                "function destinos:geo/vecino"]
fn("geo/voronoi", vor)
fn("geo/vecino", [
    op("#hx", "=", "#cx"), op("#hx", "+=", "#ox"),
    op("#hz", "=", "#cz"), op("#hz", "+=", "#oz"),
    op("#hs", "=", "#vsal"), "function destinos:geo/hash",
    op("#jx", "=", "#h"), op("#jx", "%=", "#jr"), op("#jx", "+=", "#j0"),
    op("#hs", "+=", "#K_JZ", cb="dr.const"), "function destinos:geo/hash",
    op("#jz", "=", "#h"), op("#jz", "%=", "#jr"), op("#jz", "+=", "#j0"),
    op("#ctx", "=", "#hx"), op("#ctx", "*=", "#S"), op("#ctx", "+=", "#jx"),
    op("#ctz", "=", "#hz"), op("#ctz", "*=", "#S"), op("#ctz", "+=", "#jz"),
    op("#dx", "=", "#vx"), op("#dx", "-=", "#ctx"), op("#dx", "*=", "#dx"),
    op("#dz", "=", "#vz"), op("#dz", "-=", "#ctz"), op("#dz", "*=", "#dz"),
    op("#dx", "+=", "#dz"),
    "execute if score #dx dr.calc < #best dr.calc run function destinos:geo/mejor",
])
fn("geo/mejor", [op("#best", "=", "#dx"), op("#wcx", "=", "#hx"), op("#wcz", "=", "#hz"),
                 op("#wctx", "=", "#ctx"), op("#wctz", "=", "#ctz")])
fn("geo/territorio", [
    "# Entrada: #vx #vz (bloques) · Salida: #tdx #tdz (ducado), #tkx #tkz (reino), #tw (1 = tierra salvaje)",
    op("#S", "=", "#S_D", cb="dr.const"), op("#vsal", "=", "#sal", cb="dr.const"),
    "function destinos:geo/voronoi",
    op("#tdx", "=", "#wcx"), op("#tdz", "=", "#wcz"), op("#tdctx", "=", "#wctx"), op("#tdctz", "=", "#wctz"),
    "# el centro del ducado se desplaza un poco antes de buscar su reino (fronteras orgánicas)",
    op("#hx", "=", "#tdx"), op("#hz", "=", "#tdz"),
    op("#hs", "=", "#salWARP", cb="dr.const"), "function destinos:geo/hash",
    op("#vx", "=", "#h"), op("#vx", "%=", "#2WARP", cb="dr.const"), op("#vx", "+=", "#tdctx"), op("#vx", "-=", "#WARP", cb="dr.const"),
    op("#hs", "=", "#salWZ", cb="dr.const"), "function destinos:geo/hash",
    op("#vz", "=", "#h"), op("#vz", "%=", "#2WARP", cb="dr.const"), op("#vz", "+=", "#tdctz"), op("#vz", "-=", "#WARP", cb="dr.const"),
    op("#S", "=", "#S_K", cb="dr.const"), op("#vsal", "=", "#salREINO", cb="dr.const"),
    "function destinos:geo/voronoi",
    op("#tkx", "=", "#wcx"), op("#tkz", "=", "#wcz"), op("#tdk", "=", "#best"),
    "# ¿tierra salvaje? un poco en cualquier sitio y bastante lejos del centro de los reinos",
    op("#hx", "=", "#tdx"), op("#hz", "=", "#tdz"),
    op("#hs", "=", "#salSALV", cb="dr.const"), "function destinos:geo/hash",
    op("#tir", "=", "#h"), op("#tir", "%=", "#100", cb="dr.const"),
    "scoreboard players set #tw dr.calc 0",
    "execute if score #tir dr.calc < #P_SALVAJE dr.const run scoreboard players set #tw dr.calc 1",
    "execute if score #tdk dr.calc > #DFR2 dr.const if score #tir dr.calc < #P_FRONTERA dr.const run scoreboard players set #tw dr.calc 1",
])

# ---------------------------------------------------------------- jugador
fn("jugador/comprobar", [
    "execute store result score #vx dr.calc run data get entity @s Pos[0]",
    "execute store result score #vz dr.calc run data get entity @s Pos[2]",
    "# Con Destinos Core, #tlisto = 0 mientras el mod calcula esa zona: se espera al siguiente segundo",
    "scoreboard players set #tlisto dr.calc 1",
    "function destinos:geo/territorio",
    "execute if score #tlisto dr.calc matches 0 run return 0",
    "scoreboard players set #cd dr.calc 0",
    "execute unless score @s dr.dx = #tdx dr.calc run scoreboard players set #cd dr.calc 1",
    "execute unless score @s dr.dz = #tdz dr.calc run scoreboard players set #cd dr.calc 1",
    "execute unless score @s dr.init matches 1 run scoreboard players set #cd dr.calc 1",
    "scoreboard players set #cr dr.calc 0",
    "execute unless score @s dr.kx = #tkx dr.calc run scoreboard players set #cr dr.calc 1",
    "execute unless score @s dr.kz = #tkz dr.calc run scoreboard players set #cr dr.calc 1",
    "execute unless score @s dr.w = #tw dr.calc run scoreboard players set #cr dr.calc 1",
    "execute unless score @s dr.init matches 1 run scoreboard players set #cr dr.calc 1",
    "# mar abierto: para entrar hace falta océano aquí y a 24 bloques en las 4 direcciones (evita parpadeos en la costa)",
    "scoreboard players set #mar dr.calc 0",
    "execute if score @s dr.mar matches 1 if biome ~ ~ ~ #minecraft:is_ocean run scoreboard players set #mar dr.calc 1",
    "execute unless score @s dr.mar matches 1 if biome ~ ~ ~ #minecraft:is_ocean if biome ~24 ~ ~ #minecraft:is_ocean if biome ~-24 ~ ~ #minecraft:is_ocean if biome ~ ~ ~24 #minecraft:is_ocean if biome ~ ~ ~-24 #minecraft:is_ocean run scoreboard players set #mar dr.calc 1",
    "execute unless score @s dr.mar = #mar dr.calc run scoreboard players set #cd dr.calc 1",
    "execute unless score @s dr.mar = #mar dr.calc run scoreboard players set #cr dr.calc 1",
    "execute if score #cd dr.calc matches 1 run function destinos:jugador/cambio",
])
def guarda_args(dst="a"):
    return [f"execute store result storage destinos:tmp {dst}.dx int 1 run scoreboard players get #tdx dr.calc",
            f"execute store result storage destinos:tmp {dst}.dz int 1 run scoreboard players get #tdz dr.calc",
            f"execute store result storage destinos:tmp {dst}.kx int 1 run scoreboard players get #tkx dr.calc",
            f"execute store result storage destinos:tmp {dst}.kz int 1 run scoreboard players get #tkz dr.calc",
            f"execute store result storage destinos:tmp {dst}.id int 1 run scoreboard players get @s dr.id"]
fn("jugador/cambio", [
    *guarda_args(),
    "execute if score #mar dr.calc matches 0 run function destinos:reino/entrar with storage destinos:tmp a",
    "execute if score #mar dr.calc matches 1 run function destinos:mar/entrar with storage destinos:tmp a",
    "scoreboard players operation @s dr.dx = #tdx dr.calc", "scoreboard players operation @s dr.dz = #tdz dr.calc",
    "scoreboard players operation @s dr.kx = #tkx dr.calc", "scoreboard players operation @s dr.kz = #tkz dr.calc",
    "scoreboard players operation @s dr.w = #tw dr.calc",
    "scoreboard players operation @s dr.mar = #mar dr.calc",
    "scoreboard players set @s dr.init 1",
])
fn("jugador/donde", [
    "scoreboard players set @s destinos.donde 0",
    "execute unless score @s dr.init matches 1 run return fail",
    "execute store result storage destinos:tmp a.dx int 1 run scoreboard players get @s dr.dx",
    "execute store result storage destinos:tmp a.dz int 1 run scoreboard players get @s dr.dz",
    "execute store result storage destinos:tmp a.kx int 1 run scoreboard players get @s dr.kx",
    "execute store result storage destinos:tmp a.kz int 1 run scoreboard players get @s dr.kz",
    "execute if score @s dr.mar matches 1 run return run function destinos:jugador/donde_mar with storage destinos:tmp a",
    "function destinos:jugador/donde_m with storage destinos:tmp a",
])
fn("jugador/donde_mar", [
    '$execute if data storage destinos:mundo mares."m$(dx)_$(dz)" run function destinos:hud/info_mar with storage destinos:mundo mares."m$(dx)_$(dz)"',
])
fn("jugador/donde_m", [
    '$execute if data storage destinos:mundo ducados."d$(dx)_$(dz)"{salvaje:1b} run function destinos:hud/info_salvaje with storage destinos:mundo ducados."d$(dx)_$(dz)"',
    '$execute if data storage destinos:mundo ducados."d$(dx)_$(dz)"{salvaje:0b} run function destinos:hud/info_noble with storage destinos:mundo ducados."d$(dx)_$(dz)"',
    '$execute if data storage destinos:mundo ducados."d$(dx)_$(dz)"{salvaje:0b} run function destinos:hud/info_reino with storage destinos:mundo reinos."k$(kx)_$(kz)"',
])
fn("jugador/hud", [
    "scoreboard players set @s destinos.hud 0",
    "execute store result storage destinos:tmp a.id int 1 run scoreboard players get @s dr.id",
    "execute if score @s dr.hud matches 1 run return run function destinos:jugador/hud_off",
    "scoreboard players set @s dr.hud 1",
    "function destinos:hud/bossbar_crear with storage destinos:tmp a",
    "scoreboard players set @s dr.init 0",
])
fn("jugador/hud_off", [
    "scoreboard players set @s dr.hud 0",
    "function destinos:hud/bossbar_ocultar with storage destinos:tmp a",
])

# ---------------------------------------------------------------- reino
fn("reino/entrar", [
    "# Macro: dx dz kx kz id · se ejecuta como el jugador y en su posición",
    '$execute if score #tw dr.calc matches 0 unless data storage destinos:mundo reinos."k$(kx)_$(kz)" run function destinos:reino/generar_reino with storage destinos:tmp a',
    '$execute unless data storage destinos:mundo ducados."d$(dx)_$(dz)" run function destinos:reino/generar_ducado with storage destinos:tmp a',
    '$data modify storage destinos:tmp t set from storage destinos:mundo ducados."d$(dx)_$(dz)"',
    "$data modify storage destinos:tmp t.id set value $(id)",
    "title @s times 10 70 20",
    "execute if score #tw dr.calc matches 1 run function destinos:hud/salvaje with storage destinos:tmp t",
    "execute if score #tw dr.calc matches 0 run function destinos:hud/noble with storage destinos:tmp t",
    "execute if score #cr dr.calc matches 1 run playsound minecraft:item.goat_horn.sound.0 master @s ~ ~ ~ 0.35 1",
    "execute if score #cr dr.calc matches 0 run playsound minecraft:block.bell.use master @s ~ ~ ~ 0.4 0.8",
    '$execute if score #tw dr.calc matches 0 if data storage destinos:mundo reinos."k$(kx)_$(kz)"{descubierto:0b} run function destinos:hud/primer_reino with storage destinos:mundo reinos."k$(kx)_$(kz)"',
    '$execute if score #tw dr.calc matches 0 run data modify storage destinos:mundo reinos."k$(kx)_$(kz)".descubierto set value 1b',
    '$execute if score #tw dr.calc matches 0 if data storage destinos:mundo ducados."d$(dx)_$(dz)"{descubierto:0b} run function destinos:hud/primer_ducado with storage destinos:tmp t',
    '$data modify storage destinos:mundo ducados."d$(dx)_$(dz)".descubierto set value 1b',
    "execute if score @s dr.hud matches 1 if score #tw dr.calc matches 1 run function destinos:hud/bossbar_salvaje with storage destinos:tmp t",
    "execute if score @s dr.hud matches 1 if score #tw dr.calc matches 0 run function destinos:hud/bossbar_noble with storage destinos:tmp t",
])
def elegir(lista_expr, mod, sal, dst):
    """hash con #sal<sal> → índice % mod → copia lista[índice] a destinos:tmp <dst>"""
    return [op("#hs", "=", f"#sal{sal}", cb="dr.const"), "function destinos:geo/hash",
            op("#h", "%=", f"#{mod}", cb="dr.const"),
            "execute store result storage destinos:tmp idx.i int 1 run scoreboard players get #h dr.calc",
            f'data modify storage destinos:tmp idx.lista set value "{lista_expr}"',
            "function destinos:util/elegir with storage destinos:tmp idx",
            f"data modify storage destinos:tmp {dst} set from storage destinos:tmp elegido"]
def indice(mod, sal, dst):
    return [op("#hs", "=", f"#sal{sal}", cb="dr.const"), "function destinos:geo/hash",
            op("#h", "%=", f"#{mod}", cb="dr.const"),
            f"execute store result storage destinos:tmp {dst} int 1 run scoreboard players get #h dr.calc"]
fn("util/elegir", ["$data modify storage destinos:tmp elegido set from storage destinos:tablas $(lista)[$(i)]"])
fn("reino/generar_reino", [
    "# Macro: kx kz",
    "function destinos:cultura/detectar",
    "$scoreboard players set #hx dr.calc $(kx)",
    "$scoreboard players set #hz dr.calc $(kz)",
    "data modify storage destinos:tmp nuevo set value {descubierto:0b}",
    op("#hs", "=", "#salNR", cb="dr.const"), "function destinos:nombres/generar",
    "data modify storage destinos:tmp nuevo.nombre set from storage destinos:tmp gen.nombre",
    "data modify storage destinos:tmp nuevo.cultura set from storage destinos:tmp gen.cultura",
    "function destinos:reino/casa with storage destinos:tmp gen",
    *indice(N_LEMAS, "Lema", "nuevo.lema"),
    *indice(N_RASGOS, "Rasgo", "nuevo.rasgo"),
    *elegir("colores", 10, "Color", "nuevo.color"),
    '$data modify storage destinos:mundo reinos."k$(kx)_$(kz)" set from storage destinos:tmp nuevo',
])
fn("reino/casa", [
    "# Macro: cultura",
    *elegir("$(cultura).casas", 12, "Casa", "nuevo.casa")[:-1],
    "data modify storage destinos:tmp nuevo.casa set from storage destinos:tmp elegido",
])
# los $(cultura) dentro de reino/casa hacen que sus líneas sean macro
files["function/reino/casa.mcfunction"] = "\n".join(
    ("$" + l if "$(" in l and not l.startswith("$") else l) for l in files["function/reino/casa.mcfunction"].splitlines()) + "\n"
fn("reino/generar_ducado", [
    "# Macro: dx dz kx kz",
    "$scoreboard players set #hx dr.calc $(dx)",
    "$scoreboard players set #hz dr.calc $(dz)",
    "data modify storage destinos:tmp nuevo set value {descubierto:0b}",
    "execute if score #tw dr.calc matches 1 run function destinos:reino/ducado_salvaje",
    "execute if score #tw dr.calc matches 0 run function destinos:reino/ducado_noble with storage destinos:tmp a",
    '$data modify storage destinos:mundo ducados."d$(dx)_$(dz)" set from storage destinos:tmp nuevo',
])
fn("reino/ducado_salvaje", [
    "function destinos:cultura/detectar",
    op("#hs", "=", "#salND", cb="dr.const"), "function destinos:nombres/generar",
    "data modify storage destinos:tmp nuevo.nombre set from storage destinos:tmp gen.nombre",
    "data modify storage destinos:tmp nuevo.cultura set from storage destinos:tmp gen.cultura",
    "data modify storage destinos:tmp nuevo.salvaje set value 1b",
    *indice(N_GEO, "Geo", "nuevo.geo"),
])
fn("reino/ducado_noble", [
    "# Macro: kx kz",
    '$data modify storage destinos:tmp gen.cultura set from storage destinos:mundo reinos."k$(kx)_$(kz)".cultura',
    op("#hs", "=", "#salND", cb="dr.const"), "function destinos:nombres/generar",
    "data modify storage destinos:tmp nuevo.nombre set from storage destinos:tmp gen.nombre",
    "data modify storage destinos:tmp nuevo.cultura set from storage destinos:tmp gen.cultura",
    "data modify storage destinos:tmp nuevo.salvaje set value 0b",
    '$data modify storage destinos:tmp nuevo.reino set value "k$(kx)_$(kz)"',
    '$data modify storage destinos:tmp nuevo.rnombre set from storage destinos:mundo reinos."k$(kx)_$(kz)".nombre',
    '$data modify storage destinos:tmp nuevo.color set from storage destinos:mundo reinos."k$(kx)_$(kz)".color',
    "# tipo: 60% ducado, 20% condado, 20% marca",
    op("#hs", "=", "#salTipo", cb="dr.const"), "function destinos:geo/hash", op("#h", "%=", "#10", cb="dr.const"),
    'data modify storage destinos:tmp nuevo.tipo set value "ducado"',
    'execute if score #h dr.calc matches 6..7 run data modify storage destinos:tmp nuevo.tipo set value "condado"',
    'execute if score #h dr.calc matches 8..9 run data modify storage destinos:tmp nuevo.tipo set value "marca"',
    "# quién gobierna",
    op("#hs", "=", "#salGen", cb="dr.const"), "function destinos:geo/hash", op("#h", "%=", "#2", cb="dr.const"),
    'data modify storage destinos:tmp nuevo.g set value "m"',
    'execute if score #h dr.calc matches 1 run data modify storage destinos:tmp nuevo.g set value "f"',
    "function destinos:reino/senor with storage destinos:tmp nuevo",
    *indice(N_FAMAS, "Fama", "nuevo.fama"),
])
fn("reino/senor", [
    "# Macro: cultura g",
    op("#hs", "=", "#salSen", cb="dr.const"), "function destinos:geo/hash",
    op("#h", "%=", "#12", cb="dr.const"),
    "execute store result storage destinos:tmp idx.i int 1 run scoreboard players get #h dr.calc",
    '$data modify storage destinos:tmp idx.lista set value "$(cultura).$(g)"',
    "function destinos:util/elegir with storage destinos:tmp idx",
    "data modify storage destinos:tmp nuevo.senor set from storage destinos:tmp elegido",
])

# ---------------------------------------------------------------- nombres
fn("nombres/generar", [
    "# Entrada: #hx #hz #hs + destinos:tmp gen.cultura · Salida: destinos:tmp gen.nombre",
    "function destinos:geo/hash", op("#h", "%=", "#16", cb="dr.const"),
    "execute store result storage destinos:tmp gen.i1 int 1 run scoreboard players get #h dr.calc",
    op("#hs", "+=", "#K_JZ", cb="dr.const"), "function destinos:geo/hash", op("#h", "%=", "#8", cb="dr.const"),
    "execute store result storage destinos:tmp gen.i2 int 1 run scoreboard players get #h dr.calc",
    op("#hs", "+=", "#K_JZ", cb="dr.const"), "function destinos:geo/hash", op("#h", "%=", "#16", cb="dr.const"),
    "execute store result storage destinos:tmp gen.i3 int 1 run scoreboard players get #h dr.calc",
    "function destinos:nombres/componer with storage destinos:tmp gen",
])
fn("nombres/componer", [
    "$data modify storage destinos:tmp gen.a set from storage destinos:tablas $(cultura).pre[$(i1)]",
    "$data modify storage destinos:tmp gen.b set from storage destinos:tablas $(cultura).mid[$(i2)]",
    "$data modify storage destinos:tmp gen.c set from storage destinos:tablas $(cultura).suf[$(i3)]",
    "function destinos:nombres/unir with storage destinos:tmp gen",
])
fn("nombres/unir", ['$data modify storage destinos:tmp gen.nombre set value "$(a)$(b)$(c)"'])

# ---------------------------------------------------------------- cultura
det = ["# Elige la cultura por el bioma donde está el jugador. Salida: destinos:tmp gen.cultura",
       'data modify storage destinos:tmp gen.cultura set value "llanura"',
       "scoreboard players set #cul dr.calc 0"]
det += ["# islas: océano cálido aquí o a 48 bloques"]
for pos in ("~ ~ ~", "~48 ~ ~", "~-48 ~ ~", "~ ~ ~48", "~ ~ ~-48"):
    det.append(f'execute if score #cul dr.calc matches 0 if biome {pos} #destinos:cultura/islas run scoreboard players set #cul dr.calc 1')
det.append('execute if score #cul dr.calc matches 1 run data modify storage destinos:tmp gen.cultura set value "islas"')
for k in ORDEN[1:]:
    det += [f'execute if score #cul dr.calc matches 0 if biome ~ ~ ~ #destinos:cultura/{k} run data modify storage destinos:tmp gen.cultura set value "{k}"',
            f"execute if score #cul dr.calc matches 0 if biome ~ ~ ~ #destinos:cultura/{k} run scoreboard players set #cul dr.calc 1"]
fn("cultura/detectar", det)
for k, v in BIOMAS.items():
    js(f"tags/worldgen/biome/cultura/{k}.json", {"replace": False, "values": v})

# ---------------------------------------------------------------- hud
T = lambda key, fb, w=None, **kw: {"translate": key, "fallback": fb, **({"with": w} if w else {}), **kw}
def snbt(o):
    """JSON → SNBT de componente de texto (claves sin comillas)."""
    if isinstance(o, dict):
        return "{" + ",".join(f"{k}:{snbt(v)}" for k, v in o.items()) + "}"
    if isinstance(o, list):
        return "[" + ",".join(snbt(v) for v in o) + "]"
    if isinstance(o, bool):
        return "true" if o else "false"
    return json.dumps(o, ensure_ascii=False)

fn("hud/noble", [
    "# Macro: registro de ducado noble",
    "$title @s subtitle " + snbt(T("destinos.reino", "Reino de %s", ["$(rnombre)"], color="$(color)")),
    "$title @s title " + snbt(T("destinos.tipo.$(tipo)", "$(nombre)", ["$(nombre)"], color="$(color)")),
])
fn("hud/salvaje", [
    "# Macro: registro de tierra salvaje",
    "title @s subtitle " + snbt(T("destinos.salvajes", "Tierras salvajes", color="gray")),
    "$title @s title " + snbt(T("destinos.salvaje.$(geo)", "$(nombre)", ["$(nombre)"], color="#9e9e9e")),
])
fn("hud/primer_reino", [
    "# Macro: registro de reino",
    "$tellraw @s " + snbt(["", {"text": "⚜ ", "color": "$(color)"},
                          T("destinos.primer_reino", "Entras por primera vez en el Reino de %s, bajo el estandarte de la Casa %s.",
                            [{"text": "$(nombre)", "color": "$(color)", "bold": True}, {"text": "$(casa)", "color": "$(color)"}]),
                          {"text": "\n   "}, T("destinos.lema.$(lema)", "", italic=True, color="gray")]),
])
fn("hud/primer_ducado", [
    "# Macro: registro de ducado noble",
    "$tellraw @s " + snbt(T("destinos.gobierna", "Gobierna %s · Fama: %s",
                             [T("destinos.titulo.$(tipo).$(g)", "$(senor)", ["$(senor)"]), T("destinos.fama.$(fama)", "")],
                             color="gray")),
])
fn("hud/info_noble", [
    "$tellraw @s " + snbt(["", {"text": "⚑ ", "color": "$(color)"},
                          T("destinos.tipo.$(tipo)", "$(nombre)", ["$(nombre)"], color="$(color)", bold=True),
                          {"text": " · "}, T("destinos.reino", "Reino de %s", ["$(rnombre)"], color="$(color)")]),
    "$tellraw @s " + snbt(T("destinos.gobierna", "Gobierna %s · Fama: %s",
                             [T("destinos.titulo.$(tipo).$(g)", "$(senor)", ["$(senor)"]), T("destinos.fama.$(fama)", "")],
                             color="gray")),
])
fn("hud/info_reino", [
    "$tellraw @s " + snbt(T("destinos.info_reino", "Casa %s · Cultura %s · Reino %s",
                             ["$(casa)", T("destinos.cultura.$(cultura)", "$(cultura)"), T("destinos.rasgo.$(rasgo)", "")],
                             color="gray")),
    "$tellraw @s " + snbt(T("destinos.lema.$(lema)", "", italic=True, color="dark_gray")),
])
fn("hud/info_salvaje", [
    "$tellraw @s " + snbt(["", {"text": "☘ ", "color": "#9e9e9e"},
                          T("destinos.salvaje.$(geo)", "$(nombre)", ["$(nombre)"], color="#9e9e9e", bold=True),
                          {"text": " · "}, T("destinos.salvajes_info", "Tierras salvajes: aquí no manda nadie", color="gray")]),
])
fn("hud/bossbar_crear", [
    '$bossbar add destinos:t$(id) ""',
    "$bossbar set destinos:t$(id) players @s",
    "$bossbar set destinos:t$(id) color white",
    "$bossbar set destinos:t$(id) style progress",
    "$bossbar set destinos:t$(id) value 100",
    "$bossbar set destinos:t$(id) visible true",
])
fn("hud/bossbar_ocultar", ["$bossbar set destinos:t$(id) visible false"])
fn("hud/bossbar_noble", [
    "$bossbar set destinos:t$(id) name " + snbt(T("destinos.bossbar", "%s · %s",
        [T("destinos.tipo.$(tipo)", "$(nombre)", ["$(nombre)"]), T("destinos.reino", "Reino de %s", ["$(rnombre)"])],
        color="$(color)")),
])
fn("hud/bossbar_salvaje", [
    "$bossbar set destinos:t$(id) name " + snbt(T("destinos.bossbar", "%s · %s",
        [T("destinos.salvaje.$(geo)", "$(nombre)", ["$(nombre)"]), T("destinos.salvajes", "Tierras salvajes")],
        color="#9e9e9e")),
])


# ---------------------------------------------------------------- mar
fn("mar/entrar", [
    "# Macro: dx dz id · mar abierto sobre la celda de ducado dx,dz",
    '$execute unless data storage destinos:mundo mares."m$(dx)_$(dz)" run function destinos:mar/generar with storage destinos:tmp a',
    '$data modify storage destinos:tmp t set from storage destinos:mundo mares."m$(dx)_$(dz)"',
    "$data modify storage destinos:tmp t.id set value $(id)",
    "title @s times 10 70 20",
    "function destinos:hud/mar with storage destinos:tmp t",
    "playsound minecraft:ambient.underwater.exit master @s ~ ~ ~ 0.5 0.7",
    "execute if score @s dr.hud matches 1 run function destinos:hud/bossbar_mar with storage destinos:tmp t",
])
fn("mar/generar", [
    "# Macro: dx dz",
    "$scoreboard players set #hx dr.calc $(dx)",
    "$scoreboard players set #hz dr.calc $(dz)",
    "function destinos:cultura/detectar",
    op("#hs", "=", "#salMar", cb="dr.const"), "function destinos:nombres/generar",
    "data modify storage destinos:tmp nuevo set value {}",
    "data modify storage destinos:tmp nuevo.nombre set from storage destinos:tmp gen.nombre",
    "data modify storage destinos:tmp nuevo.cultura set from storage destinos:tmp gen.cultura",
    '$data modify storage destinos:mundo mares."m$(dx)_$(dz)" set from storage destinos:tmp nuevo',
])
fn("hud/mar", [
    "title @s subtitle " + snbt(T("destinos.mar_abierto", "Mar abierto", color="#8fc6e8")),
    "$title @s title " + snbt(T("destinos.mar", "Mar de %s", ["$(nombre)"], color="#4fa3d9")),
])
fn("hud/bossbar_mar", [
    "$bossbar set destinos:t$(id) name " + snbt(T("destinos.bossbar", "%s · %s",
        [T("destinos.mar", "Mar de %s", ["$(nombre)"]), T("destinos.mar_abierto", "Mar abierto")], color="#4fa3d9")),
])
fn("hud/info_mar", [
    "$tellraw @s " + snbt(["", {"text": "≈ ", "color": "#4fa3d9"}, T("destinos.mar", "Mar de %s", ["$(nombre)"], color="#4fa3d9", bold=True),
                          {"text": " · "}, T("destinos.mar_abierto", "Mar abierto", color="gray")]),
])

# ---------------------------------------------------------------- libro-mapa
N_MAPA, PASO_MAPA = 11, 800
MEDIO = N_MAPA // 2
fn("jugador/mapa", [
    "scoreboard players set @s destinos.mapa 0",
    "execute if entity @a[tag=destinos.mapa] run return run tellraw @s " + snbt(T("destinos.mapa.ocupado", "Ya se está dibujando un mapa, espera un momento.", color="gray")),
    "tag @s add destinos.mapa",
    "execute store result score #mx0 dr.calc run data get entity @s Pos[0]",
    "execute store result score #mz0 dr.calc run data get entity @s Pos[2]",
    f"scoreboard players remove #mx0 dr.calc {MEDIO * PASO_MAPA}",
    f"scoreboard players remove #mz0 dr.calc {MEDIO * PASO_MAPA}",
    "scoreboard players set #mj dr.calc 0",
    'data modify storage destinos:tmp mapa set value {pagina:[{text:""}]}',
    "data modify storage destinos:tmp mapa.pagina append value " + snbt(T("destinos.mapa.titulo", "Mapa político · N ↑", bold=True)),
    'data modify storage destinos:tmp mapa.pagina append value {text:"\\n\\n"}',
    "tellraw @s " + snbt(T("destinos.mapa.dibujando", "El cartógrafo dibuja el mapa…", color="gray", italic=True)),
    "schedule function destinos:mapa/fila 1t replace",
])
fn("mapa/fila", [
    "# Una fila por tick para no pasar del límite de comandos",
    "execute unless entity @a[tag=destinos.mapa] run return fail",
    "execute as @a[tag=destinos.mapa,limit=1] run function destinos:mapa/fila_j",
])
fila = ["scoreboard players set #mpaso dr.calc %d" % PASO_MAPA]
for i in range(N_MAPA):
    fila += [f"scoreboard players set #mi dr.calc {i}", "function destinos:mapa/celda"]
fila += [
    'data modify storage destinos:tmp mapa.pagina append value {text:"\\n"}',
    "scoreboard players add #mj dr.calc 1",
    f"execute if score #mj dr.calc matches ..{N_MAPA - 1} run return run schedule function destinos:mapa/fila 1t replace",
    "function destinos:mapa/entregar",
]
fn("mapa/fila_j", fila)
fn("mapa/celda", [
    op("#vx", "=", "#mi"), op("#vx", "*=", "#mpaso"), op("#vx", "+=", "#mx0"),
    op("#vz", "=", "#mj"), op("#vz", "*=", "#mpaso"), op("#vz", "+=", "#mz0"),
    "scoreboard players set #tlisto dr.calc 1",
    "function destinos:geo/territorio",
    'data modify storage destinos:tmp celda set value {text:"█",color:"#555555"}',
    op("#hx", "=", "#tkx"), op("#hz", "=", "#tkz"),
    "execute if score #tlisto dr.calc matches 1 if score #tw dr.calc matches 0 run function destinos:mapa/color_reino",
    "# con Destinos Core: zona que el mod aún está calculando = sin cartografiar",
    'execute if score #tlisto dr.calc matches 0 run data modify storage destinos:tmp celda set value {text:"░",color:"#888888"}',
    f'execute if score #mi dr.calc matches {MEDIO} if score #mj dr.calc matches {MEDIO} run data modify storage destinos:tmp celda.color set value "white"',
    "data modify storage destinos:tmp mapa.pagina append from storage destinos:tmp celda",
])
fn("mapa/color_reino", [
    "# mismo color que tendrá el reino (sale solo del hash, no hace falta haberlo visitado)",
    *elegir("colores", 10, "Color", "celda.color"),
])
fn("mapa/entregar", [
    "data modify storage destinos:tmp libro set value []",
    "data modify storage destinos:tmp libro append from storage destinos:tmp mapa.pagina",
    "data modify storage destinos:tmp libro append value " + snbt([{"text": ""},
        T("destinos.mapa.leyenda", "Cada cuadro ≈ %s bloques.\n\n█ blanco: tú\n█ gris: tierras salvajes\n█ color: un reino\n░ sin cartografiar aún: pide el mapa otra vez en un rato\n\nLos mares y ríos no aparecen.", [str(PASO_MAPA)])]),
    "data modify storage destinos:tmp mapa.libro set from storage destinos:tmp libro",
    "function destinos:mapa/dar with storage destinos:tmp mapa",
    "tag @s remove destinos.mapa",
])
fn("mapa/dar", [
    '$give @s written_book[written_book_content={title:"Mapa político",author:"Destinos",pages:$(libro)}]',
])

# ---------------------------------------------------------------- debug
fn("debug/donde", [
    "# Uso (OP): /execute as <jugador> at @s run function destinos:debug/donde",
    "execute store result score #vx dr.calc run data get entity @s Pos[0]",
    "execute store result score #vz dr.calc run data get entity @s Pos[2]",
    "function destinos:geo/territorio",
    'tellraw @s ["",{text:"[Destinos] ducado d"},{score:{name:"#tdx",objective:"dr.calc"}},{text:"_"},{score:{name:"#tdz",objective:"dr.calc"}},'
    '{text:" · reino k"},{score:{name:"#tkx",objective:"dr.calc"}},{text:"_"},{score:{name:"#tkz",objective:"dr.calc"}},'
    '{text:" · salvaje "},{score:{name:"#tw",objective:"dr.calc"}},{text:" · sal "},{score:{name:"#sal",objective:"dr.const"}}]',
])
fn("debug/olvidar", [
    "# Borra lo generado (nombres, descubrimientos) pero mantiene la sal: se regenera igual",
    "data modify storage destinos:mundo reinos set value {}",
    "data modify storage destinos:mundo ducados set value {}",
    "scoreboard players reset * dr.init",
])

# macros: toda línea con $( debe empezar por $
for k, v in list(files.items()):
    for l in v.splitlines():
        if "$(" in l:
            assert l.startswith("$"), (k, l)

# ---------------------------------------------------------------- idiomas
LEMAS = [
    ("«La piedra no se arrodilla.»", "“Stone does not kneel.”"),
    ("«Del fuego, la forja.»", "“From fire, the forge.”"),
    ("«Pocos, pero firmes.»", "“Few, but steadfast.”"),
    ("«El camino es nuestro.»", "“The road is ours.”"),
    ("«Recordamos.»", "“We remember.”"),
    ("«Ni oro ni miedo.»", "“Neither gold nor fear.”"),
    ("«Bajo la misma estrella.»", "“Under the same star.”"),
    ("«La marea siempre vuelve.»", "“The tide always returns.”"),
    ("«Raíz profunda, rama alta.»", "“Deep root, high branch.”"),
    ("«Nuestra palabra es ley.»", "“Our word is law.”"),
    ("«Que arda el invierno.»", "“Let winter burn.”"),
    ("«Paciencia y acero.»", "“Patience and steel.”"),
]
RASGOS = [("militar", "militaristic"), ("mercantil", "mercantile"), ("devoto", "devout"),
          ("decadente", "decadent"), ("joven", "young")]
FAMAS = [("justicia", "justice"), ("corrupción", "corruption"), ("paranoia", "paranoia"),
         ("ambición", "ambition"), ("mala salud", "poor health"), ("piedad", "piety")]
GEO = [("Bosques de %s", "Woods of %s"), ("Páramo de %s", "Moors of %s"), ("Picos de %s", "Peaks of %s"),
       ("Marjal de %s", "Marshes of %s"), ("Yermo de %s", "Barrens of %s")]
TITULOS = {"ducado": (("Duque %s", "Duke %s"), ("Duquesa %s", "Duchess %s")),
           "condado": (("Conde %s", "Count %s"), ("Condesa %s", "Countess %s")),
           "marca": (("Marqués %s", "Margrave %s"), ("Marquesa %s", "Margravine %s"))}
TIPOS = {"ducado": ("Ducado de %s", "Duchy of %s"), "condado": ("Condado de %s", "County of %s"),
         "marca": ("Marca de %s", "March of %s")}
es, en = {}, {}
def L(k, a, b): es[k], en[k] = a, b
L("destinos.reino", "Reino de %s", "Kingdom of %s")
L("destinos.mar", "Mar de %s", "Sea of %s")
L("destinos.mar_abierto", "Mar abierto", "Open sea")
L("destinos.mapa.titulo", "Mapa político · N ↑", "Political map · N ↑")
L("destinos.mapa.ocupado", "Ya se está dibujando un mapa, espera un momento.", "A map is already being drawn, wait a moment.")
L("destinos.mapa.dibujando", "El cartógrafo dibuja el mapa…", "The cartographer is drawing the map…")
L("destinos.mapa.leyenda", "Cada cuadro ≈ %s bloques.\n\n█ blanco: tú\n█ gris: tierras salvajes\n█ color: un reino\n░ sin cartografiar aún: pide el mapa otra vez en un rato\n\nLos mares y ríos no aparecen.",
  "Each square ≈ %s blocks.\n\n█ white: you\n█ grey: wildlands\n█ colour: a kingdom\n░ not mapped yet: ask for the map again in a while\n\nSeas and rivers aren't shown.")
L("destinos.salvajes", "Tierras salvajes", "Wildlands")
L("destinos.salvajes_info", "Tierras salvajes: aquí no manda nadie", "Wildlands: no one rules here")
L("destinos.bossbar", "%s · %s", "%s · %s")
L("destinos.primer_reino", "Entras por primera vez en el Reino de %s, bajo el estandarte de la Casa %s.",
  "You enter the Kingdom of %s for the first time, under the banner of House %s.")
L("destinos.gobierna", "Gobierna %s · Fama: %s", "Ruled by %s · Known for: %s")
L("destinos.info_reino", "Casa %s · Cultura %s · Reino %s", "House %s · %s culture · %s realm")
for k, (a, b) in TIPOS.items(): L(f"destinos.tipo.{k}", a, b)
for k, (m, f) in TITULOS.items():
    L(f"destinos.titulo.{k}.m", *m); L(f"destinos.titulo.{k}.f", *f)
for i, (a, b) in enumerate(LEMAS): L(f"destinos.lema.{i}", a, b)
for i, (a, b) in enumerate(RASGOS): L(f"destinos.rasgo.{i}", a, b)
for i, (a, b) in enumerate(FAMAS): L(f"destinos.fama.{i}", a, b)
for i, (a, b) in enumerate(GEO): L(f"destinos.salvaje.{i}", a, b)
for k, c in CULTURAS.items(): L(f"destinos.cultura.{k}", c["es"], c["en"])
assert len(LEMAS) == N_LEMAS and len(RASGOS) == N_RASGOS and len(FAMAS) == N_FAMAS and len(GEO) == N_GEO

# ---------------------------------------------------------------- escribir
if os.path.exists(OUT):
    shutil.rmtree(OUT)
for rel, txt in files.items():
    p = os.path.join(NS, rel); os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, "w", encoding="utf-8").write(txt)
def wjson(p, o):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, "w", encoding="utf-8").write(json.dumps(o, ensure_ascii=False, indent=2) + "\n")
for p, o in files_json: wjson(p, o)
wjson(os.path.join(DP, "data/minecraft/tags/function/load.json"), {"values": ["destinos:init/load"]})
wjson(os.path.join(DP, "pack.mcmeta"), {"pack": {"description": "Destinos: Reinos v0.1 · reinos y ducados RNG",
                                                 "min_format": 88, "max_format": 99}})
wjson(os.path.join(RP, "pack.mcmeta"), {"pack": {"description": "Destinos: Reinos v0.1 · idiomas ES/EN",
                                                 "min_format": 69, "max_format": 99}})
for loc in ("es_es", "es_mx", "es_ar", "es_cl", "es_ec", "es_uy", "es_ve"):
    wjson(os.path.join(RP, f"assets/destinos/lang/{loc}.json"), es)
wjson(os.path.join(RP, "assets/destinos/lang/en_us.json"), en)
wjson(os.path.join(RP, "assets/destinos/lang/en_gb.json"), en)
for d in (DP, RP):
    with zipfile.ZipFile(d + ".zip", "w", zipfile.ZIP_DEFLATED) as z:
        for root, _, fs in os.walk(d):
            for f in fs:
                full = os.path.join(root, f); z.write(full, os.path.relpath(full, d))
print("funciones:", len(files), "· claves de idioma:", len(es))

# Variante para Destinos Core: el mod carga data/ y assets/ desde su jar, y geo/territorio pasa a
# preguntar al mod (fronteras por terreno) en vez de usar el Voronoi geométrico.
if MOD:
    if os.path.exists(MOD):
        shutil.rmtree(MOD)
    shutil.copytree(os.path.join(DP, "data"), os.path.join(MOD, "data"))
    shutil.copytree(os.path.join(RP, "assets"), os.path.join(MOD, "assets"))
    open(os.path.join(MOD, "data/destinos/function/geo/territorio.mcfunction"), "w", encoding="utf-8").write(
        "# Destinos Core: fronteras que siguen montañas, ríos y costas (mismas entradas y salidas)\n"
        "destinoscore territorio\n")
    print("variante del mod en", MOD)
