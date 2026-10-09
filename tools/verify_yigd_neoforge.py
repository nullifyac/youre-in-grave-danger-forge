"""Verify the 26.1.2 release JAR using only Python's standard library."""
import argparse
import json
import struct
import tomllib
import zipfile
from pathlib import Path


def verify(path: Path) -> None:
    with zipfile.ZipFile(path) as jar:
        names = set(jar.namelist())
        assert "LICENSE" in names, "Missing upstream license"
        assert not any("/gametest/" in name or "yigd_game_tests" in name for name in names), "Test fixtures entered the release"
        metadata = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode("utf-8"))
        assert metadata["mods"][0]["modId"] == "yigd"
        assert metadata["mods"][0]["version"] == "2.0.22"
        dependencies = {entry["modId"]: entry for entry in metadata["dependencies"]["yigd"]}
        assert dependencies["minecraft"]["versionRange"] == "[26.1.2,26.1.3)"
        assert dependencies["cloth_config"]["type"] == "required"
        assert dependencies["curios"]["type"] == "optional"
        assert dependencies["travelersbackpack"]["versionRange"] == "[11.2.8,12)"
        assert "accessories" not in dependencies
        assert struct.unpack(">H", jar.read("com/b1n_ry/yigd/Yigd.class")[6:8])[0] == 69, "Wrong Java target"
        mixins = json.loads(jar.read("yigd.mixins.json"))
        for group in ("mixins", "client", "server"):
            for name in mixins.get(group, []):
                assert (mixins["package"].replace(".", "/") + "/" + name + ".class") in names, f"Missing mixin {name}"
        for name in names:
            if name.endswith(".json"):
                json.loads(jar.read(name))
            assert "/tags/blocks/" not in name and "/tags/items/" not in name, f"Legacy tag path {name}"
            assert "/recipes/" not in name, f"Legacy recipe path {name}"
        for tag in ("dragon_immune", "wither_immune"):
            data = json.loads(jar.read(f"data/minecraft/tags/block/{tag}.json"))
            assert "yigd:grave" in data["values"], f"Missing {tag} protection"
        for item in ("grave", "grave_key", "death_scroll"):
            definition = json.loads(jar.read(f"assets/yigd/items/{item}.json"))
            assert definition["model"]["model"] == f"yigd:item/{item}"
        assert "data/yigd/recipe/grave.json" in names, "Missing grave recipe"
        print(f"PASS: {path.name}; metadata, Java 25 bytecode, resources, license and fixture exclusion")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", type=Path)
    verify(parser.parse_args().jar)
