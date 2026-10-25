"""Mini-intérprete de los comandos de scoreboard usados en geo/*. Ejecuta las funciones REALES del
datapack y compara el resultado con sim.py en miles de puntos. Si coinciden, el datapack calcula el
mismo mapa que se ve en las imágenes."""
import os, random, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import sim

DP = sys.argv[1]
FN = os.path.join(DP, "data/destinos/function")
S = {}  # (holder, objective) -> int

def i32(v): return sim.i32(v)
def get(h, o): return S[(h, o)]

def run(name):
    for line in open(os.path.join(FN, name + ".mcfunction"), encoding="utf-8"):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        cmd(line)

def cmd(line):
    t = line.split()
    if t[0] == "function":
        return run(t[1].split(":")[1])
    if t[:3] == ["scoreboard", "players", "set"]:
        S[(t[3], t[4])] = int(t[5]); return
    if t[:3] == ["scoreboard", "players", "add"]:
        S[(t[3], t[4])] = i32(S[(t[3], t[4])] + int(t[5])); return
    if t[:3] == ["scoreboard", "players", "operation"]:
        a, oa, o, b, ob = t[3], t[4], t[5], t[6], t[7]
        x, y = S.get((a, oa), 0), S[(b, ob)]
        if o == "=": r = y
        elif o == "+=": r = x + y
        elif o == "-=": r = x - y
        elif o == "*=": r = x * y
        elif o == "/=": r = x // y
        elif o == "%=": r = x % y  # positiveModulo
        else: raise ValueError(line)
        S[(a, oa)] = i32(r); return
    if t[0] == "execute":
        i = 1
        while t[i] != "run":
            if t[i] in ("if", "unless") and t[i + 1] == "score":
                h, o = t[i + 2], t[i + 3]
                if t[i + 4] == "matches":
                    rng = t[i + 5]
                    lo, _, hi = rng.partition("..")
                    v = S[(h, o)]
                    ok = (lo == "" or v >= int(lo)) and (hi == "" or v <= int(hi)) if ".." in rng else v == int(rng)
                    i += 6
                else:
                    opr, h2, o2 = t[i + 4], t[i + 5], t[i + 6]
                    v, w = S[(h, o)], S[(h2, o2)]
                    ok = {"<": v < w, ">": v > w, "=": v == w, "<=": v <= w, ">=": v >= w}[opr]
                    i += 7
                if t[i - 0] and (ok if t[i - (6 if t[i-6] in ('if','unless') else 7)] == "if" else not ok) is False:
                    return
            else:
                raise ValueError(line)
        return cmd(" ".join(t[i + 1:]))
    raise ValueError(line)

def cmd_exec_fixed(line):
    pass

# Reescritura más clara de execute (la de arriba es frágil): la sustituimos
def cmd2(line):
    t = line.split()
    if t[0] != "execute":
        return cmd_base(line)
    i = 1
    while t[i] != "run":
        kind = t[i]; assert kind in ("if", "unless") and t[i + 1] == "score", line
        h, o = t[i + 2], t[i + 3]
        if t[i + 4] == "matches":
            rng = t[i + 5]; v = S[(h, o)]
            if ".." in rng:
                lo, _, hi = rng.partition("..")
                ok = (lo == "" or v >= int(lo)) and (hi == "" or v <= int(hi))
            else:
                ok = v == int(rng)
            i += 6
        else:
            opr, h2, o2 = t[i + 4], t[i + 5], t[i + 6]
            v, w = S[(h, o)], S[(h2, o2)]
            ok = {"<": v < w, ">": v > w, "=": v == w, "<=": v <= w, ">=": v >= w}[opr]
            i += 7
        if (kind == "if") != ok:
            return
    return cmd2(" ".join(t[i + 1:]))

cmd_base = cmd
cmd = cmd2  # run() llama a cmd → ahora usa cmd2, que delega en cmd_base para lo demás

def cargar_constantes(sal):
    for line in open(os.path.join(FN, "init/load.mcfunction"), encoding="utf-8"):
        line = line.strip()
        if line.startswith("scoreboard players set") or line.startswith("scoreboard players operation") or line.startswith("scoreboard players add"):
            if "#sal" in line and "set" in line.split()[2]:
                continue
            if line.split()[3].startswith("#sal") and S.get(("#sal", "dr.const")) is None:
                S[("#sal", "dr.const")] = sal
            cmd(line)

def main():
    rnd = random.Random(1)
    total = 0
    for sal in (184467221, 99, 1999999999, 7):
        S.clear(); S[("#sal", "dr.const")] = sal
        cargar_constantes(sal)
        for _ in range(1500):
            x, z = rnd.randint(-60000, 60000), rnd.randint(-60000, 60000)
            S[("#vx", "dr.calc")], S[("#vz", "dr.calc")] = x, z
            run("geo/territorio")
            dp = ((get("#tdx", "dr.calc"), get("#tdz", "dr.calc")), (get("#tkx", "dr.calc"), get("#tkz", "dr.calc")),
                  get("#tw", "dr.calc") == 1)
            py = sim.territorio(x, z, sal)
            assert dp == py, (sal, x, z, dp, py)
            total += 1
    print("OK: el datapack y el simulador coinciden en", total, "puntos")

main()
