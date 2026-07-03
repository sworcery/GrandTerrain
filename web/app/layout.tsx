import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "GrandTerrain — Custom Minecraft World Generator",
  description:
    "Configure and download massive custom Minecraft worlds with epic terrain, custom caves, and unique structures.",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className="flex min-h-screen flex-col bg-zinc-950 text-zinc-100 antialiased">
        <nav className="border-b border-zinc-800 px-6 py-4">
          <div className="mx-auto flex max-w-6xl items-center justify-between">
            <a href="/" className="text-xl font-bold tracking-tight">
              Grand<span className="text-emerald-400">Terrain</span>
            </a>
            <div className="flex gap-6 text-sm text-zinc-400">
              <a href="/configure" className="hover:text-zinc-100">
                Build a World
              </a>
              <a
                href="https://github.com/sworcery/GrandTerrain"
                className="hover:text-zinc-100"
              >
                GitHub
              </a>
            </div>
          </div>
        </nav>
        <main className="flex-1">{children}</main>
        <footer className="border-t border-zinc-800 px-6 py-6">
          <div className="mx-auto flex max-w-6xl items-center justify-between text-xs text-zinc-600">
            <span>GrandTerrain &middot; Custom Minecraft Worlds</span>
            <span>
              <a href="https://github.com/sworcery/GrandTerrain" className="hover:text-zinc-400">
                Open source &middot; MIT
              </a>
            </span>
          </div>
        </footer>
      </body>
    </html>
  );
}
