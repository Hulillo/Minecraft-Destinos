"""Simulador de 'Destinos: Reinos' con aritmética de scoreboard (int32 con desbordamiento,
división hacia abajo). Replica 1:1 las funciones geo/* del datapack para previsualizar el mapa."""
import os, sys
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "datapack"))
from constantes import S_DUCADO, S_REINO, WARP, D_FRONTERA, P_FRONTERA, P_SALVAJE, K_JZ, K_REINO, K_SALV, K_WARP

def i32(v):
    v &= 0xFFFFFFFF
    return v - (1 << 32) if v >= (1 << 31) else v

def fdiv(a, b):  # Mth.floorDiv
    return a // b

def pmod(a, b):  # Mth.positiveModulo (solo se usa con a >= 0)
    return a % b

def hash_(hx, hz, hs):
    h = i32(hx * 73856093)
    h = i32(h + i32(hz * 19349663))
    h = i32(h + hs)
    h = i32(h * h)
    h = i32(h + hs)
    h = i32(h * 1103515245)
    h = i32(h + 12345)
    h = fdiv(h, 65536)
    if h < 0:
        h = i32(h * -1)
    return h  # 0..32768

def voronoi(vx, vz, S, sal):
    cx, cz = fdiv(vx, S), fdiv(vz, S)
    cuarto, mitad = fdiv(S, 8), fdiv(S * 3, 4)
    best = 2147483647
    res = None
    for ox in (-1, 0, 1):
        for oz in (-1, 0, 1):
            hx, hz = cx + ox, cz + oz
            jx = cuarto + pmod(hash_(hx, hz, sal), mitad)
            jz = cuarto + pmod(hash_(hx, hz, i32(sal + K_JZ)), mitad)
            ctx, ctz = i32(hx * S + jx), i32(hz * S + jz)
            dx, dz = vx - ctx, vz - ctz
            d = i32(i32(dx * dx) + i32(dz * dz))
            if d < best:
                best, res = d, (hx, hz, ctx, ctz, d)
    return res


def territorio(x, z, sal):
    dcx, dcz, dctx, dctz, _ = voronoi(x, z, S_DUCADO, sal)
    wx = i32(dctx + pmod(hash_(dcx, dcz, i32(sal + K_WARP)), 2 * WARP) - WARP)
    wz = i32(dctz + pmod(hash_(dcx, dcz, i32(sal + K_WARP + K_JZ)), 2 * WARP) - WARP)
    kcx, kcz, _, _, dk = voronoi(wx, wz, S_REINO, i32(sal + K_REINO))
    tirada = pmod(hash_(dcx, dcz, i32(sal + K_SALV)), 100)
    salvaje = tirada < P_SALVAJE or (dk > D_FRONTERA * D_FRONTERA and tirada < P_FRONTERA)
    return (dcx, dcz), (kcx, kcz), salvaje

def color_de(key, sal):
    h = hash_(key[0], key[1], i32(sal + 31337))
    return ((h * 97) % 200 + 40, (h * 57) % 200 + 40, (h * 131) % 200 + 40)

def render(sal, radio=24000, px=600, out="mapa.png"):
    paso = 2 * radio // px
    img = Image.new("RGB", (px, px))
    grid = [[None] * px for _ in range(px)]
    for j in range(px):
        for i in range(px):
            x, z = -radio + i * paso, -radio + j * paso
            grid[j][i] = territorio(x, z, sal)
    for j in range(px):
        for i in range(px):
            d, k, w = grid[j][i]
            c = (70, 70, 70) if w else color_de(k, sal)
            borde_d = (i > 0 and grid[j][i - 1][0] != d) or (j > 0 and grid[j - 1][i][0] != d)
            borde_k = (i > 0 and grid[j][i - 1][1] != k) or (j > 0 and grid[j - 1][i][1] != k)
            if borde_k:
                c = (10, 10, 10)
            elif borde_d:
                c = tuple(int(v * 0.65) for v in c)
            img.putpixel((i, j), c)
    dr = ImageDraw.Draw(img)
    o = px // 2
    dr.ellipse((o - 4, o - 4, o + 4, o + 4), outline=(255, 255, 255), width=2)
    img.save(out)

if __name__ == "__main__":
    for sal in map(int, sys.argv[1:]):
        render(sal, out=f"mapa_{sal}.png")
        print("ok", sal, territorio(0, 0, sal))
