import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Self-contained server bundle for the Docker image (docker-compose.yml).
  output: "standalone",
};

export default nextConfig;
