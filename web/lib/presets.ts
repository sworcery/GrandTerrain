import type { WorldConfig } from "./config-schema";
import { DEFAULT_CONFIG } from "./config-schema";

export interface Preset {
  id: string;
  name: string;
  description: string;
  config: WorldConfig;
}

export const PRESETS: Preset[] = [
  {
    id: "default",
    name: "Grand Default",
    description: "Balanced terrain with all features enabled. Towering mountains, deep caves, and flowing rivers.",
    config: structuredClone(DEFAULT_CONFIG),
  },
  {
    id: "alpine",
    name: "Alpine Expanse",
    description: "Extreme mountain heights with deep valleys and heavy erosion. Snow line lowered for dramatic peaks.",
    config: {
      terrain: { mountainHeightScale: 8.0, continentalScale: 1.5, erosionStrength: 2.0, seaLevel: 100, worldHeight: 1536 },
      caves: { caveDensity: 0.8, caveFrequency: 1.0, enableMegaCaverns: true, enableUndergroundRivers: true, cavernCenterY: -150 },
      rivers: { riverWidth: 1.5, riverDepth: 2.0 },
      climate: { snowLineBase: 300, biomeBlendWidth: 6, climateBlendWidth: 0.08 },
      biomeThresholds: { deepOceanOffset: -80, coastalOffset: -30, lowlandOffset: 20 },
      structures: { enableCastles: true, enableRuins: true, enableDungeons: true, enableWatchtowers: true },
    },
  },
  {
    id: "volcanic",
    name: "Volcanic Wastes",
    description: "Low sea level exposing vast terrain, minimal erosion, deep caverns. A harsh landscape of magma and stone.",
    config: {
      terrain: { mountainHeightScale: 4.0, continentalScale: 2.0, erosionStrength: 0.3, seaLevel: 80, worldHeight: 1024 },
      caves: { caveDensity: 1.5, caveFrequency: 1.2, enableMegaCaverns: true, enableUndergroundRivers: false, cavernCenterY: -200 },
      rivers: { riverWidth: 0.5, riverDepth: 0.5 },
      climate: { snowLineBase: 500, biomeBlendWidth: 2, climateBlendWidth: 0.03 },
      biomeThresholds: { deepOceanOffset: -40, coastalOffset: -15, lowlandOffset: 5 },
      structures: { enableCastles: false, enableRuins: true, enableDungeons: true, enableWatchtowers: false },
    },
  },
  {
    id: "coastal",
    name: "Coastal Paradise",
    description: "High sea level with sweeping coastal cliffs and wide rivers. Gentle terrain with lush biome blending.",
    config: {
      terrain: { mountainHeightScale: 3.0, continentalScale: 0.8, erosionStrength: 1.5, seaLevel: 160, worldHeight: 1024 },
      caves: { caveDensity: 0.7, caveFrequency: 0.8, enableMegaCaverns: false, enableUndergroundRivers: true, cavernCenterY: -80 },
      rivers: { riverWidth: 2.5, riverDepth: 1.5 },
      climate: { snowLineBase: 450, biomeBlendWidth: 8, climateBlendWidth: 0.1 },
      biomeThresholds: { deepOceanOffset: -100, coastalOffset: -40, lowlandOffset: 15 },
      structures: { enableCastles: true, enableRuins: true, enableDungeons: false, enableWatchtowers: true },
    },
  },
  {
    id: "underground",
    name: "Underground Empire",
    description: "Maximum cave density and frequency. Mega caverns and underground rivers dominate the subsurface.",
    config: {
      terrain: { mountainHeightScale: 5.0, continentalScale: 1.0, erosionStrength: 1.0, seaLevel: 128, worldHeight: 1024 },
      caves: { caveDensity: 2.5, caveFrequency: 2.0, enableMegaCaverns: true, enableUndergroundRivers: true, cavernCenterY: -60 },
      rivers: { riverWidth: 1.0, riverDepth: 1.0 },
      climate: { snowLineBase: 400, biomeBlendWidth: 4, climateBlendWidth: 0.05 },
      biomeThresholds: { deepOceanOffset: -60, coastalOffset: -20, lowlandOffset: 10 },
      structures: { enableCastles: true, enableRuins: true, enableDungeons: true, enableWatchtowers: true },
    },
  },
  {
    id: "tundra",
    name: "Frozen Tundra",
    description: "Low snow line blankets most terrain in white. Sparse vegetation, frozen rivers, and isolated watchtowers.",
    config: {
      terrain: { mountainHeightScale: 6.0, continentalScale: 1.2, erosionStrength: 0.8, seaLevel: 110, worldHeight: 1024 },
      caves: { caveDensity: 0.5, caveFrequency: 0.7, enableMegaCaverns: true, enableUndergroundRivers: false, cavernCenterY: -120 },
      rivers: { riverWidth: 0.8, riverDepth: 0.8 },
      climate: { snowLineBase: 200, biomeBlendWidth: 10, climateBlendWidth: 0.15 },
      biomeThresholds: { deepOceanOffset: -50, coastalOffset: -15, lowlandOffset: 8 },
      structures: { enableCastles: false, enableRuins: true, enableDungeons: true, enableWatchtowers: true },
    },
  },
];
