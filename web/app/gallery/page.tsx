export default function GalleryPage() {
  return (
    <div className="mx-auto max-w-5xl px-6 py-12">
      <h1 className="text-3xl font-bold">Gallery</h1>
      <p className="mt-2 text-zinc-400">
        Example worlds built with GrandTerrain. Screenshots coming soon.
      </p>

      <div className="mt-10 grid gap-6 md:grid-cols-2 lg:grid-cols-3">
        {[
          { name: "Alpine Expanse", desc: "Towering peaks with pine forests and deep valleys." },
          { name: "Volcanic Wastes", desc: "Magma deposits, ash particles, and dramatic terrain." },
          { name: "Coastal Paradise", desc: "Sweeping cliffs, copper veins, and ocean views." },
          { name: "Underground Empire", desc: "Mega caverns, underground rivers, and deep dungeons." },
          { name: "Frozen Tundra", desc: "Snow-capped peaks, ice-covered plains, and sparse trees." },
          { name: "Dense Wilds", desc: "Dark forests, swamps with clay deposits, and ancient ruins." },
        ].map((world) => (
          <div
            key={world.name}
            className="flex aspect-video flex-col justify-end rounded-xl border border-zinc-800 bg-zinc-900 p-4"
          >
            <h3 className="font-semibold">{world.name}</h3>
            <p className="mt-1 text-sm text-zinc-400">{world.desc}</p>
          </div>
        ))}
      </div>
    </div>
  );
}
