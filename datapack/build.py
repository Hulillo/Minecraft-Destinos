"""Genera el datapack y el resource pack de idiomas de Destinos: Reinos v0.3."""
import json, os, shutil, sys, zipfile
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from culturas import CULTURAS, ORDEN, BIOMAS
import constantes as K

# Uso: python build.py <salida> [--mod <carpeta generated del mod>]
OUT = sys.argv[1]
MOD = sys.argv[sys.argv.index("--mod") + 1] if "--mod" in sys.argv else None
DP = os.path.join(OUT, "destinos_reinos")
RP = os.path.join(OUT, "destinos_reinos_idiomas")
NS = os.path.join(DP, "data", "destinos")

# Constantes del mapa (datapack/constantes.py, compartidas con sim.py)
C = dict(S_D=K.S_DUCADO, S_K=K.S_REINO, WARP=K.WARP, D_FRONTERA=K.D_FRONTERA, P_FRONTERA=K.P_FRONTERA,
         P_SALVAJE=K.P_SALVAJE, K_JZ=K.K_JZ, K_REINO=K.K_REINO, K_SALV=K.K_SALV, K_WARP=K.K_WARP)
SALES = ["NR", "Casa", "Lema", "Rasgo", "Color", "ND", "Tipo", "Gen", "Sen", "Fama", "Geo", "Mar"]  # sal + 1..12
assert SALES.index("Color") + 1 == K.SAL_COLOR, "Destinos Core calcula el color con K.SAL_COLOR"
assert SALES.index("Rasgo") + 1 == K.SAL_RASGO, "Destinos Core calcula el rasgo con K.SAL_RASGO"
N_LEMAS, N_RASGOS, N_FAMAS, N_GEO = 12, K.N_RASGOS, 6, 5
COLORES = K.COLORES

files = {}
def fn(path, lines):
    """Escribe una función. Las líneas con $(…) se marcan solas como macro."""
    lines = [("$" + l if "$(" in l and not l.startswith("$") else l) for l in lines if l is not None]
    files[f"function/{path}.mcfunction"] = "\n".join(lines) + "\n"
def js(path, obj, base=NS):
    files_json.append((os.path.join(base, path), obj))
files_json = []
def op(a, o, b, ca="dr.calc", cb="dr.calc"):
    return f"scoreboard players operation {a} {ca} {o} {b} {cb}"
def tirada(sal, mod):
    """#h = hash(#hx, #hz, #sal<sal>) % mod"""
    return [op("#hs", "=", f"#sal{sal}", cb="dr.const"), "function destinos:geo/hash", op("#h", "%=", f"#{mod}", cb="dr.const")]
def a_storage(dst, pares):
    """Copia marcadores a destinos:tmp <dst>.<campo>; pares = [(campo, "holder objetivo"), …]"""
    pre = f"{dst}." if dst else ""
    return [f"execute store result storage destinos:tmp {pre}{k} int 1 run scoreboard players get {src}" for k, src in pares]
def celda_macro(x, z):
    """#hx #hz = $(x) $(z) (función macro)"""
    return [f"$scoreboard players set #hx dr.calc $({x})", f"$scoreboard players set #hz dr.calc $({z})"]
def nombre_nuevo(sal):
    """Genera un nombre con la cultura de destinos:tmp gen y lo guarda en destinos:tmp nuevo"""
    return [op("#hs", "=", f"#sal{sal}", cb="dr.const"), "function destinos:nombres/generar",
            "data modify storage destinos:tmp nuevo.nombre set from storage destinos:tmp gen.nombre",
            "data modify storage destinos:tmp nuevo.cultura set from storage destinos:tmp gen.cultura"]
TRIGGERS = ("donde", "hud", "mapa")
TIEMPOS_TITULO = "title @s times 10 70 20"

# ---------------------------------------------------------------- init
fn("init/load", [
    "# Destinos: Reinos v0.3 · carga",
    *[f"scoreboard objectives add {o} dummy" for o in
      ["dr.calc", "dr.const", "dr.dx", "dr.dz", "dr.kx", "dr.kz", "dr.w", "dr.init", "dr.id", "dr.hud", "dr.mar"]],
    *[f"scoreboard objectives add destinos.{t} trigger" for t in TRIGGERS],
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
    "schedule function destinos:humvil/ajusta 40t replace",
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
    *[f"scoreboard players enable @a destinos.{t}" for t in TRIGGERS],
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
    "# si el ducado cambia de dueño (política viva) con el jugador dentro, también se avisa",
    "execute if score #cr dr.calc matches 1 run scoreboard players set #cd dr.calc 1",
    "execute if score #cd dr.calc matches 1 run function destinos:jugador/cambio",
])
def guarda_args(dst="a"):
    return a_storage(dst, [("dx", "#tdx dr.calc"), ("dz", "#tdz dr.calc"), ("kx", "#tkx dr.calc"),
                           ("kz", "#tkz dr.calc"), ("id", "@s dr.id")])
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
    *a_storage("a", [(k, f"@s dr.{k}") for k in ("dx", "dz", "kx", "kz")]),
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
    *a_storage("a", [("id", "@s dr.id")]),
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
    TIEMPOS_TITULO,
    "execute if score #tw dr.calc matches 1 run function destinos:hud/salvaje with storage destinos:tmp t",
    "execute if score #tw dr.calc matches 0 run function destinos:hud/noble with storage destinos:tmp t",
    "execute if score #tw dr.calc matches 0 if data storage destinos:tmp t{conquista:1b} run function destinos:hud/noble_conquista with storage destinos:tmp t",
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
    return [*tirada(sal, mod),
            "execute store result storage destinos:tmp idx.i int 1 run scoreboard players get #h dr.calc",
            f'data modify storage destinos:tmp idx.lista set value "{lista_expr}"',
            "function destinos:util/elegir with storage destinos:tmp idx",
            f"data modify storage destinos:tmp {dst} set from storage destinos:tmp elegido"]
def indice(mod, sal, dst):
    return [*tirada(sal, mod), *a_storage("", [(dst, "#h dr.calc")])]
fn("util/elegir", ["$data modify storage destinos:tmp elegido set from storage destinos:tablas $(lista)[$(i)]"])
fn("reino/generar_reino", [
    "# Macro: kx kz",
    "function destinos:cultura/detectar",
    *celda_macro("kx", "kz"),
    "data modify storage destinos:tmp nuevo set value {descubierto:0b}",
    *nombre_nuevo("NR"),
    "function destinos:reino/casa with storage destinos:tmp gen",
    *indice(N_LEMAS, "Lema", "nuevo.lema"),
    *indice(N_RASGOS, "Rasgo", "nuevo.rasgo"),
    *elegir("colores", 10, "Color", "nuevo.color"),
    '$data modify storage destinos:mundo reinos."k$(kx)_$(kz)" set from storage destinos:tmp nuevo',
])
fn("reino/casa", [
    "# Macro: cultura",
    *elegir("$(cultura).casas", 12, "Casa", "nuevo.casa"),
])
fn("reino/generar_ducado", [
    "# Macro: dx dz kx kz",
    *celda_macro("dx", "dz"),
    "data modify storage destinos:tmp nuevo set value {descubierto:0b}",
    "execute if score #tw dr.calc matches 1 run function destinos:reino/ducado_salvaje",
    "execute if score #tw dr.calc matches 0 run function destinos:reino/ducado_noble with storage destinos:tmp a",
    '$data modify storage destinos:mundo ducados."d$(dx)_$(dz)" set from storage destinos:tmp nuevo',
])
fn("reino/ducado_salvaje", [
    "function destinos:cultura/detectar",
    *nombre_nuevo("ND"),
    "data modify storage destinos:tmp nuevo.salvaje set value 1b",
    *indice(N_GEO, "Geo", "nuevo.geo"),
])
fn("reino/ducado_noble", [
    "# Macro: kx kz",
    '$data modify storage destinos:tmp gen.cultura set from storage destinos:mundo reinos."k$(kx)_$(kz)".cultura',
    *nombre_nuevo("ND"),
    "data modify storage destinos:tmp nuevo.salvaje set value 0b",
    '$data modify storage destinos:tmp nuevo.reino set value "k$(kx)_$(kz)"',
    '$data modify storage destinos:tmp nuevo.rnombre set from storage destinos:mundo reinos."k$(kx)_$(kz)".nombre',
    '$data modify storage destinos:tmp nuevo.color set from storage destinos:mundo reinos."k$(kx)_$(kz)".color',
    "# tipo: 60% ducado, 20% condado, 20% marca",
    *tirada("Tipo", 10),
    'data modify storage destinos:tmp nuevo.tipo set value "ducado"',
    'execute if score #h dr.calc matches 6..7 run data modify storage destinos:tmp nuevo.tipo set value "condado"',
    'execute if score #h dr.calc matches 8..9 run data modify storage destinos:tmp nuevo.tipo set value "marca"',
    "# quién gobierna",
    *tirada("Gen", 2),
    'data modify storage destinos:tmp nuevo.g set value "m"',
    'execute if score #h dr.calc matches 1 run data modify storage destinos:tmp nuevo.g set value "f"',
    "function destinos:reino/senor with storage destinos:tmp nuevo",
    *indice(N_FAMAS, "Fama", "nuevo.fama"),
])
fn("reino/senor", [
    "# Macro: cultura g",
    *elegir("$(cultura).$(g)", 12, "Sen", "nuevo.senor"),
])

# ---------------------------------------------------------------- política viva (las llama Destinos Core)
fn("politica/asegurar_reino", [
    "# Macro: kx kz · en el centro del reino (para la cultura). Crea el registro si aún no existe",
    '$execute unless data storage destinos:mundo reinos."k$(kx)_$(kz)" run function destinos:reino/generar_reino {kx:$(kx),kz:$(kz)}',
])
fn("politica/asegurar_ducado", [
    "# Macro: dx dz kx kz w · en el centro del ducado, con su dueño de hoy (w=1: sin señor)",
    "$scoreboard players set #tw dr.calc $(w)",
    "$data modify storage destinos:tmp a set value {dx:$(dx),dz:$(dz),kx:$(kx),kz:$(kz)}",
    '$execute unless data storage destinos:mundo ducados."d$(dx)_$(dz)" run function destinos:reino/generar_ducado with storage destinos:tmp a',
])
fn("politica/cambio", [
    "# Macro: dx dz kx kz w c · el ducado tiene dueño nuevo (c=1: ganado en guerra)",
    "# sin señor, o tierra salvaje que pasa a un reino: se borra y se rehace al entrar con su nuevo estado",
    "$scoreboard players set #pw dr.calc $(w)",
    '$execute if score #pw dr.calc matches 1 run return run data remove storage destinos:mundo ducados."d$(dx)_$(dz)"',
    '$execute if data storage destinos:mundo ducados."d$(dx)_$(dz)"{salvaje:1b} run return run data remove storage destinos:mundo ducados."d$(dx)_$(dz)"',
    '$execute unless data storage destinos:mundo ducados."d$(dx)_$(dz)" run return 0',
    '$data modify storage destinos:mundo ducados."d$(dx)_$(dz)".reino set value "k$(kx)_$(kz)"',
    '$data modify storage destinos:mundo ducados."d$(dx)_$(dz)".rnombre set from storage destinos:mundo reinos."k$(kx)_$(kz)".nombre',
    '$data modify storage destinos:mundo ducados."d$(dx)_$(dz)".color set from storage destinos:mundo reinos."k$(kx)_$(kz)".color',
    '$data modify storage destinos:mundo ducados."d$(dx)_$(dz)".conquista set value $(c)b',
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

# textos que se repiten en títulos, chat y bossbar
NOMBRE_DUCADO = T("destinos.tipo.$(tipo)", "$(nombre)", ["$(nombre)"])
NOMBRE_REINO = T("destinos.reino", "Reino de %s", ["$(rnombre)"])
NOMBRE_SALVAJE = T("destinos.salvaje.$(geo)", "$(nombre)", ["$(nombre)"])
NOMBRE_MAR = T("destinos.mar", "Mar de %s", ["$(nombre)"])
SALVAJES = T("destinos.salvajes", "Tierras salvajes")
MAR_ABIERTO = T("destinos.mar_abierto", "Mar abierto")
GOBIERNA = T("destinos.gobierna", "Gobierna %s · Fama: %s",
             [T("destinos.titulo.$(tipo).$(g)", "$(senor)", ["$(senor)"]), T("destinos.fama.$(fama)", "")], color="gray")
def con(texto, **estilo):
    """Copia de un componente con estilo añadido (color, bold…)"""
    return {**texto, **estilo}
def bossbar(nombre, lugar, color):
    """Bossbar del jugador: "<nombre> · <lugar>" """
    return ["$bossbar set destinos:t$(id) name " + snbt(T("destinos.bossbar", "%s · %s", [nombre, lugar], color=color))]

GOBIERNA_CORTO = T("destinos.gobierna_corto", "Gobierna %s", [T("destinos.titulo.$(tipo).$(g)", "$(senor)", ["$(senor)"])],
                   color="gray", italic=True)
fn("hud/noble", [
    "# Macro: registro de ducado noble",
    "$title @s subtitle " + snbt(con(NOMBRE_REINO, color="$(color)")),
    "$title @s title " + snbt(con(NOMBRE_DUCADO, color="$(color)")),
    "# quién manda hoy (con Destinos Core, el duque vivo o el rey en la capital), debajo del título",
    "$title @s actionbar " + snbt(GOBIERNA_CORTO),
])
fn("hud/noble_conquista", [
    "# Macro: registro de ducado noble ganado en una guerra (política viva)",
    "$title @s subtitle " + snbt(con(T("destinos.reino_conquista", "Ahora del Reino de %s", ["$(rnombre)"]), color="$(color)")),
    "$title @s title " + snbt(con(NOMBRE_DUCADO, color="$(color)")),
    "$title @s actionbar " + snbt(GOBIERNA_CORTO),
])
fn("hud/salvaje", [
    "# Macro: registro de tierra salvaje",
    "title @s subtitle " + snbt(con(SALVAJES, color="gray")),
    "$title @s title " + snbt(con(NOMBRE_SALVAJE, color="#9e9e9e")),
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
    "$tellraw @s " + snbt(GOBIERNA),
])
fn("hud/info_noble", [
    "$tellraw @s " + snbt(["", {"text": "⚑ ", "color": "$(color)"},
                          con(NOMBRE_DUCADO, color="$(color)", bold=True),
                          {"text": " · "}, con(NOMBRE_REINO, color="$(color)")]),
    "$tellraw @s " + snbt(GOBIERNA),
])
fn("hud/info_reino", [
    "$tellraw @s " + snbt(T("destinos.info_reino", "Casa %s · Cultura %s · Reino %s",
                             ["$(casa)", T("destinos.cultura.$(cultura)", "$(cultura)"), T("destinos.rasgo.$(rasgo)", "")],
                             color="gray")),
    "$tellraw @s " + snbt(T("destinos.lema.$(lema)", "", italic=True, color="dark_gray")),
])
fn("hud/info_salvaje", [
    "$tellraw @s " + snbt(["", {"text": "☘ ", "color": "#9e9e9e"},
                          con(NOMBRE_SALVAJE, color="#9e9e9e", bold=True),
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
fn("hud/bossbar_noble", bossbar(NOMBRE_DUCADO, NOMBRE_REINO, "$(color)"))
fn("hud/bossbar_salvaje", bossbar(NOMBRE_SALVAJE, SALVAJES, "#9e9e9e"))


# ---------------------------------------------------------------- mar
fn("mar/entrar", [
    "# Macro: dx dz id · mar abierto sobre la celda de ducado dx,dz",
    '$execute unless data storage destinos:mundo mares."m$(dx)_$(dz)" run function destinos:mar/generar with storage destinos:tmp a',
    '$data modify storage destinos:tmp t set from storage destinos:mundo mares."m$(dx)_$(dz)"',
    "$data modify storage destinos:tmp t.id set value $(id)",
    TIEMPOS_TITULO,
    "function destinos:hud/mar with storage destinos:tmp t",
    "playsound minecraft:ambient.underwater.exit master @s ~ ~ ~ 0.5 0.7",
    "execute if score @s dr.hud matches 1 run function destinos:hud/bossbar_mar with storage destinos:tmp t",
])
fn("mar/generar", [
    "# Macro: dx dz",
    *celda_macro("dx", "dz"),
    "function destinos:cultura/detectar",
    "data modify storage destinos:tmp nuevo set value {}",
    *nombre_nuevo("Mar"),
    '$data modify storage destinos:mundo mares."m$(dx)_$(dz)" set from storage destinos:tmp nuevo',
])
fn("hud/mar", [
    "title @s subtitle " + snbt(con(MAR_ABIERTO, color="#8fc6e8")),
    "$title @s title " + snbt(con(NOMBRE_MAR, color="#4fa3d9")),
])
fn("hud/bossbar_mar", bossbar(NOMBRE_MAR, MAR_ABIERTO, "#4fa3d9"))
fn("hud/info_mar", [
    "$tellraw @s " + snbt(["", {"text": "≈ ", "color": "#4fa3d9"}, con(NOMBRE_MAR, color="#4fa3d9", bold=True),
                          {"text": " · "}, con(MAR_ABIERTO, color="gray")]),
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
L("key.destinos_core.mapa_politico", "Mapa político", "Political map")
L("key.category.destinos_core.destinos", "Destinos", "Destinos")
L("destinos.mapa_politico.on", "Mapa político: activado", "Political map: on")
L("destinos.mapa_politico.off", "Mapa político: desactivado", "Political map: off")
L("destinos.reino_conquista", "Ahora del Reino de %s", "Now of the Kingdom of %s")
# política viva: los textos los escribe Destinos Core (args: año, reino A, reino B, ducado)
POLITICA = {
    "guerra": ("⚔ Año %1$s · El %2$s declara la guerra al %3$s.", "⚔ Year %1$s · The %2$s declares war on the %3$s."),
    "paz": ("☮ Año %1$s · El %2$s y el %3$s firman la paz.", "☮ Year %1$s · The %2$s and the %3$s make peace."),
    "conquista": ("⚑ Año %1$s · %4$s pasa al %2$s tras la guerra con el %3$s.",
                  "⚑ Year %1$s · %4$s falls to the %2$s in the war against the %3$s."),
    "cae": ("☠ Año %1$s · El %2$s ha caído: el %3$s se queda con sus últimas tierras.",
            "☠ Year %1$s · The %2$s has fallen: the %3$s takes its last lands."),
    "alianza": ("✦ Año %1$s · El %2$s y el %3$s sellan una alianza.", "✦ Year %1$s · The %2$s and the %3$s seal an alliance."),
    "ruptura": ("✧ Año %1$s · Se rompe la alianza entre el %2$s y el %3$s.",
                "✧ Year %1$s · The alliance between the %2$s and the %3$s is broken."),
    "vasallaje": ("♛ Año %1$s · El %2$s jura vasallaje al %3$s.", "♛ Year %1$s · The %2$s swears fealty to the %3$s."),
    "independencia": ("⚐ Año %1$s · El %2$s rompe el vasallaje y se declara libre del %3$s.",
                      "⚐ Year %1$s · The %2$s breaks its fealty and declares itself free of the %3$s."),
    "anexion": ("♛ Año %1$s · El %2$s se anexiona a su vasallo, el %3$s.", "♛ Year %1$s · The %2$s annexes its vassal, the %3$s."),
    "disolucion": ("☠ Año %1$s · El %2$s se hunde en el caos: sus tierras quedan sin señor.",
                   "☠ Year %1$s · The %2$s collapses into chaos: its lands are left without a lord."),
    "expansion": ("⚜ Año %1$s · El %2$s reclama %4$s, antes tierra salvaje.", "⚜ Year %1$s · The %2$s claims %4$s, once wildland."),
    "capital": ("♛ Año %1$s · ¡El %2$s toma %4$s, la capital del %3$s!", "♛ Year %1$s · The %2$s seizes %4$s, capital of the %3$s!"),
    "union": ("❖ Año %1$s · El %2$s y el %3$s se unen: nace la %4$s.",
              "❖ Year %1$s · The %2$s and the %3$s unite: the %4$s is born."),
    "conquista_ayuda": ("⚑ Año %1$s · Con la ayuda de %5$s, el %2$s toma %4$s al %3$s.",
                        "⚑ Year %1$s · With the help of %5$s, the %2$s takes %4$s from the %3$s."),
    "capital_ayuda": ("♛ Año %1$s · ¡Con la ayuda de %5$s, el %2$s toma %4$s, la capital del %3$s!",
                      "♛ Year %1$s · With the help of %5$s, the %2$s seizes %4$s, capital of the %3$s!"),
    "paz_ayuda": ("☮ Año %1$s · Gracias a la embajada de %5$s, el %2$s y el %3$s firman la paz.",
                  "☮ Year %1$s · Thanks to the embassy of %5$s, the %2$s and the %3$s make peace."),
    "alianza_ayuda": ("✦ Año %1$s · Gracias a la embajada de %5$s, el %2$s y el %3$s sellan una alianza.",
                      "✦ Year %1$s · Thanks to the embassy of %5$s, the %2$s and the %3$s seal an alliance."),
    "fin_union": ("✧ Año %1$s · Se rompe la %4$s: el %2$s y el %3$s vuelven a ir cada uno por su lado.",
                  "✧ Year %1$s · The %4$s breaks apart: the %2$s and the %3$s go their separate ways again."),
}
for k, (a, b) in POLITICA.items(): L(f"destinos.politica.{k}", a, b)
# v0.7 Gente: sucesos con personas (%5$s y %6$s: la gente con su título; %7$s: la causa de la muerte; %8$s: quién)
GENTE_SUCESOS = {
    "muerte": ("✝ Año %1$s · Muere %5$s del %2$s (%7$s).", "✝ Year %1$s · %5$s of the %2$s has died (%7$s)."),
    "sucesion": ("♛ Año %1$s · Sube al trono del %2$s %5$s.", "♛ Year %1$s · %5$s ascends the throne of the %2$s."),
    "crisis": ("⚠ Año %1$s · Sin heredero, el %2$s se tambalea: %5$s se hace con la corona.",
               "⚠ Year %1$s · With no heir, the %2$s totters: %5$s seizes the crown."),
    "boda": ("💍 Año %1$s · Boda de %5$s del %2$s y %6$s del %3$s: la alianza queda sellada.",
             "💍 Year %1$s · %5$s of the %2$s weds %6$s of the %3$s: the alliance is sealed."),
    "boda_noble": ("💍 Año %1$s · Boda en el %2$s: %5$s se casa con %6$s.", "💍 Year %1$s · Wedding in the %2$s: %5$s marries %6$s."),
    "golpe": ("⚔ Año %1$s · Golpe en el %2$s: %5$s derroca a %6$s.", "⚔ Year %1$s · Coup in the %2$s: %5$s overthrows %6$s."),
    "caido": ("✝ Año %1$s · Cae en la guerra %5$s, del %2$s.", "✝ Year %1$s · %5$s of the %2$s falls in battle."),
    "caido_ducado": ("✝ Año %1$s · Cae %5$s, del %2$s, luchando por %4$s.", "✝ Year %1$s · %5$s of the %2$s falls fighting for %4$s."),
    "herencia": ("❖ Año %1$s · Una sola corona para el %2$s y el %3$s: la hereda %5$s.",
                 "❖ Year %1$s · One crown for the %2$s and the %3$s: %5$s inherits it."),
    "renombre": ("★ Año %1$s · Nace una leyenda en el %2$s: %5$s.", "★ Year %1$s · A legend is born in the %2$s: %5$s."),
    "nacimiento": ("✦ Año %1$s · Nace %5$s en la familia real del %2$s.", "✦ Year %1$s · %5$s is born to the royal family of the %2$s."),
    "asesinato": ("☠ Año %1$s · Asesinato en el %2$s: %8$s mata a %5$s.", "☠ Year %1$s · Murder in the %2$s: %8$s kills %5$s."),
}
for k, (a, b) in GENTE_SUCESOS.items(): L(f"destinos.politica.{k}", a, b)
GENTE_TITULOS = {
    "rey": (("el rey %s", "la reina %s"), ("King %s", "Queen %s")),
    "consorte": (("el rey consorte %s", "la reina %s"), ("Prince Consort %s", "Queen %s")),
    "familia": (("el príncipe %s", "la princesa %s"), ("Prince %s", "Princess %s")),
    "duque": (("el duque %s", "la duquesa %s"), ("Duke %s", "Duchess %s")),
    "general": (("el general %s", "la generala %s"), ("General %s", "General %s")),
    "consejero": (("el consejero %s", "la consejera %s"), ("Councillor %s", "Councillor %s")),
    "sin_cargo": (("%s", "%s"), ("%s", "%s")),
}
for k, ((em, ef), (nm, nf)) in GENTE_TITULOS.items():
    L(f"destinos.gente.titulo.{k}.m", em, nm); L(f"destinos.gente.titulo.{k}.f", ef, nf)
GENTE_RASGOS = {
    "valiente": ("valiente", "valiente", "brave"), "cobarde": ("cobarde", "cobarde", "craven"), "cruel": ("cruel", "cruel", "cruel"),
    "justo": ("justo", "justa", "just"), "ambicioso": ("ambicioso", "ambiciosa", "ambitious"), "humilde": ("humilde", "humilde", "humble"),
    "codicioso": ("codicioso", "codiciosa", "greedy"), "generoso": ("generoso", "generosa", "generous"),
    "piadoso": ("piadoso", "piadosa", "pious"), "astuto": ("astuto", "astuta", "cunning"),
}
for k, (m, f, en_) in GENTE_RASGOS.items():
    L(f"destinos.gente.rasgo.{k}.m", m, en_); L(f"destinos.gente.rasgo.{k}.f", f, en_)
GENTE_MOTES = {
    "conquistador": (("el Conquistador", "la Conquistadora"), "the Conqueror"), "terrible": (("el Terrible", "la Terrible"), "the Terrible"),
    "justo": (("el Justo", "la Justa"), "the Just"), "cruel": (("el Cruel", "la Cruel"), "the Cruel"),
    "piadoso": (("el Piadoso", "la Piadosa"), "the Pious"), "generoso": (("el Generoso", "la Generosa"), "the Generous"),
    "astuto": (("el Astuto", "la Astuta"), "the Cunning"), "bravo": (("el Bravo", "la Brava"), "the Bold"),
    "grande": (("el Grande", "la Grande"), "the Great"),
}
for k, ((m, f), en_) in GENTE_MOTES.items():
    L(f"destinos.gente.mote.{k}.m", m, en_); L(f"destinos.gente.mote.{k}.f", f, en_)
GENTE_CAUSAS = {"vejez": ("de vejez", "of old age"), "enfermedad": ("de enfermedad", "of illness"), "guerra": ("en la guerra", "in battle"),
                "golpe": ("en un golpe", "in a coup"), "asesinato": ("asesinado", "murdered"), "parto": ("de parto", "in childbirth")}
for k, (a, b) in GENTE_CAUSAS.items(): L(f"destinos.gente.causa.{k}", a, b)
GENTE = {
    "nombre_mote": ("%s %s", "%s %s"),
    "pista": ("Casa %s · %s años · %s y %s\nGuerra %s · Diplomacia %s · Intriga %s",
              "House %s · aged %s · %s and %s\nWarfare %s · Diplomacy %s · Intrigue %s"),
    "pista_muerto": ("Casa %s · murió con %s años, %s", "House %s · died aged %s, %s"),
    "rey": ("♚ %s, %s años · %s y %s", "♚ %s, aged %s · %s and %s"),
    "rey_menor": ("♚ %s, %s años (menor: gobierna %s)", "♚ %s, aged %s (a minor: %s rules)"),
    "consorte": ("Consorte: %s, %s años", "Consort: %s, aged %s"),
    "heredero": ("Heredero: %s (%s), %s años", "Heir: %s (%s), aged %s"),
    "sin_heredero": ("Sin heredero: si muere, habrá crisis", "No heir: a crisis looms if they die"),
    "ley": ("Ley: %s", "Law: %s"),
    "ley.varon": ("hereda el hijo mayor", "the eldest son inherits"),
    "ley.absoluta": ("hereda el primogénito", "the firstborn inherits"),
    "ley.valia": ("hereda el más capaz de la sangre", "the ablest of the blood inherits"),
    "ley.electiva": ("los nobles eligen al rey", "the nobles elect the ruler"),
    "corte": ("General: %s · Consejo: %s", "General: %s · Council: %s"),
    "linaje": ("Linaje", "Lineage"),
    "reinado": ("%s · años %s-%s, %s", "%s · years %s-%s, %s"),
    "reinado_hoy": ("%s · desde el año %s", "%s · since year %s"),
    "fin.golpe": ("derrocado", "overthrown"),
    "fin.otro": ("dejó el trono", "left the throne"),
    "ver_arbol": ("⚘ Ver el árbol familiar", "⚘ See the family tree"),
    "arbol.cargando": ("Buscando el árbol…", "Looking up the tree…"),
    "arbol.titulo": ("Árbol familiar · casa %s", "Family tree · House %s"),
    "arbol.ayuda": ("Clic en alguien: su árbol · ♚ reina hoy · ♛ reinó · Esc: cerrar",
                    "Click someone: their tree · ♚ rules today · ♛ once ruled · Esc: close"),
    "arbol.vive": ("n. %s", "b. %s"),
    "arbol.vida": ("%s–%s", "%s–%s"),
}
for k, (a, b) in GENTE.items(): L(f"destinos.gente.{k}", a, b)
GENTE_PARENTESCO = {"hijo": (("hijo", "hija"), ("son", "daughter")), "nieto": (("nieto", "nieta"), ("grandson", "granddaughter")),
                    "hermano": (("hermano", "hermana"), ("brother", "sister")), "sobrino": (("sobrino", "sobrina"), ("nephew", "niece")),
                    "noble": (("noble elegido", "noble elegida"), ("elected noble", "elected noble"))}
for k, ((m, f), (em, ef)) in GENTE_PARENTESCO.items():
    L(f"destinos.gente.parentesco.{k}.m", m, em); L(f"destinos.gente.parentesco.{k}.f", f, ef)
L("destinos.politica.coordenadas", "Centro: %s, %s", "Centre: %s, %s")
L("destinos.politica.ver_mapa", "Clic: verlo en el mapa", "Click: show it on the map")
L("destinos.politica.donde", " (a %s bloques al %s)", " (%s blocks to the %s)")
L("destinos.politica.aqui", " (aquí mismo)", " (right here)")
for k, (a_, b_) in {"n": ("N", "N"), "ne": ("NE", "NE"), "e": ("E", "E"), "se": ("SE", "SE"), "s": ("S", "S"),
                    "so": ("SO", "SW"), "o": ("O", "W"), "no": ("NO", "NW")}.items():
    L(f"destinos.rumbo.{k}", a_, b_)
L("destinos.politica.resumen", "Han pasado %s turnos: %s sucesos · %s ducados y %s reinos conocidos",
  "%s turns went by: %s events · %s duchies and %s kingdoms known")
L("destinos.politica.pocos", "Aún se conocen pocos reinos alrededor, así que no tienen con quién pelearse ni aliarse. El mundo conocido crece solo con el tiempo y al explorar.",
  "Few kingdoms are known around here yet, so they have no one to fight or ally with. The known world grows over time and as you explore.")
L("destinos.politica.lejano", "reino lejano", "distant kingdom")
L("destinos.cartel.bienvenida", "Bienvenido a", "Welcome to")
L("destinos.cartel.capital", "capital del", "capital of the")
L("destinos.cartel.libre", "tierra libre", "free land")
L("destinos.politica.mas", "… y %s sucesos más (/destinoscore cronica)", "… and %s more events (/destinoscore cronica)")
L("destinos.politica.estado", "Año %s · turno %s · siguiente turno en %s min · %s ducados y %s reinos conocidos",
  "Year %s · turn %s · next turn in %s min · %s duchies and %s kingdoms known")
L("destinos.politica.salvaje", "Aquí no manda nadie.", "No one rules here.")
L("destinos.politica.reino", "%s · estabilidad %s/100", "%s · stability %s/100")
L("destinos.politica.vasallo", "Vasallo del %s", "Vassal of the %s")
L("destinos.politica.guerras", "⚔ En guerra con: %s", "⚔ At war with: %s")
L("destinos.politica.aliados", "✦ Aliados: %s", "✦ Allies: %s")
L("destinos.politica.relacion", "%s (%s)", "%s (%s)")
L("destinos.politica.relaciones", "Vecinos (relación de -100 a 100): %s", "Neighbours (relations from -100 to 100): %s")
L("destinos.politica.cronica", "📜 Crónica del mundo", "📜 Chronicle of the world")
for i, (a, b) in enumerate([("Corona de %s y %s", "Crown of %s and %s"), ("Unión de %s y %s", "Union of %s and %s"),
                            ("Liga de %s y %s", "League of %s and %s"), ("Mancomunidad de %s y %s", "Commonwealth of %s and %s")]):
    L(f"destinos.union.{i}", a, b)
L("destinos.politica.poder", "Poder %s · unos %s habitantes · %s ducados · comida %s, mineral %s, madera %s",
  "Power %s · about %s inhabitants · %s duchies · food %s, ore %s, timber %s")
L("destinos.politica.capital_info", "♛ Capital: %s", "♛ Capital: %s")
L("destinos.politica.capital_perdida", "♛ Capital: %s, en manos del %s", "♛ Capital: %s, held by the %s")
L("destinos.politica.capital_montana", "%s (en las montañas)", "%s (in the mountains)")
L("destinos.politica.en_union", "❖ Forma parte de la %s", "❖ Part of the %s")
L("destinos.politica.ranking", "♛ Reinos más poderosos que se conocen", "♛ Most powerful known kingdoms")
L("destinos.politica.ranking_linea", "%s. %s · poder %s · unos %s habitantes · %s ducados",
  "%s. %s · power %s · about %s inhabitants · %s duchies")
L("destinos.politica.cronica_vacia", "La crónica está vacía: aún no ha pasado nada.", "The chronicle is empty: nothing has happened yet.")
L("destinos.politica.permiso", "Solo en modo creativo o desde la consola.", "Only in creative mode or from the console.")
L("destinos.politica.forzado", "Pasan %s turnos de política…", "%s political turns go by…")
L("destinos.politica.ritmo", "Ahora pasa un turno de política cada %s días de juego.", "A political turn now happens every %s in-game days.")
for k, (a, b) in TIPOS.items(): L(f"destinos.tipo.{k}", a, b)
# encargos (v0.5): los textos los escribe Destinos Core
ENCARGOS = {
    "vacio": ("El tablón está vacío: los mensajeros aún no han llegado. Vuelve en un momento.",
              "The board is empty: the messengers haven't arrived yet. Come back in a moment."),
    "tablon": ("Tablón de encargos · %s", "Quest board · %s"),
    "libres": ("Encargos de gente libre: aquí no hay señor al que servir", "Jobs from free folk: there is no lord to serve here"),
    "del_reino": ("Encargos del %s", "Jobs from the %s"),
    "tu_rep": ("Tu reputación aquí: %s/100%s", "Your reputation here: %s/100%s"),
    "tu_rep_dificiles": (" · con %s te darán encargos de 4 y 5 estrellas", " · at %s you'll get 4 and 5 star jobs"),
    "lleno": ("Ya llevas %s encargos: termina o deja alguno antes de coger otro.",
              "You already carry %s jobs: finish or drop one before taking another."),
    "cogido": ("📜 Encargo aceptado: %s", "📜 Job accepted: %s"),
    "dejado": ("Dejas el encargo: %s", "You drop the job: %s"),
    "humo": ("Huele a humo de hoguera: el campamento de los bandidos está cerca.",
             "You smell campfire smoke: the bandit camp is close."),
    "avistada": ("A lo lejos se ve algo: el encargo te lleva hasta allí.",
                 "Something stands out in the distance: the job leads you there."),
    "cumplido": ("¡Encargo cumplido!", "Job done!"),
    "paga": ("+%s", "+%s"),
    "paga_rep": ("+%s · reputación con el %s: %s (+%s)", "+%s · reputation with the %s: %s (+%s)"),
    "cumplido_chat": ("✔ %s · %s", "✔ %s · %s"),
    "vencido": ("✘ Se acabó el plazo: %s", "✘ Time ran out: %s"),
    "rep_baja": (" · reputación con el %s: %s (-%s)", " · reputation with the %s: %s (-%s)"),
    "quedan": ("Quedan %s bandidos", "%s bandits left"),
    "crimen": ("Reputación con el %s: %s (-%s)", "Reputation with the %s: %s (-%s)"),
    "caza": ("Bandidos en %s", "Bandits in %s"),
    "exploracion": ("Explora %s", "Explore %s"),
    "caza.texto": ("Una banda asalta los caminos de %s. Encuentra su campamento y acaba con todos.",
                   "A gang is raiding the roads of %s. Find their camp and finish them all."),
    "exploracion.texto": ("Nadie ha vuelto con noticias de %s. Viaja hasta allí y descubre qué esconde.",
                          "No one has come back with news of %s. Travel there and find out what it hides."),
    "estado.quedan": ("Quedan %s bandidos en el campamento.", "%s bandits left in the camp."),
    "estado.buscar": ("Ve a la zona marcada y busca su campamento.", "Go to the marked area and look for their camp."),
    "estado.estructura": ("Dicen que allí hay unas ruinas. Llega hasta ellas.", "They say there are ruins there. Reach them."),
    "estado.corazon": ("Llega al corazón del ducado.", "Reach the heart of the duchy."),
    "rep": ("+%s reputación", "+%s reputation"),
    "dia": ("%s día", "%s day"),
    "dias": ("%s días", "%s days"),
    "aqui": ("aquí mismo", "right here"),
    "lejos": ("a %s bloques al %s", "%s blocks to the %s"),
    "ninguno": ("No llevas ningún encargo. Búscalos en el tablón junto a la campana de los pueblos.",
                "You carry no jobs. Look for them on the board next to the village bell."),
    "en_curso": ("📜 Encargos en curso", "📜 Jobs in progress"),
    "sin_rep": ("Ningún reino sabe aún quién eres. Haz encargos para que se hable de ti.",
                "No kingdom knows who you are yet. Do jobs so they start talking about you."),
    "reputacion": ("⚖ Tu reputación (de -100 a 100)", "⚖ Your reputation (from -100 to 100)"),
    "reputacion_linea": ("%s: %s", "%s: %s"),
    "bandido": ("Bandido", "Bandit"),
    "jefe": ("Jefe de los bandidos", "Bandit chief"),
    "pestana.tablon": ("Tablón", "Board"),
    "pestana.curso": ("En curso (%s/%s)", "In progress (%s/%s)"),
    "coger": ("Coger", "Take"),
    "dejar": ("Dejar", "Drop"),
    "ya_cogido": ("✔ Cogido", "✔ Taken"),
    "pantalla.ninguno": ("No llevas ningún encargo.", "You carry no jobs."),
    "pantalla.vacio": ("Hoy no hay nada clavado en el tablón.", "Nothing is pinned to the board today."),
    "hud": ("%s m %s · %s d", "%s m %s · %s d"),
    "hud_arriba": ("▲ %s m arriba", "▲ %s m up"),
    "hud_abajo": ("▼ %s m abajo", "▼ %s m down"),
    "hud_nivel": ("● a tu altura", "● at your level"),
}
ENCARGOS.update({
    "mazmorra": ("Limpia %s", "Clear %s"),
    "mazmorra.texto": ("Algo se ha despertado en %s. Entra, aguanta las oleadas y acaba con lo que las manda.",
                       "Something has woken up in %s. Go in, hold out against the waves and finish whatever leads them."),
    "estado.jefe": ("Solo queda el jefe.", "Only the boss is left."),
    "estado.oleada": ("Oleada %s de %s.", "Wave %s of %s."),
    "estado.entra": ("Entra en la mazmorra y aguanta las oleadas.", "Enter the dungeon and hold out against the waves."),
})
ENCARGOS.update({
    "guerra": ("⚔ Arrasa el campamento del %s en %s", "⚔ Raze the %s camp in %s"),
    "guerra.texto": ("Las tropas del %s han acampado en %s, en el frente. Arrasa el campamento: con %s campamentos arrasados en ese ducado, nuestro ejército lo tomará.",
                     "Troops of the %s have camped in %s, on the front. Raze the camp: with %s camps razed in that duchy, our army will take it."),
    "estado.soldados": ("Quedan %s soldados en el campamento.", "%s soldiers left in the camp."),
    "estado.buscar_guerra": ("Ve a la zona marcada y busca el campamento enemigo.", "Go to the marked area and look for the enemy camp."),
    "tambores": ("Se oyen tambores de guerra: el campamento enemigo está cerca.", "War drums echo: the enemy camp is close."),
    "quedan_soldados": ("Quedan %s soldados", "%s soldiers left"),
    "soldado": ("Soldado del %s", "Soldier of the %s"),
    "capitan": ("Capitán del %s", "Captain of the %s"),
    "rep_enemigo": ("El %s no lo olvidará · reputación: %s (-%s)", "The %s won't forget it · reputation: %s (-%s)"),
    "frente": ("⚔ Campamentos arrasados en %s: %s de %s", "⚔ Camps razed in %s: %s of %s"),
    "frente_roto": ("⚔ ¡El frente se rompe! En el próximo turno el %s tomará %s.", "⚔ The front breaks! Next turn the %s will take %s."),
    "sin_guerra": ("La guerra ya ha acabado: el campamento no cambia nada en el frente.", "The war is already over: the camp changes nothing on the front."),
})
ENCARGOS.update({
    "embajada": ("✉ Lleva la carta al %s", "✉ Carry the letter to the %s"),
    "embajada.texto": ("Lleva la carta sellada de nuestro rey hasta la plaza de la capital del %s, en %s. Si hay guerra, es una carta de paz; si no, de alianza. Por el camino habrá quien quiera quitártela.",
                       "Carry our king's sealed letter to the square of the capital of the %s, in %s. If there is war, it is a letter of peace; if not, of alliance. Some will try to take it from you on the way."),
    "estado.carta": ("Lleva la carta sellada a la plaza de su capital.", "Take the sealed letter to the square of their capital."),
    "estado.trono": ("Lleva la carta sellada al rey, al salón del trono de su castillo.", "Take the sealed letter to the king, in the throne room of their castle."),
})
EMBAJADA = {
    "carta": ("✉ Carta sellada del %s al %s", "✉ Sealed letter from the %s to the %s"),
    "cazador": ("Cazarrecompensas", "Bounty hunter"),
    "guardia": ("Guardia del %s", "Guard of the %s"),
    "guardias": ("¡Guardias! En el %s no te quieren por aquí.", "Guards! The %s doesn't want you around here."),
    "guardias_guerra": ("¡Guardias! El %s está en guerra con quien te manda.", "Guards! The %s is at war with whoever sent you."),
    "emboscada": ("¡Cazarrecompensas! Alguien no quiere que esa carta llegue.", "Bounty hunters! Someone doesn't want that letter to arrive."),
    "al_trono": ("Aquí te dirán que la carta es para el rey: búscalo en el salón del trono del castillo.",
                 "Here they'll tell you the letter is for the king: find him in the castle's throne room."),
    "sin_carta": ("Te falta la carta sellada: sin ella no te recibirán.", "You're missing the sealed letter: without it they won't receive you."),
    "rep": ("El %s te recibe con respeto · reputación: %s (+%s)", "The %s receives you with respect · reputation: %s (+%s)"),
    "tarde": ("La carta llega tarde: las cosas entre los dos reinos ya han cambiado.", "The letter arrives too late: things between the two kingdoms have already changed."),
    "paz": ("✉ La carta está entregada: en el próximo turno el %s y el %s firmarán la paz.", "✉ The letter is delivered: next turn the %s and the %s will make peace."),
    "alianza": ("✉ La carta está entregada: en el próximo turno el %s y el %s sellarán una alianza.", "✉ The letter is delivered: next turn the %s and the %s will seal an alliance."),
}
MAZMORRAS = {
    "illager": ("el bastión de %s", "the stronghold of %s"),
    "ahogados": ("las ruinas ahogadas de %s", "the drowned ruins of %s"),
    "desierto": ("la tumba de las arenas de %s", "the sand tomb of %s"),
    "jungla": ("el templo perdido de %s", "the lost temple of %s"),
    "hielo": ("la cripta helada de %s", "the frozen crypt of %s"),
    "pantano": ("el cubil del pantano de %s", "the swamp lair of %s"),
    "sculk": ("las profundidades de %s", "the depths of %s"),
    "mina": ("la mina maldita de %s", "the cursed mine of %s"),
    "cripta": ("la cripta de %s", "the crypt of %s"),
    "jefe.illager": ("Capitán de la Guarnición", "Garrison Captain"),
    "jefe.ahogados": ("Señor de las Mareas", "Lord of the Tides"),
    "jefe.desierto": ("Faraón sin Descanso", "Restless Pharaoh"),
    "jefe.jungla": ("Guardián del Templo", "Temple Guardian"),
    "jefe.hielo": ("Rey de la Escarcha", "Frost King"),
    "jefe.pantano": ("Bruja del Cieno", "Mire Witch"),
    "jefe.sculk": ("Eco de las Profundidades", "Echo of the Deep"),
    "jefe.mina": ("Reina de la Mina", "Queen of the Mine"),
    "jefe.cripta": ("Señor de la Cripta", "Lord of the Crypt"),
    "sin_nombre": ("tierras sin nombre", "nameless lands"),
    "despierta": ("%s · algo se mueve en la oscuridad…", "%s · something stirs in the dark…"),
    "corto.illager": ("El bastión", "The stronghold"),
    "corto.ahogados": ("Las ruinas ahogadas", "The drowned ruins"),
    "corto.desierto": ("La tumba de las arenas", "The sand tomb"),
    "corto.jungla": ("El templo perdido", "The lost temple"),
    "corto.hielo": ("La cripta helada", "The frozen crypt"),
    "corto.pantano": ("El cubil del pantano", "The swamp lair"),
    "corto.sculk": ("Las profundidades", "The depths"),
    "corto.mina": ("La mina maldita", "The cursed mine"),
    "corto.cripta": ("La cripta", "The crypt"),
    "viene_jefe": ("¡Algo grande se acerca!", "Something big is coming!"),
    "siguiente": ("Oleada %s de %s", "Wave %s of %s"),
    "barra": ("%s · oleada %s/%s", "%s · wave %s/%s"),
    "llamada": ("¡El jefe llama a los suyos!", "The boss calls for help!"),
    "limpia": ("¡Mazmorra limpia!", "Dungeon cleared!"),
    "cofre": ("Ha aparecido un cofre con el botín", "A chest with the loot has appeared"),
    "noche": ("☠ Algo se ha despertado en %s · %s", "☠ Something has woken up in %s · %s"),
    "rep": ("El %s te lo agradece · reputación: %s (+%s)", "The %s thanks you · reputation: %s (+%s)"),
}
for k, (a, b) in MAZMORRAS.items(): L(f"destinos.mazmorra.{k}", a, b)
for k, (a, b) in EMBAJADA.items(): L(f"destinos.embajada.{k}", a, b)

MAPA = {
    "pestana.reino": ("Reino", "Realm"), "pestana.corte": ("Corte", "Court"), "pestana.cronica": ("Crónica", "Chronicle"),
    "ultimo": ("Lo último", "Latest"), "sin_corte": ("Aún no se sabe quién reina aquí.", "No one knows yet who rules here."),
    "sin_cronica": ("Aún no ha pasado nada en este reino.", "Nothing has happened in this realm yet."),
    "titulo": ("Mapa político", "Political map"),
    "cerrar": ("Cerrar", "Close"),
    "capa_si": ("Colores en tus mapas: sí", "Colours on your maps: on"),
    "capa_no": ("Colores en tus mapas: no", "Colours on your maps: off"),
    "anio": ("Año %s · turno %s · %s reinos conocidos", "Year %s · turn %s · %s known kingdoms"),
    "vacio": ("Aún no hay mapa: el servidor está calculando los territorios de alrededor…", "No map yet: the server is working out the lands around you…"),
    "leyenda.capital": ("♛ Capital", "♛ Capital"),
    "leyenda.pueblos": ("⌂ Aldea ⛏ Mina ♠ Aserradero", "⌂ Village ⛏ Mine ♠ Sawmill"),
    "leyenda.pueblos2": ("⚓ Puerto ♜ Ciudad ⚑ Puesto", "⚓ Port ♜ City ⚑ Outpost"),
    "leyenda.frente": ("Frente de guerra", "War front"),
    "leyenda.salvaje": ("Tierras salvajes", "Wildlands"),
    "leyenda.reciente": ("Cambió de dueño", "Changed hands"),
    "leyenda.niebla": ("Sin explorar", "Unexplored"),
    "ayuda": ("Arrastra · rueda: zoom · 1-9: modos · espacio: a ti · clic: fijar · clic der.: marca · H: ayuda",
              "Drag · wheel: zoom · 1-9: modes · space: to you · click: pin · right click: marker · H: help"),
    "tutorial.titulo": ("Cómo se usa el mapa político", "How to use the political map"),
    "tutorial.reinos": ("Cada color es un reino; las rayas finas parten sus ducados. ♛ es su capital.",
                        "Each colour is a kingdom; thin lines split its duchies. ♛ marks its capital."),
    "tutorial.ficha": ("Pasa el ratón por un reino para ver su ficha a la derecha (estandarte, poder, guerras, uniones). Clic: la dejas fija.",
                       "Hover a kingdom to see its sheet on the right (banner, power, wars, unions). Click: pin it."),
    "tutorial.mover": ("Arrastra para moverte, rueda para acercarte. Espacio te devuelve a donde estás.",
                       "Drag to move, wheel to zoom. Space takes you back to where you are."),
    "tutorial.modos": ("Teclas 1-9 o los botones de la izquierda: reinos, diplomacia, guerras, poder, cultura, seguridad, fauna, relieve y biomas.",
                       "Keys 1-9 or the buttons on the left: kingdoms, diplomacy, wars, power, culture, safety, wildlife, terrain and biomes."),
    "tutorial.marcas": ("Clic derecho: pones una marca con nombre. Clic en una marca: la haces destino y sale la línea de viaje.",
                        "Right click: drop a named marker. Click a marker: make it your destination and a travel line appears."),
    "tutorial.chat": ("En el chat, los nombres de reinos, ducados y capitales se pueden pulsar: abren el mapa ahí.",
                      "In chat, kingdom, duchy and capital names are clickable: they open the map right there."),
    "tutorial.buscar": ("Arriba a la derecha puedes buscar un reino o un ducado por su nombre.",
                        "Top right you can search a kingdom or a duchy by name."),
    "tutorial.cerrar": ("Clic o cualquier tecla para empezar · H: volver a verlo", "Click or press any key to start · H: show this again"),
    "marca_nombre": ("Nombre de la marca", "Marker name"),
    "pista_lejos": ("A %s bloques de ti, al %s", "%s blocks from you, to the %s"),
    "escala": ("%s bloques", "%s blocks"),
    "viaje": ("%s bloques al %s", "%s blocks to the %s"),
    "cargando": ("Cargando fichas…", "Loading…"),
    "sin_ficha": ("Pasa el ratón por un reino para ver su ficha, o haz clic para dejarla fija.", "Hover over a kingdom to see its card, or click to pin it."),
    "fijada": ("(ficha fija: clic fuera para soltarla)", "(pinned: click elsewhere to unpin)"),
    "poder": ("Poder %s · puesto %s de %s", "Power %s · rank %s of %s"),
    "gente": ("Unos %s habitantes · %s ducados", "About %s people · %s duchies"),
    "estabilidad": ("Estabilidad %s/100", "Stability %s/100"),
    "capital": ("♛ Capital: %s", "♛ Capital: %s"),
    "capital_perdida": ("♛ Capital perdida: %s", "♛ Capital lost: %s"),
    "vasallo": ("Vasallo del %s", "Vassal of the %s"),
    "guerras": ("⚔ En guerra con %s", "⚔ At war with %s"),
    "aliados": ("✦ Aliado del %s", "✦ Allied with %s"),
    "union": ("❖ En la %s, con el %s", "❖ Part of the %s, with the %s"),
    "cronica_recientes": ("▲ %s más recientes (rueda)", "▲ %s more recent (scroll)"),
    "cronica_antiguos": ("▼ %s más antiguos (rueda)", "▼ %s older (scroll)"),
    "en_paz": ("En paz y sin alianzas", "At peace, no alliances"),
    "rep": ("Tu reputación: %s", "Your reputation: %s"),
    "rep_buena": ("Tu reputación: %s (te aprecian)", "Your reputation: %s (they like you)"),
    "rep_mala": ("Tu reputación: %s (sus guardias van a por ti)", "Your reputation: %s (their guards are after you)"),
    "mar": ("Mar abierto", "Open sea"),
    "aguas": ("Aguas del %s", "Waters of the %s"),
    "ducado_desconocido": ("Ducado sin nombre todavía", "Duchy not named yet"),
    "salvaje": ("Tierras salvajes: aquí no manda nadie", "Wildlands: nobody rules here"),
    "pista_poder": ("Poder %s · puesto %s", "Power %s · rank %s"),
    "pista_reciente": ("Acaba de cambiar de dueño", "Just changed hands"),
    "buscar": ("Buscar reino o ducado", "Search kingdom or duchy"),
    "modos": ("Modos", "Modes"),
    "modo.reinos": ("Reinos", "Kingdoms"),
    "modo.diplomacia": ("Diplomacia", "Diplomacy"),
    "modo.guerras": ("Guerras", "Wars"),
    "modo.poder": ("Poder", "Power"),
    "modo.cultura": ("Cultura", "Culture"),
    "modo.seguridad": ("Seguridad", "Safety"),
    "modo.fauna": ("Fauna", "Wildlife"),
    "modo.relieve": ("Relieve", "Terrain"),
    "modo.biomas": ("Biomas", "Biomes"),
    "leyenda.alianza": ("Alianza", "Alliance"),
    "leyenda.guerra": ("Guerra", "War"),
    "leyenda.vasallaje": ("Vasallaje", "Vassalage"),
    "leyenda.union": ("Unión de coronas", "Union of crowns"),
    "leyenda.en_guerra": ("Reinos en guerra", "Kingdoms at war"),
    "leyenda.en_paz": ("En paz", "At peace"),
    "leyenda.debil": ("Débil", "Weak"),
    "leyenda.medio": ("Medio", "Middling"),
    "leyenda.fuerte": ("El más fuerte", "Strongest"),
    "leyenda.seguro": ("Seguro", "Safe"),
    "leyenda.tranquilo": ("Tranquilo", "Calm"),
    "leyenda.peligroso": ("Peligroso", "Dangerous"),
    "leyenda.muy_peligroso": ("Muy peligroso", "Very dangerous"),
    "leyenda.bosque": ("Bosque", "Forest"),
    "leyenda.llanura": ("Llanura", "Plains"),
    "leyenda.nieve": ("Nieve", "Snow"),
    "leyenda.desierto": ("Desierto", "Desert"),
    "leyenda.jungla": ("Jungla", "Jungle"),
    "leyenda.pantano": ("Pantano", "Swamp"),
    "leyenda.raros": ("✦ Bichos raros", "✦ Rare creatures"),
    "leyenda.bajo": ("Llano", "Lowland"),
    "leyenda.colinas": ("Colinas", "Hills"),
    "leyenda.monte": ("Monte", "Highland"),
    "leyenda.cumbres": ("Cumbres", "Peaks"),
    "leyenda.rio": ("Río", "River"),
    "geo.sierra": ("Sierra de %s", "%s Range"),
    "geo.rio": ("Río %s", "River %s"),
    "geo.mar": ("Mar de %s", "Sea of %s"),
    "geo.golfo": ("Golfo de %s", "Gulf of %s"),
    "cultura": ("Cultura: %s", "Culture: %s"),
    "cronica": ("Crónica", "Chronicle"),
    "pista_peligro": ("Peligro: %s", "Danger: %s"),
    "pista_animales": ("Animales: %s", "Animals: %s"),
    "pista_bichos": ("Ojo con: %s", "Watch out for: %s"),
    "pista_altura": ("Altura: y=%s", "Height: y=%s"),
    "pista_fondo": ("Fondo a y=%s", "Seabed at y=%s"),
    "pista_rio": ("Pasa un río", "A river runs here"),
    "pista_bioma": ("Bioma: %s", "Biome: %s"),
    "terreno.campo": ("Campo abierto", "Open country"),
    "terreno.mar": ("Mar", "Sea"),
    "terreno.ribera": ("Ribera", "Riverside"),
    "terreno.nieve": ("Nieve", "Snow"),
    "terreno.badlands": ("Tierras baldías", "Badlands"),
    "terreno.desierto": ("Desierto", "Desert"),
    "terreno.jungla": ("Jungla", "Jungle"),
    "terreno.sabana": ("Sabana", "Savanna"),
    "terreno.pantano": ("Pantano", "Swamp"),
    "terreno.taiga": ("Taiga", "Taiga"),
    "terreno.montana": ("Montaña", "Mountains"),
    "terreno.bosque": ("Bosque", "Forest"),
    "terreno.llanura": ("Llanura", "Plains"),
}
for k, (a, b) in MAPA.items(): L(f"destinos.mapa.{k}", a, b)
for k, (a, b) in ENCARGOS.items(): L(f"destinos.encargo.{k}", a, b)
L("destinos.moneda.cobre", "%s cobre", "%s copper")
L("destinos.moneda.plata", "%s plata", "%s silver")
L("destinos.moneda.oro", "%s oro", "%s gold")
L("block.destinos_core.tablon", "Tablón de encargos", "Quest board")
L("item.destinos_core.moneda_cobre", "Moneda de cobre", "Copper coin")
L("item.destinos_core.moneda_plata", "Moneda de plata", "Silver coin")
L("item.destinos_core.moneda_oro", "Moneda de oro", "Gold coin")
for k, (m, f) in TITULOS.items():
    L(f"destinos.titulo.{k}.m", *m); L(f"destinos.titulo.{k}.f", *f)
# v0.7 Gente: el ducado de la capital lo gobierna el rey en persona (Destinos Core pone g = rey_m / rey_f)
for k in TIPOS:
    L(f"destinos.titulo.{k}.rey_m", "Rey %s", "King %s"); L(f"destinos.titulo.{k}.rey_f", "Reina %s", "Queen %s")
L("destinos.gobierna_corto", "Gobierna %s", "Ruled by %s")
for i, (a, b) in enumerate(LEMAS): L(f"destinos.lema.{i}", a, b)
for i, (a, b) in enumerate(RASGOS): L(f"destinos.rasgo.{i}", a, b)
for i, (a, b) in enumerate(FAMAS): L(f"destinos.fama.{i}", a, b)
for i, (a, b) in enumerate(GEO): L(f"destinos.salvaje.{i}", a, b)
for k, c in CULTURAS.items(): L(f"destinos.cultura.{k}", c["es"], c["en"])
assert len(LEMAS) == N_LEMAS and len(RASGOS) == N_RASGOS and len(FAMAS) == N_FAMAS and len(GEO) == N_GEO


# ---------------------------------------------------------------- Humanoid Villagers (v0.6.3)
# Con Humanoid Villagers (datapack dentro de un mod: humvil), cada aldeano se vuelve invisible y encima va un maniquí
# con modelo de jugador, nombre y skin. Dos arreglos, sin tocar su mod:
#  - que no saque maniquíes con el nombre y la skin de los jugadores del mundo ($human_pool 2 = solo aleatorios): si
#    no, a veces un aldeano se llama como tú y en sus charlas parece que hablas tú;
#  - el oficio del aldeano en la línea de debajo del nombre (su "description", que trae "NPC"), y nombre y oficio solo
#    al mirarlo (sin CustomNameVisible). Se mira cada 2 s, solo cerca de los jugadores, y solo se escribe si cambia.
# Si Humanoid Villagers no está, no hay maniquíes con su etiqueta y nada de esto hace nada.
OFICIOS = {
    "minecraft:armorer": (("Armero", "Armera"), "Armorer"),
    "minecraft:butcher": (("Carnicero", "Carnicera"), "Butcher"),
    "minecraft:cartographer": (("Cartógrafo", "Cartógrafa"), "Cartographer"),
    "minecraft:cleric": (("Clérigo", "Clériga"), "Cleric"),
    "minecraft:farmer": (("Granjero", "Granjera"), "Farmer"),
    "minecraft:fisherman": (("Pescador", "Pescadora"), "Fisherman"),
    "minecraft:fletcher": (("Flechero", "Flechera"), "Fletcher"),
    "minecraft:leatherworker": (("Peletero", "Peletera"), "Leatherworker"),
    "minecraft:librarian": (("Bibliotecario", "Bibliotecaria"), "Librarian"),
    "minecraft:mason": (("Cantero", "Cantera"), "Mason"),
    "minecraft:shepherd": (("Pastor", "Pastora"), "Shepherd"),
    "minecraft:toolsmith": (("Herrero", "Herrera"), "Toolsmith"),
    "minecraft:weaponsmith": (("Espadero", "Espadera"), "Weaponsmith"),
    "minecraft:nitwit": (("Vecino", "Vecina"), "Villager"),
    "minecraft:none": (("Sin oficio", "Sin oficio"), "Unemployed"),
    "nino": (("Niño", "Niña"), "Child"),
    "ambulante": (("Mercader ambulante", "Mercader ambulante"), "Wandering trader"),
}
for k, ((m, f), en_) in OFICIOS.items():
    L(f"destinos.oficio.{k}.m", m, en_); L(f"destinos.oficio.{k}.f", f, en_)
fn("humvil/ajusta", [
    "# Humanoid Villagers: solo maniquíes aleatorios, nunca con el nombre y la skin de un jugador (2 s después de cargar,",
    "# cuando su datapack ya ha puesto sus ajustes; si no está, el marcador no existe y esto no hace nada)",
    "scoreboard players set $human_pool humvil.settings 2",
    "schedule function destinos:humvil/oficios 40t replace",
])
fn("humvil/oficios", [
    "schedule function destinos:humvil/oficios 40t replace",
    "execute as @a at @s as @e[type=minecraft:mannequin,tag=humvil.human,distance=..32] at @s run function destinos:humvil/oficio",
])
fn("humvil/oficio", [
    "# @s: el maniquí de un aldeano; su aldeano (invisible) está debajo y lleva el mismo humvil.id",
    "scoreboard players operation $id humvil.id = @s humvil.id",
    "tag @e[type=!minecraft:mannequin,tag=humvil.human.transformed,distance=..3,predicate=humvil:lib/check_id,limit=1] add destinos.hv",
    "execute unless entity @e[tag=destinos.hv,distance=..3,limit=1] run return 0",
    'data modify storage destinos:tmp hv set value {p:"minecraft:none",g:"m",antes:""}',
    "data modify storage destinos:tmp hv.p set from entity @e[tag=destinos.hv,distance=..3,limit=1] VillagerData.profession",
    'execute if entity @e[tag=destinos.hv,distance=..3,type=minecraft:wandering_trader] run data modify storage destinos:tmp hv.p set value "ambulante"',
    "# niño: edad negativa (sin el predicado de Humanoid Villagers, que no se puede nombrar si el mod no está)",
    "execute store result score #edad dr.calc run data get entity @e[tag=destinos.hv,distance=..3,limit=1] Age",
    'execute if score #edad dr.calc matches ..-1 run data modify storage destinos:tmp hv.p set value "nino"',
    'execute if score @e[tag=destinos.hv,distance=..3,limit=1] humvil.transform.gender matches 1 run data modify storage destinos:tmp hv.g set value "f"',
    "tag @e[tag=destinos.hv,distance=..3] remove destinos.hv",
    "data modify storage destinos:tmp hv.antes set from entity @s description.translate",
    "function destinos:humvil/etiqueta with storage destinos:tmp hv",
])
fn("humvil/etiqueta", [
    "# Solo si el oficio ha cambiado (escribir en una entidad la vuelve a cargar entera)",
    'execute store success score #hv dr.calc run data modify storage destinos:tmp hv.antes set value "destinos.oficio.$(p).$(g)"',
    "execute if score #hv dr.calc matches 0 run return 0",
    'data modify entity @s description set value {translate:"destinos.oficio.$(p).$(g)",fallback:""}',
    "data modify entity @s hide_description set value 0b",
    "data modify entity @s CustomNameVisible set value 0b",
])

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
wjson(os.path.join(DP, "pack.mcmeta"), {"pack": {"description": "Destinos: Reinos v0.4 · reinos y ducados RNG",
                                                 "min_format": 88, "max_format": 99}})
wjson(os.path.join(RP, "pack.mcmeta"), {"pack": {"description": "Destinos: Reinos v0.4 · idiomas ES/EN",
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
    # Capitales: una aldea grande por reino, en el sitio que elige Destinos Core (solo con el mod: la colocación
    # destinos_core:capital y la estructura destinos_core:capital son suyas). La estructura copia el pueblo que toca
    # en ese bioma, de vanilla o de cualquier mod, con más profundidad (EstructuraCapital.java).
    CAP = os.path.join(MOD, "data/destinos_core")
    def wj(rel, o):
        q = os.path.join(CAP, rel); os.makedirs(os.path.dirname(q), exist_ok=True)
        open(q, "w", encoding="utf-8").write(json.dumps(o, ensure_ascii=False, indent=2) + "\n")
    # todos los biomas del mundo normal, también los de mods que no se apuntan al tag de vanilla
    wj("tags/worldgen/biome/capital.json", {"replace": False, "values": [
        "#minecraft:is_overworld", {"id": "#c:is_overworld", "required": False}]})
    wj("worldgen/structure/capital.json", {"type": "destinos_core:capital", "biomes": "#destinos_core:capital",
                                           "step": "surface_structures", "spawn_overrides": {},
                                           "terrain_adaptation": "beard_thin"})
    wj("worldgen/structure_set/capitales.json", {"structures": [{"structure": "destinos_core:capital", "weight": 1}],
                                                 "placement": {"type": "destinos_core:capital", "salt": 0}})
    # v0.6.3 Pueblos especializados: un pueblo con oficio por ducado y puestos de vigía en las fronteras, en el sitio
    # que elige Destinos Core (EleccionPueblo.java), trazados como capitales pequeñas (EstructuraPueblo.java)
    wj("worldgen/structure/pueblo.json", {"type": "destinos_core:pueblo", "biomes": "#destinos_core:capital",
                                          "step": "surface_structures", "spawn_overrides": {},
                                          "terrain_adaptation": "beard_thin"})
    wj("worldgen/structure_set/pueblos.json", {"structures": [{"structure": "destinos_core:pueblo", "weight": 1}],
                                               "placement": {"type": "destinos_core:pueblo", "salt": 0}})
    print("variante del mod en", MOD)
