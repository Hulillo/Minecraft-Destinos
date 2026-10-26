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
    # la capital (el pueblo del bioma, agrandado): que se genere en varios sitios
    *[f"place structure destinos_core:capital {x} 80 {z}" for x, z in [(0, 600), (3000, -2000), (-4000, 3500)]],
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
if capitales < 2:
    print("\n".join(l.rstrip() for l in lineas if "capital" in l or "structure" in l.lower())[-3000:])
    sys.exit(f"solo se han generado {capitales} de 3 capitales de prueba")
if not ok_terr:
    sys.exit("geo/territorio no devolvió #tdx: el mod no respondió")
print("\nOK: el servidor carga Destinos Core y el datapack sin errores")
