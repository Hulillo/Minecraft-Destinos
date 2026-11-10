"""Arranca un servidor de Minecraft con Destinos Core (./gradlew runServer), espera a que cargue,
lanza unos comandos de prueba por consola y falla si alguna función del datapack no carga o da error.
Uso (desde la raíz del repo, después de build.py --mod): python herramientas/servidor_prueba.py"""
import os, re, subprocess, sys, threading, time

RAIZ = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "destinos-core")
RUN = os.path.join(RAIZ, "run")
os.makedirs(RUN, exist_ok=True)
open(os.path.join(RUN, "eula.txt"), "w").write("eula=true\n")
open(os.path.join(RUN, "server.properties"), "w").write(
    "online-mode=false\nlevel-seed=184467221\nspawn-protection=0\nsync-chunk-writes=false\n"
    # sin jugadores el servidor se pausa a los 60 s y deja de hacer ticks (los tablones se ponen en un tick)
    "pause-when-empty-seconds=-1\n")
# Humanoid Villagers no está en el servidor de prueba: su predicado de ids, de mentira, para probar el oficio del maniquí
HV = os.path.join(RUN, "world", "datapacks", "humvil_de_prueba")
os.makedirs(os.path.join(HV, "data", "humvil", "predicate", "lib"), exist_ok=True)
open(os.path.join(HV, "pack.mcmeta"), "w").write('{"pack":{"description":"prueba","min_format":88,"max_format":99}}')
open(os.path.join(HV, "data", "humvil", "predicate", "lib", "check_id.json"), "w").write(
    '{"condition":"minecraft:value_check","value":{"type":"minecraft:score","target":{"type":"minecraft:fixed","name":"$id"},'
    '"score":"humvil.id"},"range":{"min":{"type":"minecraft:score","target":{"type":"minecraft:context","target":"this"},'
    '"score":"humvil.id"},"max":{"type":"minecraft:score","target":{"type":"minecraft:context","target":"this"},"score":"humvil.id"}}}')

# La zona se calcula en segundo plano: se repite geo/territorio hasta que #tlisto sea 1
ESPERA = ["scoreboard players set #vx dr.calc 1234", "scoreboard players set #vz dr.calc -5678",
          "function destinos:geo/territorio", "scoreboard players get #tlisto dr.calc"]
COMANDOS = [
    # la capital de prueba necesita cargados los chunks que puede ocupar (hasta 128 bloques del centro)
    "forceload add -160 440 0 760",
    "forceload add 16 440 160 760",
    "forceload add 176 592 304 776",
    "destinoscore estado",
    "scoreboard players get #tdx dr.calc",
    "scoreboard players get #tkx dr.calc",
    "scoreboard players get #tw dr.calc",
    "destinoscore estado",
    "destinoscore politica",
    "destinoscore turno 16",
    "destinoscore politica reinos",
    "destinoscore cronica",
    # el mapa político: las fichas de reinos y los nombres de los ducados conocidos
    "destinoscore fichas",
    "data get storage destinos:politica turno",
    # la capital (el pueblo del bioma, agrandado): que se pueda generar
    "place structure destinos_core:capital 0 80 600",
    # v0.6.3: un pueblo propio (con /place sale una aldea) y los pueblos de los ducados de alrededor
    "forceload add 400 -1400 560 -1240",
    "place structure destinos_core:pueblo 480 80 -1320",
    # y uno de los que salen solos (la aldea más cercana de la lista), para auditarlo luego
    "forceload add 298 -736 458 -576",
    "destinoscore pueblos",
    # la capital que ha salido sola al cargar los chunks: que su chunk la tenga y los de alrededor la conozcan
    "execute positioned 196 100 667 run destinoscore capital",
    "execute positioned 196 100 667 run destinoscore muralla",
    # los cotilleos de Humanoid Villagers: lo que se sabe de la zona ya calculada al principio (#vx #vz, reino)
    "execute positioned 1234 100 -5678 run destinoscore cotilleo",
    "data get storage destinos:cotilleo hay",
    # el oficio bajo el nombre del maniquí de Humanoid Villagers (aldeano invisible + maniquí con el mismo humvil.id)
    "scoreboard objectives add humvil.id dummy",
    "scoreboard objectives add humvil.transform.gender dummy",
    'summon minecraft:villager -80 200 600 {NoAI:1b,NoGravity:1b,Tags:["humvil.human.transformed","hvprueba"],'
    'VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"}}',
    'summon minecraft:mannequin -80 200 600 {NoGravity:1b,CustomNameVisible:1b,CustomName:"Roberto",hide_description:1b,'
    'Tags:["humvil.human","hvprueba"]}',
    "scoreboard players set @e[tag=hvprueba] humvil.id 7",
    "execute as @e[type=minecraft:mannequin,tag=hvprueba] at @s run function destinos:humvil/oficio",
    "data get entity @e[type=minecraft:mannequin,tag=hvprueba,limit=1] description",
    "data get entity @e[type=minecraft:mannequin,tag=hvprueba,limit=1] hide_description",
    "data get entity @e[type=minecraft:mannequin,tag=hvprueba,limit=1] CustomNameVisible",
    "data get storage destinos:tmp hv",
    # encargos: lo que ofrecería hoy un tablón en la zona ya calculada
    "destinoscore encargos 1234 -5678",
    "destinoscore tablones",
    "execute positioned 196 100 667 run destinoscore encargos guerra",
    "destinoscore mazmorras",
    "destinoscore mazmorras prueba",
    # las capitales de los 9×9 reinos de alrededor: ninguna en el agua (con la altura de verdad); las ricas son
    # pocas, así que hacen falta bastantes reinos para que salga una y probar su castillo
    "destinoscore revisa 4",
]
# Frases que Minecraft escribe cuando algo del datapack falla
MALO = re.compile(r"Failed to (load|parse) function|Couldn't load function|Unknown or incomplete command|"
                  r"Failed to place structure|Failed to parse|Couldn't parse element|"
                  r"Failed to load datapacks|Exception|ERROR\]|Unknown function", re.I)

gradlew = os.path.join(RAIZ, "gradlew")
p = subprocess.Popen([gradlew, "runServer", "--no-daemon", "--console=plain"], cwd=RAIZ,
                     stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
lineas, listo = [], threading.Event()

def leer():
    for l in p.stdout:
        sys.stdout.write(l)
        lineas.append(l)
        if re.search(r"Done \([\d.,]+s\)!", l):
            listo.set()
threading.Thread(target=leer, daemon=True).start()

if not listo.wait(900):
    p.kill(); sys.exit("El servidor no llegó a arrancar")
time.sleep(5)
inicio = time.time()
while not any(re.search(r"#tlisto has 1 ", l) for l in lineas):
    if time.time() - inicio > 300:
        p.kill(); sys.exit("La zona de terreno no terminó de calcularse en 5 minutos")
    for c in ESPERA:
        p.stdin.write(c + "\n"); p.stdin.flush()
    time.sleep(5)
print(f"zona calculada en {time.time() - inicio:.0f} s sin bloquear el servidor")
for c in COMANDOS:
    p.stdin.write(c + "\n"); p.stdin.flush(); time.sleep(3)
# una capital rica de verdad (con castillo), generada al cargar sus chunks como al explorar
rica = None
for _ in range(100):
    m = re.search(r"primera rica en (-?\d+) (-?\d+)", "".join(lineas))
    if m or any("reinos revisados en" in l for l in lineas):
        rica = m
        break
    time.sleep(3)
if rica:
    rx, rz = int(rica.group(1)), int(rica.group(2))
    for c in [f"forceload add {rx - 160} {rz - 160} {rx} {rz + 160}", f"forceload add {rx + 16} {rz - 160} {rx + 160} {rz + 160}",
              f"execute positioned {rx} 100 {rz} run destinoscore capital", "destinoscore tablones"]:
        p.stdin.write(c + "\n"); p.stdin.flush(); time.sleep(10)
# los ojos de la generación: lo generado contra su plano (muralla, casas, calles), en la capital que salió sola, en el
# pueblo y en la rica; con tiempo para que acaben de generarse los chunks cargados
time.sleep(30)
for c in ["execute positioned 196 100 667 run destinoscore audita", "execute positioned 378 100 -656 run destinoscore audita"] + (
        [f"execute positioned {rx} 100 {rz} run destinoscore audita"] if rica else []):
    p.stdin.write(c + "\n"); p.stdin.flush(); time.sleep(8)
p.stdin.write("stop\n"); p.stdin.flush()
try:
    p.wait(300)
except subprocess.TimeoutExpired:
    p.kill()

malas = []
for i, l in enumerate(lineas):
    if (MALO.search(l) and "destinos" in l.lower()) or re.search(r"Failed to load function|Couldn't load function", l):
        malas += [x.rstrip() for x in lineas[i:i + 4]]  # la línea siguiente suele decir qué comando falla
salida = "".join(lineas)
ok_terr = re.search(r"#tdx has \S+ \[dr\.calc\]|#tdx tiene", salida)
if malas:
    print("\n=== FALLOS DEL DATAPACK / MOD ===")
    print("\n".join(dict.fromkeys(malas)))
    sys.exit(1)
capitales = len(re.findall(r"Generated structure \S*destinos_core:capital", salida))
if "agrandado no es válido" in salida:
    sys.exit("la capital no ha podido agrandar el pueblo (ver el aviso de arriba)")
if "antes de que el datapack ponga la sal" in salida:
    sys.exit("se han generado chunks sin sal (ahí no habría capitales)")
if capitales < 1:
    print("\n".join(l.rstrip() for l in lineas if "capital" in l or "structure" in l.lower())[-3000:])
    sys.exit("la capital de prueba no se ha generado")
diag = re.search(r"capital del reino en .*", salida)
if not diag or "su plano tiene" not in diag.group(0):
    sys.exit("la capital del mapa no está entera en el mundo: " + (diag.group(0) if diag else "sin /destinoscore capital"))
if not re.search(r"Destinos Core: aldea en ", salida):
    print("\n".join(l.rstrip() for l in lineas if "aldea" in l or "pueblo" in l.lower())[-3000:])
    sys.exit("el pueblo de prueba (destinos_core:pueblo) no se ha generado")
pueblos = re.search(r"pueblos propios en 5×5 ducados.*", salida)
if not pueblos:
    sys.exit("/destinoscore pueblos no ha contestado")
print("Pueblos:", pueblos.group(0))
# la generación nueva (ObrasMundo): la capital la construyen sus chunks al decorarse, sin depender de la estructura
if not re.search(r"se empieza a construir capital de ", salida):
    sys.exit("ningún chunk ha empezado a construir la capital (ObrasMundo)")
cotilleo = re.search(r"destinos:cotilleo has the following contents: .*", salida)
if not cotilleo or "reino" not in cotilleo.group(0):
    sys.exit("destinoscore cotilleo no ha dejado nada que contar: " + (cotilleo.group(0) if cotilleo else "sin respuesta"))
print("Cotilleo:", cotilleo.group(0))
print("Maniquí:", "\n".join(l.rstrip() for l in lineas if "Roberto" in l or "destinos:tmp" in l or "hvprueba" in l))
if not re.search(r"has the following entity data: \{.*translate: \"destinos\.oficio\.minecraft:farmer", salida):
    sys.exit("el maniquí de prueba no tiene el oficio bajo el nombre")
auditoria = list(dict.fromkeys(re.sub(r"^.*?(AUDITORIA)", r"\1", l.rstrip()) for l in lineas if "AUDITORIA" in l))
print("\n=== AUDITORÍA DE LO GENERADO (de momento solo informa) ===")
print("\n".join(auditoria) if auditoria else "sin respuesta de /destinoscore audita")
if not auditoria:
    sys.exit("/destinoscore audita no ha contestado")
revision = re.search(r"reinos revisados en .*", salida)
if not revision:
    sys.exit("/destinoscore revisa no ha contestado")
if "capitales en el agua: 0" not in revision.group(0):
    sys.exit("hay capitales elegidas en el agua: " + revision.group(0))
if not rica:
    print("AVISO: no hay ninguna capital rica en los reinos revisados; el castillo no se ha probado")
else:
    if not re.search(r"capital en .*\(nivel 2, con castillo", salida):
        print("\n".join(l.rstrip() for l in lineas if "Destinos Core" in l)[-4000:])
        sys.exit("la capital rica no se ha generado con su castillo")
    if not re.search(rf"capital del reino en {rx}, {rz} .*su plano tiene", salida):
        sys.exit("la capital rica no tiene plano")
if "Tried to load a DUMMY block entity" in salida:
    sys.exit("hay bloques con datos (estandartes, barriles…) puestos fuera de su sitio: " + re.search(r"Tried to load a DUMMY.*", salida).group(0))
if "plaza mayor de la capital" not in salida:
    print("AVISO: ninguna plaza mayor ha tenido sitio para sus estandartes")
if not re.search(r"Destinos Core: tablón en .*: [1-9]\d* encargos", salida):
    print("\n".join(l.rstrip() for l in lineas if "tablón" in l or "encargo" in l.lower())[-3000:])
    sys.exit("el tablón de prueba no ha ofrecido ningún encargo")
cuentas = re.findall(r"tablones · \d+ chunks mirados · (\d+) campanas vistas · (\d+) tablones puestos", salida)
if cuentas and int(cuentas[-1][0]) > 0 and int(cuentas[-1][1]) == 0:
    print("\n".join(l.rstrip() for l in lineas if "tabl" in l or "campana" in l)[-3000:])
    sys.exit("se han visto campanas de capitales pero ninguna ha recibido su tablón")
if "tablón de encargos junto a la campana" not in salida:
    print("AVISO: ninguna campana de pueblo o capital ha recibido su tablón")
fichas = re.findall(r"Destinos Core: fichas del mapa · (\d+) reinos · (\d+) ducados · (\d+) con nombre.*", salida)
print("fichas:", fichas[-1] if fichas else "sin respuesta")
if not fichas:
    sys.exit("/destinoscore fichas no ha contestado")
if int(fichas[-1][0]) == 0 or int(fichas[-1][2]) == 0:
    sys.exit("las fichas del mapa político han salido vacías")
fauna = re.findall(r"fichas del mapa · .* · (\d+) con fauna · (\d+) peligrosos", salida)
print("fauna y peligro:", fauna[-1] if fauna else "sin datos")
if not fauna or int(fauna[-1][0]) == 0:
    sys.exit("ningún ducado tiene fauna en las fichas del mapa")
guerra = re.findall(r"Destinos Core: campamento de guerra de prueba · .*", salida)
print("guerra:", guerra[-1] if guerra else "sin respuesta")
if not guerra:
    sys.exit("/destinoscore encargos guerra no ha contestado")
if "capitán MAL" in guerra[-1] or re.search(r"· 0 soldados|· 0 de cuero", guerra[-1]):
    sys.exit("el campamento de guerra de prueba no ha salido bien")
maz = re.findall(r"mazmorras · (\d+) apuntadas", salida)
print("mazmorras:", (re.findall(r"Destinos Core: mazmorras · .*", salida) or ["sin respuesta"])[-1])
print("prueba:", (re.findall(r"Destinos Core: mazmorra de prueba · .*", salida) or ["sin respuesta"])[-1])
if not maz:
    sys.exit("/destinoscore mazmorras no ha contestado")
if "jefe MAL" in salida:
    sys.exit("el jefe de la mazmorra de prueba no ha salido bien")
if int(maz[-1]) == 0:
    print("AVISO: no se ha apuntado ninguna mazmorra en los chunks cargados")
if not ok_terr:
    sys.exit("geo/territorio no devolvió #tdx: el mod no respondió")
print("\nOK: el servidor carga Destinos Core y el datapack sin errores")
