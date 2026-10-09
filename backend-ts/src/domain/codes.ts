/** Open-ended numbering systems. Add new values without a schema change. */
export const CODE_SYSTEMS = ["E", "INS", "CAS", "FDC", "JECFA"] as const;
export type CodeSystem = (typeof CODE_SYSTEMS)[number] | (string & {});

export function normalizeCodeValue(raw: string): string {
  return raw
    .trim()
    .replace(/^(E|INS|FD&C|FDC|CAS)[\s:-]*/i, "")
    .replace(/\s+/g, "")
    .toUpperCase();
}

export function displayCode(system: string, code: string): string {
  const s = system.trim().toUpperCase();
  const c = code.trim();
  if (!c) return s;
  if (s === "E") return c.toUpperCase().startsWith("E") ? c.toUpperCase() : `E${c}`;
  if (s === "INS") return `INS ${c}`;
  if (s === "CAS") return `CAS ${c}`;
  if (s === "FDC") return `FD&C ${c}`;
  return `${s} ${c}`;
}

export function codesLabel(
  codes: Array<{ system: string; code: string; display?: string }>
): string | null {
  if (!codes.length) return null;
  const order = ["INS", "E", "FDC", "CAS"];
  const sorted = [...codes].sort((a, b) => {
    const ia = order.indexOf(a.system.toUpperCase());
    const ib = order.indexOf(b.system.toUpperCase());
    return (ia === -1 ? 99 : ia) - (ib === -1 ? 99 : ib);
  });
  return sorted.map((c) => c.display || displayCode(c.system, c.code)).join(" · ");
}
