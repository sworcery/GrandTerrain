import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "GrandTerrain — Custom Minecraft Worlds",
  description:
    "Design and order handcrafted Minecraft worlds with epic terrain, custom caves, and unique structures.",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className="bg-zinc-950 text-zinc-100 antialiased">
        <nav className="border-b border-zinc-800 px-6 py-4">
          <div className="mx-auto flex max-w-6xl items-center justify-between">
            <a href="/" className="text-xl font-bold tracking-tight">
              Grand<span className="text-emerald-400">Terrain</span>
            </a>
            <div className="flex gap-6 text-sm text-zinc-400">
              <a href="/gallery" className="hover:text-zinc-100">
                Gallery
              </a>
              <a href="/configure" className="hover:text-zinc-100">
                Build a World
              </a>
            </div>
          </div>
        </nav>
        <main>{children}</main>
      </body>
    </html>
  );
}
