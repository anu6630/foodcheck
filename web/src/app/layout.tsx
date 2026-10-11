import type { Metadata } from "next";
import { Figtree, Newsreader } from "next/font/google";
import { BRAND, SITE_URL } from "@/lib/config";
import "./globals.css";

const display = Newsreader({
  subsets: ["latin"],
  variable: "--font-display",
  weight: ["500", "600", "700"],
});

const body = Figtree({
  subsets: ["latin"],
  variable: "--font-body",
  weight: ["400", "500", "600", "700"],
});

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: {
    default: `${BRAND} — Scan labels. Know the ingredients.`,
    template: `%s · ${BRAND}`,
  },
  description:
    "Scan food labels and check ingredients for banned and restricted additives. Know what’s in a pack before you buy.",
  openGraph: {
    title: `${BRAND} — Scan labels. Know the ingredients.`,
    description:
      "Scan food labels and check ingredients for banned and restricted additives.",
    url: SITE_URL,
    siteName: BRAND,
    images: [{ url: "/og.png", width: 1024, height: 1024, alt: BRAND }],
    type: "website",
  },
  twitter: {
    card: "summary",
    title: BRAND,
    description:
      "Scan food labels and check ingredients for banned and restricted additives.",
    images: ["/og.png"],
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className={`${display.variable} ${body.variable} h-full`}>
      <body className="min-h-full flex flex-col antialiased">{children}</body>
    </html>
  );
}
