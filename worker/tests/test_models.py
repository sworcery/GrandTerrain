import pytest
from pydantic import ValidationError

from src.models import GenerateRequest, WorldConfig

# The exact property keys ConfigManager.readProperties consumes in the mod.
# If this set drifts, generated worlds silently ignore part of the config.
MOD_PROPERTY_KEYS = {
    "terrain.mountainHeightScale",
    "terrain.continentalScale",
    "terrain.erosionStrength",
    "terrain.seaLevel",
    "terrain.worldMinY",
    "terrain.worldHeight",
    "caves.density",
    "caves.frequency",
    "caves.enableMegaCaverns",
    "caves.enableUndergroundRivers",
    "caves.cavernCenterY",
    "rivers.width",
    "rivers.depth",
    "snow.lineBase",
    "snow.biomeBlendWidth",
    "snow.climateBlendWidth",
    "biomes.deepOceanOffset",
    "biomes.coastalOffset",
    "biomes.lowlandOffset",
    "structures.enableCastles",
    "structures.enableRuins",
    "structures.enableDungeons",
    "structures.enableWatchtowers",
}


def test_to_properties_emits_exactly_the_keys_the_mod_reads():
    props = WorldConfig().to_properties()
    keys = {line.split("=", 1)[0] for line in props.strip().splitlines()}
    assert keys == MOD_PROPERTY_KEYS


def test_world_bounds_are_pinned():
    props = WorldConfig().to_properties()
    assert "terrain.worldMinY=-256" in props
    assert "terrain.worldHeight=1024" in props


def test_booleans_serialize_lowercase():
    props = WorldConfig().to_properties()
    assert "structures.enableCastles=true" in props
    assert "TRUE" not in props and "False" not in props


def test_camel_case_aliases_accepted():
    req = GenerateRequest.model_validate(
        {"config": {"terrain": {"mountainHeightScale": 7.5, "seaLevel": 90}}}
    )
    assert req.config.terrain.mountain_height_scale == 7.5
    assert req.config.terrain.sea_level == 90


def test_out_of_range_values_rejected():
    with pytest.raises(ValidationError):
        WorldConfig.model_validate({"caves": {"cavernCenterY": -300}})
    with pytest.raises(ValidationError):
        WorldConfig.model_validate({"terrain": {"seaLevel": 500}})
