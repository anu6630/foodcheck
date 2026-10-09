export const TRACKED_COUNTRIES = ["IN", "EU", "US", "AU", "NZ", "GB", "CA", "KR", "JP"] as const;

export const COUNTRY_LABELS: Record<string, string> = {
  IN: "India",
  EU: "European Union",
  US: "United States",
  AU: "Australia",
  NZ: "New Zealand",
  GB: "United Kingdom",
  CA: "Canada",
  KR: "South Korea",
  JP: "Japan",
};

export function countryLabel(code: string): string {
  return COUNTRY_LABELS[code] || code;
}
