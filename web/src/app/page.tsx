import Image from "next/image";
import Link from "next/link";
import { SiteFooter, SiteHeader } from "@/components/SiteChrome";
import { BRAND, PLAY_STORE_URL } from "@/lib/config";

export default function HomePage() {
  return (
    <>
      <SiteHeader />
      <main className="flex-1">
        <section className="relative min-h-[100svh] overflow-hidden bg-ink text-paper">
          <Image
            src="/hero-label.jpg"
            alt="Close-up of a packaged food ingredients label"
            fill
            priority
            className="object-cover object-[center_30%] opacity-55"
            sizes="100vw"
          />
          <div
            className="absolute inset-0"
            style={{
              background:
                "linear-gradient(180deg, rgba(12,31,36,0.55) 0%, rgba(12,31,36,0.35) 42%, rgba(12,31,36,0.92) 100%), radial-gradient(900px 480px at 70% 20%, rgba(31,122,107,0.35), transparent 60%)",
            }}
          />
          <div
            aria-hidden
            className="scan-line pointer-events-none absolute inset-x-0 top-0 z-10 h-24"
            style={{
              background:
                "linear-gradient(180deg, transparent, rgba(232,163,23,0.55), transparent)",
              boxShadow: "0 0 40px var(--glow)",
            }}
          />

          <div className="relative z-10 mx-auto flex min-h-[100svh] max-w-6xl flex-col justify-end px-5 pb-16 pt-28 sm:px-8 sm:pb-20">
            <p className="rise font-[family-name:var(--font-display)] text-4xl font-semibold tracking-tight text-paper sm:text-6xl md:text-7xl">
              {BRAND}
            </p>
            <h1 className="rise rise-delay-1 mt-4 max-w-2xl text-2xl font-medium leading-snug text-paper/95 sm:text-3xl md:text-4xl">
              Scan a label. See what’s banned or restricted.
            </h1>
            <p className="rise rise-delay-2 mt-4 max-w-xl text-base leading-relaxed text-paper/80 sm:text-lg">
              Snap the ingredients panel—or search by name—and match substances
              against regulation signals for your market.
            </p>
            <div className="rise rise-delay-3 mt-8 flex flex-wrap items-center gap-3">
              {PLAY_STORE_URL ? (
                <a
                  href={PLAY_STORE_URL}
                  className="inline-flex items-center rounded-xl bg-saffron px-5 py-3 text-sm font-bold text-ink shadow-lg shadow-black/20 transition hover:brightness-105"
                >
                  Get it on Google Play
                </a>
              ) : (
                <span className="inline-flex items-center rounded-xl bg-saffron px-5 py-3 text-sm font-bold text-ink shadow-lg shadow-black/20">
                  Coming to Google Play
                </span>
              )}
              <a
                href="#how"
                className="inline-flex items-center rounded-xl px-5 py-3 text-sm font-semibold text-paper ring-1 ring-paper/35 backdrop-blur-sm transition hover:bg-paper/10"
              >
                See how it works
              </a>
            </div>
          </div>
        </section>

        <section
          id="how"
          className="mx-auto max-w-6xl px-5 py-20 sm:px-8 sm:py-28"
        >
          <h2 className="font-[family-name:var(--font-display)] text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
            Three ways in. One clear answer.
          </h2>
          <p className="mt-3 max-w-2xl text-lg text-ink-soft">
            Start from a pack photo, an ingredients list, or a product name.
            The app surfaces severity first—so you spend less time guessing.
          </p>

          <ol className="mt-12 grid gap-10 border-t border-[var(--line)] pt-10 sm:grid-cols-3 sm:gap-8">
            {[
              {
                step: "01",
                title: "Search by name",
                body: "Type a product and browse matches ranked by confidence.",
              },
              {
                step: "02",
                title: "Photograph the pack",
                body: "We identify the product and open its ingredient flags.",
              },
              {
                step: "03",
                title: "Photograph the list",
                body: "OCR reads the panel and highlights banned or restricted items.",
              },
            ].map((item) => (
              <li key={item.step} className="min-w-0">
                <p className="font-mono text-xs font-semibold tracking-[0.18em] text-teal">
                  {item.step}
                </p>
                <h3 className="mt-3 font-[family-name:var(--font-display)] text-2xl font-semibold text-ink">
                  {item.title}
                </h3>
                <p className="mt-2 text-base leading-relaxed text-ink-soft">
                  {item.body}
                </p>
              </li>
            ))}
          </ol>
        </section>

        <section className="bg-mist">
          <div className="mx-auto grid max-w-6xl items-center gap-12 px-5 py-20 sm:px-8 sm:py-28 lg:grid-cols-2">
            <div>
              <h2 className="font-[family-name:var(--font-display)] text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
                Severity you can scan at a glance
              </h2>
              <p className="mt-3 text-lg text-ink-soft">
                Each ingredient is tagged for your selected countries. Tap
                through for regulation references when you want the detail.
              </p>
              <ul className="mt-8 space-y-4">
                <Severity
                  color="bg-flag"
                  label="Banned"
                  detail="Not permitted in at least one selected market."
                />
                <Severity
                  color="bg-amber"
                  label="Restricted"
                  detail="Allowed only with limits or conditions."
                />
                <Severity
                  color="bg-ok"
                  label="No known ban"
                  detail="No ban/restriction signal in your selected markets."
                />
              </ul>
            </div>
            <div className="relative aspect-[4/5] overflow-hidden rounded-sm ring-1 ring-ink/10">
              <Image
                src="/pack.jpg"
                alt="Packaged food product on a shelf"
                fill
                className="object-cover"
                sizes="(max-width: 1024px) 100vw, 50vw"
              />
            </div>
          </div>
        </section>

        <section className="mx-auto max-w-6xl px-5 py-20 sm:px-8 sm:py-24">
          <div className="flex flex-col gap-6 sm:flex-row sm:items-end sm:justify-between">
            <div className="max-w-xl">
              <h2 className="font-[family-name:var(--font-display)] text-3xl font-semibold tracking-tight text-ink">
                Built for real shopping decisions
              </h2>
              <p className="mt-3 text-lg text-ink-soft">
                Primary market India, with global products and multi-country
                filters when you need them. Not medical advice—regulation
                signals to help you choose.
              </p>
            </div>
            <Link
              href="/privacy"
              className="text-sm font-semibold text-teal-deep underline-offset-4 hover:underline"
            >
              Read the app privacy policy
            </Link>
          </div>
        </section>
      </main>
      <SiteFooter />
    </>
  );
}

function Severity({
  color,
  label,
  detail,
}: {
  color: string;
  label: string;
  detail: string;
}) {
  return (
    <li className="flex gap-4">
      <span
        className={`mt-1.5 h-3 w-3 shrink-0 rounded-full ${color}`}
        aria-hidden
      />
      <div>
        <p className="font-semibold text-ink">{label}</p>
        <p className="text-ink-soft">{detail}</p>
      </div>
    </li>
  );
}
