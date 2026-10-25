"""Constantes del mapa político, en un solo sitio para build.py y herramientas/sim.py.
Geo.java (Destinos Core) tiene las mismas: si cambias una aquí, cámbiala allí (PruebaGeo lo comprueba en la CI)."""
S_DUCADO = 1500      # tamaño de celda de ducado (bloques)
S_REINO = 6000       # tamaño de celda de reino (bloques)
WARP = 1200          # desplazamiento máx. del centro del ducado antes de buscar su reino
D_FRONTERA = 2800    # a partir de esta distancia al centro del reino, el ducado puede ser salvaje
P_FRONTERA = 45      # % de ducados lejanos que son salvajes
P_SALVAJE = 4        # % de ducados salvajes en cualquier sitio
K_JZ = 7919          # desplazamiento de sal para el jitter Z
K_REINO = 104729     # desplazamiento de sal para la capa de reinos
K_SALV = 1299709     # desplazamiento de sal para tierras salvajes
K_WARP = 15485863    # desplazamiento de sal para el warp del centro del ducado

# Colores de reino (el mapa en libro, los títulos y la capa de JourneyMap/Diorama). Geo.java tiene los mismos.
COLORES = ["#c0392b", "#2e5fa8", "#2e8b3a", "#7b3fa0", "#c9a227", "#d8d8d8", "#a0582c", "#138d90", "#b0306a", "#5b6f7c"]
SAL_COLOR = 5        # el color del reino se tira con #salColor = sal + 5 (ver SALES en build.py)
