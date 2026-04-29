package com.grandterrain.worldgen.noise;

public class ClimateNoise {

    private final FastNoiseLite temperatureNoise;
    private final FastNoiseLite humidityNoise;

    public ClimateNoise(long seed) {
        temperatureNoise = new FastNoiseLite((int) (seed ^ 0xC11A7E00L));
        temperatureNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        temperatureNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        temperatureNoise.SetFractalOctaves(4);
        temperatureNoise.SetFractalLacunarity(2.0f);
        temperatureNoise.SetFractalGain(0.5f);
        temperatureNoise.SetFrequency(1.0f / 4000.0f);

        humidityNoise = new FastNoiseLite((int) (seed ^ 0xBE7A8001L));
        humidityNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        humidityNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        humidityNoise.SetFractalOctaves(4);
        humidityNoise.SetFractalLacunarity(2.0f);
        humidityNoise.SetFractalGain(0.5f);
        humidityNoise.SetFrequency(1.0f / 3500.0f);
    }

    public double temperature(double x, double z) {
        return temperatureNoise.GetNoise(
                ContinentalNoise.wrapToFloat(x),
                ContinentalNoise.wrapToFloat(z));
    }

    public double humidity(double x, double z) {
        return humidityNoise.GetNoise(
                ContinentalNoise.wrapToFloat(x),
                ContinentalNoise.wrapToFloat(z));
    }
}
