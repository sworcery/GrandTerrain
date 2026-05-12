export default function Home() {
  return (
    <div className="mx-auto max-w-4xl px-6 py-24 text-center">
      <h1 className="text-5xl font-bold tracking-tight">
        Your World, <span className="text-emerald-400">Your Rules</span>
      </h1>
      <p className="mx-auto mt-6 max-w-2xl text-lg text-zinc-400">
        Design a custom Minecraft world with towering mountains, vast cave
        networks, unique structures, and hand-tuned biomes. Every world is
        inspected before delivery.
      </p>
      <div className="mt-10 flex justify-center gap-4">
        <a
          href="/configure"
          className="rounded-lg bg-emerald-600 px-6 py-3 font-medium text-white hover:bg-emerald-500"
        >
          Build Your World
        </a>
        <a
          href="/gallery"
          className="rounded-lg border border-zinc-700 px-6 py-3 font-medium text-zinc-300 hover:border-zinc-500"
        >
          View Gallery
        </a>
      </div>

      <div className="mt-24 grid gap-8 text-left md:grid-cols-3">
        <div className="rounded-xl border border-zinc-800 p-6">
          <h3 className="text-lg font-semibold">Configure Everything</h3>
          <p className="mt-2 text-sm text-zinc-400">
            23 parameters across terrain, caves, rivers, climate, biome
            thresholds, and structures. Full control over your world.
          </p>
        </div>
        <div className="rounded-xl border border-zinc-800 p-6">
          <h3 className="text-lg font-semibold">White-Glove Inspection</h3>
          <p className="mt-2 text-sm text-zinc-400">
            Every world is manually reviewed before delivery to ensure quality.
            No auto-generated junk.
          </p>
        </div>
        <div className="rounded-xl border border-zinc-800 p-6">
          <h3 className="text-lg font-semibold">Ready to Play</h3>
          <p className="mt-2 text-sm text-zinc-400">
            Download your world files and drop them into your Minecraft saves
            folder. Works with Java Edition.
          </p>
        </div>
      </div>
    </div>
  );
}
