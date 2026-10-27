"""Arranca un servidor de Minecraft con Destinos Core (./gradlew runServer), espera a que cargue,
lanza unos comandos de prueba por consola y falla si alguna función del datapack no carga o da error.
Uso (desde la raíz del repo, después de build.py --mod): python herramientas/servidor_prueba.py"""
import os, re, subprocess, sys, threading, time

RAIZ = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "destinos-core")
RUN = os.path.join(RAIZ, "run")
os.makedirs(RUN, exist_ok=True)
open(os.path.join(RUN, "eula.txt"), "w").write("eula=true\n")
open(os.path.join(RUN, "server.properties"), "w").write(
    "online-mode=false\nlevel-seed=184467221\nspawn-protection=0\nsync-chunk-writes=false\n")

# La zona se calcula en segundo plano: se repite geo/territorio hasta que #tlisto sea 1
ESPERA = ["scoreboard players set #vx dr.calc 1234", "scoreboard players set #vz dr.calc -5678",
          "function destinos:geo/territorio", "scoreboard players get #tlisto dr.calc"]
COMANDOS = [
    # la capital de prueba necesita cargados los chunks que puede ocupar (hasta 128 bloques del centro)
    "forceload add -160 440 0 760",
    "forceload add 16 440 160 760",
    "destinoscore estado",
    "scoreboard players get #tdx dr.calc",
    "scoreboard players get #tkx dr.calc",
    "scoreboard players get #tw dr.calc",
    "destinoscore estado",
    "destinoscore politica",
    "destinoscore turno 16",
    "destinoscore politica reinos",
    "destinoscore cronica",
    "data get storage destinos:politica turno",
    # la capital (el pueblo del bioma, agrandado): que se pueda generar
    "place structure destinos_core:capital 0 80 600",
    # la capital que ha salido sola al cargar los chunks: que su chunk la tenga y los de alrededor la conozcan
    "execute positioned 196 100 667 run destinoscore capital",
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
              f"execute positioned {rx} 100 {rz} run destinoscore capital"]:
        p.stdin.write(c + "\n"); p.stdin.flush(); time.sleep(10)
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
if not diag or "su chunk tiene la capital" not in diag.group(0) or "NO la conoce" in diag.group(0):
    sys.exit("la capital del mapa no está entera en el mundo: " + (diag.group(0) if diag else "sin /destinoscore capital"))
revision = re.search(r"reinos revisados en .*", salida)
if not revision:
    sys.exit("/destinoscore revisa no ha contestado")
if "capitales en el agua: 0" not in revision.group(0):
    sys.exit("hay capitales elegidas en el agua: " + revision.group(0))
if not rica:
    print("AVISO: no hay ninguna capital rica en los reinos revisados; el castillo no se ha probado")
else:
    if not re.search(r"capital en .*muralla de nivel 2, con castillo", salida):
        print("\n".join(l.rstrip() for l in lineas if "Destinos Core" in l)[-4000:])
        sys.exit("la capital rica no se ha generado con su castillo")
    if not re.search(rf"capital del reino en {rx}, {rz} .*su chunk tiene la capital", salida):
        sys.exit("la capital rica no está en su chunk")
if "Tried to load a DUMMY block entity" in salida:
    sys.exit("hay bloques con datos (estandartes, barriles…) puestos fuera de su sitio: " + re.search(r"Tried to load a DUMMY.*", salida).group(0))
if "plaza mayor de la capital" not in salida:
    print("AVISO: ninguna plaza mayor ha tenido sitio para sus estandartes")
if not ok_terr:
    sys.exit("geo/territorio no devolvió #tdx: el mod no respondió")
print("\nOK: el servidor carga Destinos Core y el datapack sin errores")
