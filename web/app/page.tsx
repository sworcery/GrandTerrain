export default function Home() {
  return (
    <div className="mx-auto max-w-4xl px-6 py-24 text-center">
      <h1 className="text-5xl font-bold tracking-tight">
        Your World, <span className="text-emerald-400">Your Rules</span>
      </h1>
      <p className="mx-auto mt-6 max-w-2xl text-lg text-zinc-400">
        Design a custom Minecraft world with towering mountains, vast cave
        networks, unique structures, and hand-tuned biomes — then generate a
        world file and drop it straight into your saves folder.
      </p>
      <div className="mt-10 flex justify-center gap-4">
        <a
          href="/configure"
          className="rounded-lg bg-emerald-600 px-6 py-3 font-medium text-white hover:bg-emerald-500"
        >
          Build Your World
        </a>
        <a
          href="https://github.com/sworcery/GrandTerrain"
          className="rounded-lg border border-zinc-700 px-6 py-3 font-medium text-zinc-300 hover:border-zinc-500"
        >
          View on GitHub
        </a>
      </div>

      <div className="mt-24 grid gap-8 text-left md:grid-cols-3">
        <div className="rounded-xl border border-zinc-800 p-6">
          <div className="mb-3 text-2xl">1</div>
          <h3 className="text-lg font-semibold">Configure Everything</h3>
          <p className="mt-2 text-sm text-zinc-400">
            22 parameters across terrain, caves, rivers, climate, biome
            thresholds, and structures. Start from a preset or dial in
            every value.
          </p>
        </div>
        <div className="rounded-xl border border-zinc-800 p-6">
          <div className="mb-3 text-2xl">2</div>
          <h3 className="text-lg font-semibold">Generate</h3>
          <p className="mt-2 text-sm text-zinc-400">
            Your settings are baked in and the world is generated with the
            GrandTerrain engine, then packaged into a downloadable file.
          </p>
        </div>
        <div className="rounded-xl border border-zinc-800 p-6">
          <div className="mb-3 text-2xl">3</div>
          <h3 className="text-lg font-semibold">Ready to Play</h3>
          <p className="mt-2 text-sm text-zinc-400">
            Drop the world into your Minecraft saves folder and load it with
            the mod installed. Works with Java Edition 26.1+.
          </p>
        </div>
      </div>

      <div className="mt-24">
        <h2 className="text-2xl font-bold">What Makes GrandTerrain Different</h2>
        <div className="mt-8 grid gap-6 text-left md:grid-cols-2">
          <div className="rounded-xl border border-zinc-800 p-5">
            <h3 className="font-semibold text-emerald-400">16 Custom Biomes</h3>
            <p className="mt-1 text-sm text-zinc-400">
              From deep ocean trenches to volcanic wastelands, each biome has
              unique spawns, vegetation, and terrain. Climate-driven selection
              ensures natural transitions.
            </p>
          </div>
          <div className="rounded-xl border border-zinc-800 p-5">
            <h3 className="font-semibold text-emerald-400">4 Cave Types</h3>
            <p className="mt-1 text-sm text-zinc-400">
              Cheese caves, spaghetti tunnels, mega caverns, and underground
              rivers — each independently configurable for density, depth,
              and frequency.
            </p>
          </div>
          <div className="rounded-xl border border-zinc-800 p-5">
            <h3 className="font-semibold text-emerald-400">Epic Structures</h3>
            <p className="mt-1 text-sm text-zinc-400">
              Castles with armories, multi-level dungeons with boss chambers,
              ancient ruins, and hilltop watchtowers — each with custom loot
              tables.
            </p>
          </div>
          <div className="rounded-xl border border-zinc-800 p-5">
            <h3 className="font-semibold text-emerald-400">Massive Scale</h3>
            <p className="mt-1 text-sm text-zinc-400">
              World heights up to 2048 blocks. Mountains that dwarf vanilla
              terrain. Deep ocean floors hundreds of blocks below sea level.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
