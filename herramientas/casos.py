"""Genera casos de prueba con el simulador: sal x z dx dz kx kz salvaje color (uno por línea).
PruebaGeo.java los lee y comprueba que Geo.java da exactamente lo mismo."""
import os, random, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import sim
from constantes import COLORES, SAL_COLOR

rnd = random.Random(2)
with open(sys.argv[1], "w") as f:
    for sal in (184467221, 99, 1999999999, 7, -123456):
        for _ in range(2000):
            x, z = rnd.randint(-60000, 60000), rnd.randint(-60000, 60000)
            (dx, dz), (kx, kz), w = sim.territorio(x, z, sal)
            color = int(COLORES[sim.hash_(kx, kz, sal + SAL_COLOR) % len(COLORES)][1:], 16)
            f.write(f"{sal} {x} {z} {dx} {dz} {kx} {kz} {int(w)} {color}\n")
