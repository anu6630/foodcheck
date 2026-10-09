import { neon } from "@neondatabase/serverless";

const connectionString = process.env.DATABASE_URL;

if (!connectionString) {
  console.warn("WARNING: DATABASE_URL is not set in environment.");
}

const sql = connectionString ? neon(connectionString) : null;

/** Scripts call pool.end(); the HTTP driver has nothing to close. */
export const pool = {
  async end() {
    /* no-op */
  },
};

export async function query<T = Record<string, unknown>>(
  text: string,
  params?: unknown[]
): Promise<{ rows: T[]; rowCount: number | null }> {
  if (!sql) throw new Error("DATABASE_URL is not set");
  const rows = (await sql.query(text, params ?? [])) as T[];
  return { rows, rowCount: rows.length };
}
