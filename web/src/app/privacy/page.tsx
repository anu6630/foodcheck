import type { Metadata } from "next";
import Link from "next/link";
import { SiteFooter } from "@/components/SiteChrome";
import {
  APP_PACKAGE,
  BRAND,
  CONTACT_EMAIL,
  SITE_URL,
} from "@/lib/config";

export const metadata: Metadata = {
  title: "App privacy policy",
  description: `Privacy policy for the ${BRAND} mobile app (${APP_PACKAGE}), including Google AdMob advertising.`,
};

const UPDATED = "10 October 2026";

export default function PrivacyPage() {
  return (
    <>
      <header className="border-b border-[var(--line)] bg-mist/50">
        <div className="mx-auto flex max-w-2xl items-center justify-between px-5 py-5 sm:px-0">
          <Link
            href="/"
            className="font-[family-name:var(--font-display)] text-lg font-semibold text-ink"
          >
            {BRAND}
          </Link>
          <Link
            href="/"
            className="text-sm font-medium text-ink-soft hover:text-ink"
          >
            Back to home
          </Link>
        </div>
      </header>

      <main className="mx-auto max-w-2xl flex-1 px-5 py-12 sm:px-0 sm:py-16">
        <article className="space-y-10 text-ink-soft">
          <header className="space-y-3">
            <h1 className="font-[family-name:var(--font-display)] text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
              Privacy policy for the {BRAND} app
            </h1>
            <p className="text-sm text-ink/55">Last updated: {UPDATED}</p>
            <p className="leading-relaxed">
              This policy explains how the {BRAND} mobile application (“the
              App”) handles information. It applies to the App on Android and
              iOS, not to unrelated LifeX websites unless those sites say
              otherwise.
            </p>
          </header>

          <Section title="Who we are">
            <p>
              The App is provided by LifeX (“we”, “us”). Package / bundle
              identifier: <code className="text-sm text-ink">{APP_PACKAGE}</code>
              .
            </p>
            <p className="mt-2">
              Website:{" "}
              <a className="font-medium text-teal-deep underline-offset-2 hover:underline" href={SITE_URL}>
                {SITE_URL}
              </a>
              <br />
              Contact:{" "}
              <a
                className="font-medium text-teal-deep underline-offset-2 hover:underline"
                href={`mailto:${CONTACT_EMAIL}`}
              >
                {CONTACT_EMAIL}
              </a>
            </p>
          </Section>

          <Section title="Information the App may process">
            <ul className="list-disc space-y-2 pl-5">
              <li>
                <strong className="text-ink">Photos and camera input.</strong>{" "}
                When you scan a pack or ingredients list, the App uses images
                you capture or choose so we can identify products and extract
                ingredient text. Images are sent to our servers / AI providers
                only to perform that analysis and related product matching.
              </li>
              <li>
                <strong className="text-ink">Search and product queries.</strong>{" "}
                Text you type to look up products or ingredients is processed to
                return results.
              </li>
              <li>
                <strong className="text-ink">Shelf / saved items (on device).</strong>{" "}
                If you save products to a local shelf or similar list, that data
                may stay on your device unless a future sync feature is clearly
                offered and you choose to use it.
              </li>
              <li>
                <strong className="text-ink">Device and diagnostics.</strong>{" "}
                We may receive technical data such as app version, OS version,
                language, country selection, crash logs, and coarse
                performance metrics to keep the App reliable.
              </li>
              <li>
                <strong className="text-ink">Advertising identifiers.</strong>{" "}
                When ads are shown, advertising platforms may use device
                advertising IDs and similar identifiers as described below.
              </li>
            </ul>
          </Section>

          <Section title="How we use information">
            <ul className="list-disc space-y-2 pl-5">
              <li>Provide scan, search, and ingredient-status features</li>
              <li>Improve matching quality, safety signals, and App performance</li>
              <li>Show ads (including via Google AdMob) and measure ad performance</li>
              <li>Prevent abuse and troubleshoot issues</li>
              <li>Comply with law when required</li>
            </ul>
          </Section>

          <Section title="Advertising — Google AdMob">
            <p>
              The App is intended to include advertising powered by{" "}
              <strong className="text-ink">Google AdMob</strong> (and related
              Google advertising services). AdMob and Google may collect and
              process data such as:
            </p>
            <ul className="mt-3 list-disc space-y-2 pl-5">
              <li>Advertising ID / resettable device identifiers</li>
              <li>IP address and approximate location derived from it</li>
              <li>Device type, OS, app version, and interaction with ads</li>
              <li>
                Information used for customizing ads, fraud prevention, and
                reporting
              </li>
            </ul>
            <p className="mt-3">
              Google’s processing is governed by Google’s policies, including
              the{" "}
              <a
                className="font-medium text-teal-deep underline-offset-2 hover:underline"
                href="https://policies.google.com/privacy"
                rel="noopener noreferrer"
                target="_blank"
              >
                Google Privacy Policy
              </a>{" "}
              and AdMob / Google Play services disclosures. You can often limit
              ad personalization in your device settings (for example, Android
              advertising ID / ad personalization controls, or Apple App
              Tracking Transparency and related settings where applicable).
            </p>
            <p className="mt-3">
              We do not sell your personal information. Advertising partners
              receive data needed to serve and measure ads as described in their
              policies.
            </p>
          </Section>

          <Section title="Third-party services">
            <p>Depending on the feature, the App may rely on:</p>
            <ul className="mt-2 list-disc space-y-2 pl-5">
              <li>Our own backend APIs hosted on cloud infrastructure</li>
              <li>AI / vision providers to parse labels and match products</li>
              <li>Google AdMob and Google Mobile Ads SDKs for advertising</li>
              <li>
                Optional analytics or crash-reporting tools if enabled in a given
                release
              </li>
            </ul>
            <p className="mt-3">
              Those providers process data only as needed to deliver their
              service under their own terms and privacy policies.
            </p>
          </Section>

          <Section title="Data retention">
            <p>
              We keep server-side logs and analysis artifacts only as long as
              needed for the purposes above (providing the feature, security,
              debugging, and legal obligations), then delete or aggregate them.
              On-device shelf data remains until you clear it or uninstall the
              App.
            </p>
          </Section>

          <Section title="Security">
            <p>
              We use reasonable technical and organizational measures to protect
              information in transit and at rest. No method of transmission or
              storage is completely secure.
            </p>
          </Section>

          <Section title="Children">
            <p>
              The App is not directed at children under 13 (or the minimum age
              required in your country). We do not knowingly collect personal
              information from children. If you believe a child has provided
              personal information, contact us and we will take appropriate
              steps.
            </p>
          </Section>

          <Section title="Your choices and rights">
            <p>
              You can decline camera or photo access in system settings (some
              features will not work without it). You can reset or limit
              advertising identifiers in device settings. Depending on where you
              live, you may have rights to access, correct, delete, or restrict
              certain personal data, or to object to certain processing. Contact{" "}
              <a
                className="font-medium text-teal-deep underline-offset-2 hover:underline"
                href={`mailto:${CONTACT_EMAIL}`}
              >
                {CONTACT_EMAIL}
              </a>{" "}
              to make a request. You may also have the right to lodge a complaint
              with a supervisory authority.
            </p>
          </Section>

          <Section title="International transfers">
            <p>
              Our servers and service providers may process data in countries
              other than yours. Where required, we use appropriate safeguards
              for those transfers.
            </p>
          </Section>

          <Section title="Changes">
            <p>
              We may update this policy as the App or laws change. The “Last
              updated” date at the top will change when we do. Continued use of
              the App after an update means you accept the revised policy.
            </p>
          </Section>

          <p className="rounded-sm bg-mist px-4 py-3 text-sm text-ink">
            The App provides informational regulation signals about ingredients.
            It is not medical, legal, or dietary advice. Always check official
            labels and regulations for decisions that matter to your health or
            compliance.
          </p>
        </article>
      </main>
      <SiteFooter />
    </>
  );
}

function Section({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <section className="space-y-3">
      <h2 className="font-[family-name:var(--font-display)] text-xl font-semibold text-ink">
        {title}
      </h2>
      <div className="leading-relaxed">{children}</div>
    </section>
  );
}
