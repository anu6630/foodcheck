import { serve } from "@hono/node-server";
import app from "./api.js";

const port = Number(process.env.PORT) || 8999;
const hostname = process.env.HOST || "0.0.0.0";

console.log(`FoodCheck API on http://${hostname}:${port}`);
console.log(`Health: http://localhost:${port}/health`);
console.log(`LAN tip: http://192.168.1.3:${port}`);

serve({
  fetch: app.fetch,
  port,
  hostname,
});
