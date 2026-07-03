from pydantic import BaseModel, ConfigDict, Field


def _to_camel(s: str) -> str:
    parts = s.split("_")
    return parts[0] + "".join(p.capitalize() for p in parts[1:])


class _CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=_to_camel, populate_by_name=True)


class TerrainConfig(_CamelModel):
    mountain_height_scale: float = Field(5.0, ge=1.0, le=10.0)
    continental_scale: float = Field(1.0, ge=0.5, le=3.0)
    erosion_strength: float = Field(1.0, ge=0.0, le=3.0)
    sea_level: int = Field(128, ge=60, le=200)
    world_height: int = Field(1024, ge=512, le=2048)


class CaveConfig(_CamelModel):
    cave_density: float = Field(1.0, ge=0.0, le=3.0)
    cave_frequency: float = Field(1.0, ge=0.5, le=3.0)
    enable_mega_caverns: bool = True
    enable_underground_rivers: bool = True
    cavern_center_y: int = Field(-100, ge=-300, le=0)


class RiverConfig(_CamelModel):
    river_width: float = Field(1.0, ge=0.5, le=3.0)
    river_depth: float = Field(1.0, ge=0.5, le=3.0)


class ClimateConfig(_CamelModel):
    snow_line_base: int = Field(400, ge=200, le=600)
    biome_blend_width: int = Field(4, ge=0, le=12)
    climate_blend_width: float = Field(0.05, ge=0.0, le=0.2)


class BiomeThresholdConfig(_CamelModel):
    deep_ocean_offset: int = Field(-60, ge=-200, le=-10)
    coastal_offset: int = Field(-20, ge=-100, le=0)
    lowland_offset: int = Field(10, ge=0, le=100)


class StructureConfig(_CamelModel):
    enable_castles: bool = True
    enable_ruins: bool = True
    enable_dungeons: bool = True
    enable_watchtowers: bool = True


class WorldConfig(_CamelModel):
    terrain: TerrainConfig = TerrainConfig()
    caves: CaveConfig = CaveConfig()
    rivers: RiverConfig = RiverConfig()
    climate: ClimateConfig = ClimateConfig()
    biome_thresholds: BiomeThresholdConfig = BiomeThresholdConfig()
    structures: StructureConfig = StructureConfig()

    def to_properties(self) -> str:
        lines = [
            f"terrain.mountainHeightScale={self.terrain.mountain_height_scale}",
            f"terrain.continentalScale={self.terrain.continental_scale}",
            f"terrain.erosionStrength={self.terrain.erosion_strength}",
            f"terrain.seaLevel={self.terrain.sea_level}",
            f"terrain.worldMinY=-256",
            f"terrain.worldHeight={self.terrain.world_height}",
            f"caves.density={self.caves.cave_density}",
            f"caves.frequency={self.caves.cave_frequency}",
            f"caves.enableMegaCaverns={str(self.caves.enable_mega_caverns).lower()}",
            f"caves.enableUndergroundRivers={str(self.caves.enable_underground_rivers).lower()}",
            f"caves.cavernCenterY={self.caves.cavern_center_y}",
            f"rivers.width={self.rivers.river_width}",
            f"rivers.depth={self.rivers.river_depth}",
            f"snow.lineBase={self.climate.snow_line_base}",
            f"snow.biomeBlendWidth={self.climate.biome_blend_width}",
            f"snow.climateBlendWidth={self.climate.climate_blend_width}",
            f"biomes.deepOceanOffset={self.biome_thresholds.deep_ocean_offset}",
            f"biomes.coastalOffset={self.biome_thresholds.coastal_offset}",
            f"biomes.lowlandOffset={self.biome_thresholds.lowland_offset}",
            f"structures.enableCastles={str(self.structures.enable_castles).lower()}",
            f"structures.enableRuins={str(self.structures.enable_ruins).lower()}",
            f"structures.enableDungeons={str(self.structures.enable_dungeons).lower()}",
            f"structures.enableWatchtowers={str(self.structures.enable_watchtowers).lower()}",
        ]
        return "\n".join(lines) + "\n"


class GenerateRequest(_CamelModel):
    config: WorldConfig


class JobResponse(_CamelModel):
    job_id: str
    status: str
    created_at: str
