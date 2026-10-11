import Image from "next/image";
import Link from "next/link";
import { BRAND } from "@/lib/config";

export function SiteHeader() {
  return (
    <header className="absolute inset-x-0 top-0 z-20">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-5 py-5 sm:px-8">
        <Link href="/" className="flex items-center gap-3 text-paper">
          <Image
            src="/app-icon.png"
            alt=""
            width={36}
            height={36}
            className="rounded-lg shadow-sm ring-1 ring-white/25"
            priority
          />
          <span className="font-[family-name:var(--font-display)] text-lg font-semibold tracking-tight">
            {BRAND}
          </span>
        </Link>
        <nav className="flex items-center gap-5 text-sm font-medium text-paper/85">
          <a href="#how" className="hover:text-paper">
            How it works
          </a>
          <Link href="/privacy" className="hover:text-paper">
            Privacy
          </Link>
        </nav>
      </div>
    </header>
  );
}

export function SiteFooter() {
  return (
    <footer className="border-t border-[var(--line)] bg-mist/60">
      <div className="mx-auto flex max-w-6xl flex-col gap-4 px-5 py-10 text-sm text-ink-soft sm:flex-row sm:items-center sm:justify-between sm:px-8">
        <p>
          <span className="font-[family-name:var(--font-display)] text-base font-semibold text-ink">
            {BRAND}
          </span>
          <span className="mx-2 text-ink/30">·</span>
          A LifeX app
        </p>
        <div className="flex flex-wrap gap-x-5 gap-y-2">
          <Link href="/privacy" className="underline-offset-2 hover:underline">
            App privacy policy
          </Link>
          <a
            href="mailto:privacy@lifex.biz"
            className="underline-offset-2 hover:underline"
          >
            privacy@lifex.biz
          </a>
        </div>
      </div>
    </footer>
  );
}
