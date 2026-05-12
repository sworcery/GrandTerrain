export interface WorldConfig {
  terrain: TerrainConfig;
  caves: CaveConfig;
  rivers: RiverConfig;
  climate: ClimateConfig;
  biomeThresholds: BiomeThresholdConfig;
  structures: StructureConfig;
}

export interface TerrainConfig {
  mountainHeightScale: number;
  continentalScale: number;
  erosionStrength: number;
  seaLevel: number;
  worldHeight: number;
}

export interface CaveConfig {
  caveDensity: number;
  caveFrequency: number;
  enableMegaCaverns: boolean;
  enableUndergroundRivers: boolean;
  cavernCenterY: number;
}

export interface RiverConfig {
  riverWidth: number;
  riverDepth: number;
}

export interface ClimateConfig {
  snowLineBase: number;
  biomeBlendWidth: number;
  climateBlendWidth: number;
}

export interface BiomeThresholdConfig {
  deepOceanOffset: number;
  coastalOffset: number;
  lowlandOffset: number;
}

export interface StructureConfig {
  enableCastles: boolean;
  enableRuins: boolean;
  enableDungeons: boolean;
  enableWatchtowers: boolean;
}

export interface ConfigField {
  key: string;
  label: string;
  type: "float" | "int" | "boolean";
  default: number | boolean;
  min?: number;
  max?: number;
  step?: number;
  tooltip: string;
}

export const TERRAIN_FIELDS: ConfigField[] = [
  { key: "mountainHeightScale", label: "Mountain Height Scale", type: "float", default: 5.0, min: 1.0, max: 10.0, step: 0.5, tooltip: "How tall mountains are relative to vanilla. 5.0 = 5x vanilla height." },
  { key: "continentalScale", label: "Continental Scale", type: "float", default: 1.0, min: 0.5, max: 3.0, step: 0.1, tooltip: "Scale of continental landmasses. Higher = larger continents." },
  { key: "erosionStrength", label: "Erosion Strength", type: "float", default: 1.0, min: 0.0, max: 3.0, step: 0.1, tooltip: "How much erosion carves valleys. 0 = no erosion." },
  { key: "seaLevel", label: "Sea Level", type: "int", default: 128, min: 60, max: 200, step: 1, tooltip: "Height of the ocean surface." },
  { key: "worldHeight", label: "World Height", type: "int", default: 1024, min: 512, max: 2048, step: 64, tooltip: "Total world height in blocks." },
];

export const CAVE_FIELDS: ConfigField[] = [
  { key: "caveDensity", label: "Cave Density", type: "float", default: 1.0, min: 0.0, max: 3.0, step: 0.1, tooltip: "Overall density of cave generation." },
  { key: "caveFrequency", label: "Cave Frequency", type: "float", default: 1.0, min: 0.5, max: 3.0, step: 0.1, tooltip: "How frequently cave tunnels appear." },
  { key: "enableMegaCaverns", label: "Mega Caverns", type: "boolean", default: true, tooltip: "Generate huge underground caverns." },
  { key: "enableUndergroundRivers", label: "Underground Rivers", type: "boolean", default: true, tooltip: "Generate flowing rivers underground." },
  { key: "cavernCenterY", label: "Cavern Center Y", type: "int", default: -100, min: -300, max: 0, step: 10, tooltip: "Y level where mega caverns center. Lower = deeper caverns." },
];

export const RIVER_FIELDS: ConfigField[] = [
  { key: "riverWidth", label: "River Width", type: "float", default: 1.0, min: 0.5, max: 3.0, step: 0.1, tooltip: "How wide surface rivers are." },
  { key: "riverDepth", label: "River Depth", type: "float", default: 1.0, min: 0.5, max: 3.0, step: 0.1, tooltip: "How deep rivers carve into terrain." },
];

export const CLIMATE_FIELDS: ConfigField[] = [
  { key: "snowLineBase", label: "Snow Line", type: "int", default: 400, min: 200, max: 600, step: 10, tooltip: "Altitude where snow starts." },
  { key: "biomeBlendWidth", label: "Biome Blend Width", type: "int", default: 4, min: 0, max: 12, step: 1, tooltip: "Altitude jitter in blocks at biome boundaries. 0 = sharp edges." },
  { key: "climateBlendWidth", label: "Climate Blend Width", type: "float", default: 0.05, min: 0.0, max: 0.2, step: 0.01, tooltip: "Noise jitter at climate zone boundaries. Higher = smoother transitions." },
];

export const BIOME_THRESHOLD_FIELDS: ConfigField[] = [
  { key: "deepOceanOffset", label: "Deep Ocean Offset", type: "int", default: -60, min: -200, max: -10, step: 5, tooltip: "Y offset from sea level where deep ocean begins. More negative = deeper ocean floor." },
  { key: "coastalOffset", label: "Coastal Offset", type: "int", default: -20, min: -100, max: 0, step: 5, tooltip: "Y offset from sea level where coastal zone begins." },
  { key: "lowlandOffset", label: "Lowland Offset", type: "int", default: 10, min: 0, max: 100, step: 5, tooltip: "Y offset above sea level where lowland biomes begin." },
];

export const STRUCTURE_FIELDS: ConfigField[] = [
  { key: "enableCastles", label: "Castles", type: "boolean", default: true, tooltip: "Generate castle structures in mountain biomes." },
  { key: "enableRuins", label: "Ruins", type: "boolean", default: true, tooltip: "Generate ancient ruins across the landscape." },
  { key: "enableDungeons", label: "Dungeons", type: "boolean", default: true, tooltip: "Generate underground dungeons with loot." },
  { key: "enableWatchtowers", label: "Watchtowers", type: "boolean", default: true, tooltip: "Generate watchtower structures on hilltops." },
];

export const DEFAULT_CONFIG: WorldConfig = {
  terrain: { mountainHeightScale: 5.0, continentalScale: 1.0, erosionStrength: 1.0, seaLevel: 128, worldHeight: 1024 },
  caves: { caveDensity: 1.0, caveFrequency: 1.0, enableMegaCaverns: true, enableUndergroundRivers: true, cavernCenterY: -100 },
  rivers: { riverWidth: 1.0, riverDepth: 1.0 },
  climate: { snowLineBase: 400, biomeBlendWidth: 4, climateBlendWidth: 0.05 },
  biomeThresholds: { deepOceanOffset: -60, coastalOffset: -20, lowlandOffset: 10 },
  structures: { enableCastles: true, enableRuins: true, enableDungeons: true, enableWatchtowers: true },
};
