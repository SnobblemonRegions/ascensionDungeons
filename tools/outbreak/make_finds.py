#!/usr/bin/env python3
"""Writes dungeons/<type>/finds.json: the Pokemon a type dungeon's chests can hold, by tier.

Reads Cobblemon's own species data from its jar (its labels mark starters, pseudo-legendaries ("powerhouse", on the final
form only, so each line is walked back to its first stage), legendaries, mythicals and the restricted box legendaries) and each dungeon's layout.json (its ordinary pools). Only species Cobblemon has
implemented, and only first stages, are used. Where a type has no starter or no pseudo-legendary, the hand-picked lists below
fill the tier. Restricted legendaries (Mewtwo, Kyogre and the like) are left out.

Usage: python3 make_finds.py <cobblemon jar> <resources dungeons dir>
Edit the tables and re-run; do not hand-edit the output.
"""
import json, os, sys, zipfile

TYPES = ["normal", "water", "grass", "electric", "ice", "fighting", "poison", "ground", "flying", "psychic", "bug", "rock",
         "ghost", "dragon", "dark", "steel", "fairy"]

# Types with no starters of their own: a few partner-like first stages that feel like a find.
PARTNERS = {
    "normal": ["eevee", "munchlax"], "electric": ["pichu", "pawmi"], "ice": ["snorunt", "swinub"], "fighting": ["riolu", "tyrogue"],
    "poison": ["toxel", "shroodle"], "ground": ["cubone", "sandile"], "flying": ["rookidee", "pikipek"], "psychic": ["ralts", "abra"],
    "bug": ["scyther", "nymble"], "rock": ["rockruff", "nacli"], "ghost": ["gastly", "sinistea"], "dragon": ["axew", "swablu"],
    "dark": ["zorua", "sneasel"], "steel": ["aron", "tinkatink"], "fairy": ["togepi", "flabebe"],
}
# Ordinary first stages of the type, added to a standard list the dungeon's own pools leave thin (Dragon's pools are mostly
# pseudo-legendary lines, which belong to a higher tier).
EXTRA_STANDARD = {
    "dragon": ["druddigon", "drampa", "cyclizar", "tatsugiri", "turtonator", "bagon"],
    "ice": ["spheal", "snom", "cubchoo", "bergmite", "smoochum"],
}

# Types with no pseudo-legendary of their own: a hand-picked signature rarity.
SIGNATURES = {
    "normal": ["porygon"], "water": ["lapras", "feebas"], "grass": ["tropius"], "electric": ["rotom"], "poison": ["skrelp", "salandit"],
    "ice": ["lapras"], "flying": ["noibat"], "bug": ["larvesta"], "fairy": ["mimikyu", "hatenna"], "fighting": ["hawlucha"],
}

def main(jar, dungeons):
    z = zipfile.ZipFile(jar)
    species = {}
    pre_evolved = set()
    for n in z.namelist():
        if not (n.startswith("data/cobblemon/species/") and n.endswith(".json")): continue
        d = json.loads(z.read(n))
        if not d.get("implemented"): continue
        key = d["name"].lower().replace(" ", "").replace("-", "").replace(".", "").replace("'", "").replace(":", "")
        species[key] = d
        for e in d.get("evolutions", []) or []:
            r = str(e.get("result", "")).split(" ")[0].lower()
            if r: pre_evolved.add(r)
    def types(d): return {str(d.get("primaryType", "")).lower(), str(d.get("secondaryType", "")).lower()}
    parent = {}
    for k, d in species.items():
        for e in d.get("evolutions", []) or []:
            r = str(e.get("result", "")).split(" ")[0].lower()
            if r: parent.setdefault(r, k)
    def line(k):
        """A species and everything it evolves from, first stage last."""
        out = [k]
        while out[-1] in parent and parent[out[-1]] not in out: out.append(parent[out[-1]])
        return out
    def labelled(label, t, first_stage=True):
        """Species carrying the label (Cobblemon puts some, like "powerhouse", only on the final form), as the first stage of
        their line, where any stage of the line has the type."""
        found = set()
        for k, d in species.items():
            if label not in d.get("labels", []): continue
            chain = line(k)
            if not any(t in types(species[c]) for c in chain if c in species): continue
            found.add(chain[-1] if first_stage else k)
        return sorted(f for f in found if f in species)
    def known(names): return [n for n in names if n in species]

    for t in TYPES:
        layout = json.load(open(os.path.join(dungeons, t, "layout.json")))
        pools = layout["pokemon"]["pools"]
        standard = sorted({e["species"].lower() for p in ("wild_low", "wild_mid") for e in pools.get(p, []) if e["species"].lower() in species})
        starters = labelled("starter", t) or known(PARTNERS.get(t, []))
        pseudo = labelled("powerhouse", t) or known(SIGNATURES.get(t, []))
        # A line that belongs to a higher tier is never a standard find, even where the dungeon's pools hold it (Dratini in Dragon).
        special = {line(k)[-1] for k in starters + pseudo}
        standard = sorted(set(standard) | set(known(EXTRA_STANDARD.get(t, []))))
        standard = [k for k in standard if line(k)[-1] not in special]
        legendary = [k for k in labelled("legendary", t, False) if "restricted" not in species[k].get("labels", []) and "mythical" not in species[k].get("labels", [])]
        mythical = [k for k in labelled("mythical", t, False) if "restricted" not in species[k].get("labels", [])]
        low = [e for p in ("wild_low", "wild_mid") for e in pools.get(p, [])]
        out = {
            "_comment": "Generated by tools/outbreak/make_finds.py from Cobblemon's species data and this dungeon's pools. Do not hand-edit.",
            "standard": standard, "starter": starters, "pseudo": pseudo, "legendary": legendary, "mythical": mythical,
            "standardLevels": [min(e["min"] for e in low), max(e["max"] for e in low)],
            "starterLevel": 10, "pseudoLevel": 10, "legendaryLevel": 40, "mythicalLevel": 40,
        }
        with open(os.path.join(dungeons, t, "finds.json"), "w") as f:
            json.dump(out, f, indent=2)
            f.write("\n")
        print(f"{t:9s} standard {len(standard):2d}  starter {starters}  pseudo {pseudo}  legendary {legendary}  mythical {mythical}")

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
