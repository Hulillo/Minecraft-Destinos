# Minecraft: Destinos

Modpack RPG de exploración y viaje para Fabric 1.21.11. Este repo tiene la parte hecha a mano: el mundo vivo con reinos, ducados y tierras salvajes que salen al azar en cada partida.

## Qué hay aquí

| Carpeta | Qué es |
|---|---|
| `datapack/` | Generador del datapack **Destinos: Reinos** y su resource pack de idiomas (español e inglés). `python datapack/build.py salida` crea los dos `.zip`. |
| `destinos-core/` | Mod **Destinos Core**. Lleva el datapack dentro y hace que las fronteras sigan el terreno: montañas, ríos y costas. |
| `herramientas/` | Simulador del mapa (`sim.py`) y pruebas que comprueban que el datapack y el mod calculan exactamente lo mismo. |

## Cómo funcionan las fronteras

1. Cada partida tiene una "sal" al azar. Con ella se colocan los centros de los ducados en una rejilla de 1500 bloques, y los reinos en una de 6000.
2. **Sin el mod**, el datapack reparte el mundo por cercanía al centro (Voronoi). Funciona, pero las fronteras ignoran el terreno.
3. **Con Destinos Core**, el mundo se divide en casillas de 64×64 bloques. El mod lee la altura y el bioma directamente del generador, sin generar chunks. Cada casilla se queda con el ducado al que se llega más barato andando. Subir montañas, cruzar ríos y atravesar el mar cuesta caro, así que las fronteras acaban en crestas, ríos y costas. Las islas lejanas quedan como tierra salvaje.

## Compilar

GitHub Actions lo compila solo en cada push. El `.jar` y los `.zip` salen en los artefactos de la ejecución.

Para compilarlo en local:

```sh
python datapack/build.py salida --mod destinos-core/src/main/generated
cd destinos-core && ./gradlew build
```

Comandos dentro del juego: `/destinoscore estado` y `/trigger destinos.donde`.
