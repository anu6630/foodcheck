import SwiftUI
import composeApp

private enum IngredientDetailTab: String, CaseIterable, Identifiable {
    case health = "Health"
    case regulation = "Regulation"
    case more = "More"
    var id: String { rawValue }
}

struct IngredientDetailView: View {
    let ingredientId: String
    @StateObject private var model = ObservableIngredientDetailModel()
    @Environment(\.dismiss) var dismiss
    @State private var tab: IngredientDetailTab = .health
    @State private var expandedKinds: Set<String> = []
    @State private var showUseLimits = false
    @State private var showSources = false
    @State private var didInitExpand = false

    var body: some View {
        ZStack {
            Color(red: 0.01, green: 0.02, blue: 0.09)
                .ignoresSafeArea()

            if model.loading {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: Color(red: 0.06, green: 0.73, blue: 0.51)))
            } else if let error = model.error {
                VStack(spacing: 16) {
                    Image(systemName: "exclamationmark.circle.fill")
                        .font(.system(size: 44))
                        .foregroundColor(.red)
                    Text(error)
                        .foregroundColor(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                }
            } else if let details = model.detail {
                detailBody(details)
            }
        }
        .navigationTitle("Ingredient Detail")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button("Done") { dismiss() }
                    .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
            }
        }
        .onAppear {
            model.load(id: ingredientId)
        }
        .onChange(of: model.detail?.id) { _ in
            guard let details = model.detail, !didInitExpand else { return }
            let grouped = groupHealth(details.healthReports)
            if let first = grouped.first?.key {
                expandedKinds = [first]
            }
            tab = details.healthReports.isEmpty ? .regulation : .health
            didInitExpand = true
        }
    }

    @ViewBuilder
    private func detailBody(_ details: IngredientDetail) -> some View {
        let reports = Array(details.healthReports)
        let grouped = groupHealth(reports)
        let bans = Array(details.bans)
        let countries = countryRows(details)
        let healthLabel = healthGlanceLabel(reports)
        let markets = marketsGlance(details)
        let peek = healthSourcePeek(reports)

        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(details.name)
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.white)
                    if let label = details.codesLabel, !label.isEmpty {
                        Text(label)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                    } else if let eNum = details.eNumber {
                        Text(eNum)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                    }
                    if let category = details.category {
                        Text(category.capitalized)
                            .font(.system(size: 12))
                            .foregroundColor(.white.opacity(0.5))
                    }
                }

                Text("At a glance")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white)

                HStack(spacing: 8) {
                    glanceCard(
                        title: "Health",
                        body: healthLabel,
                        sub: reports.isEmpty ? "No reports linked" : "\(reports.count) signal\(reports.count == 1 ? "" : "s")",
                        accent: healthAccent(reports)
                    ) { tab = .health }
                    glanceCard(
                        title: "In your markets",
                        body: markets.0,
                        sub: markets.1,
                        accent: banAccent(bans)
                    ) { tab = .regulation }
                }

                if !peek.isEmpty {
                    Text(peek)
                        .font(.system(size: 11))
                        .foregroundColor(.white.opacity(0.45))
                        .lineLimit(2)
                }

                tabRow

                switch tab {
                case .health:
                    healthTab(grouped: grouped)
                case .regulation:
                    regulationTab(countries: countries, tracked: details.trackedCountries.count)
                case .more:
                    moreTab(details: details)
                }
            }
            .padding(20)
        }
    }

    private var tabRow: some View {
        HStack(spacing: 4) {
            ForEach(IngredientDetailTab.allCases) { t in
                Button {
                    tab = t
                } label: {
                    Text(t.rawValue)
                        .font(.system(size: 13, weight: tab == t ? .bold : .semibold))
                        .foregroundColor(tab == t ? Color(red: 0.06, green: 0.73, blue: 0.51) : .white.opacity(0.5))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 10)
                                .fill(tab == t ? Color(red: 0.06, green: 0.73, blue: 0.51).opacity(0.2) : Color.clear)
                        )
                }
            }
        }
        .padding(4)
        .background(Color.white.opacity(0.06))
        .cornerRadius(12)
    }

    @ViewBuilder
    private func healthTab(grouped: [(key: String, reports: [IngredientHealthReport])]) -> some View {
        if grouped.isEmpty {
            muted("No health reports linked yet. That is not a safety clearance.")
        } else {
            Text("Grouped by topic. Each row keeps its publisher.")
                .font(.system(size: 12))
                .foregroundColor(.white.opacity(0.5))
            ForEach(grouped, id: \.key) { group in
                let open = expandedKinds.contains(group.key)
                Button {
                    if open { expandedKinds.remove(group.key) } else { expandedKinds.insert(group.key) }
                } label: {
                    HStack {
                        Text(open ? "▼" : "▶")
                            .font(.system(size: 11))
                            .foregroundColor(.orange)
                        Text(healthKindTitle(group.key))
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                        Spacer()
                        Text("(\(group.reports.count))")
                            .font(.system(size: 12))
                            .foregroundColor(.white.opacity(0.45))
                    }
                    .padding(12)
                    .background(Color.white.opacity(0.05))
                    .cornerRadius(10)
                }
                if open {
                    ForEach(Array(group.reports.enumerated()), id: \.offset) { _, report in
                        healthCard(report)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func regulationTab(countries: [CountryRow], tracked: Int) -> some View {
        Text("Status by country")
            .font(.system(size: 14, weight: .bold))
            .foregroundColor(.white)
        Text("Country law / authorisation — separate from health reports.")
            .font(.system(size: 12))
            .foregroundColor(.white.opacity(0.5))
        if countries.isEmpty {
            muted("No regulation data in the \(tracked) countries we track. That is not a safety clearance.")
        } else {
            ForEach(countries) { row in
                HStack(spacing: 10) {
                    Text(row.code)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white)
                        .frame(width: 32, alignment: .leading)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(row.label)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.white)
                        if let condition = row.condition, !condition.isEmpty {
                            Text(condition)
                                .font(.system(size: 11))
                                .foregroundColor(.white.opacity(0.45))
                                .lineLimit(2)
                        }
                    }
                    Spacer()
                    Text(row.statusLabel)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(row.statusColor)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(row.statusColor.opacity(0.15))
                        .cornerRadius(6)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(Color.white.opacity(0.04))
                .cornerRadius(8)
            }
        }
    }

    @ViewBuilder
    private func moreTab(details: IngredientDetail) -> some View {
        disclosure("Where it can be used (\(details.useLimits.count) food categories)", expanded: $showUseLimits, enabled: !details.useLimits.isEmpty)
        if showUseLimits {
            let limits = Array(details.useLimits.prefix(80))
            ForEach(Array(limits.enumerated()), id: \.offset) { _, lim in
                useLimitRow(lim)
            }
            if details.useLimits.count > limits.count {
                Text("and \(details.useLimits.count - limits.count) more categories")
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.45))
            }
        }

        disclosure("Source documents (\(details.sources.count))", expanded: $showSources, enabled: !details.sources.isEmpty)
        if !showSources && !details.sources.isEmpty {
            Text("Archived copies of what we captured for the claims on this ingredient.")
                .font(.system(size: 12))
                .foregroundColor(.white.opacity(0.45))
        }
        if showSources {
            ForEach(Array(details.sources.enumerated()), id: \.offset) { _, src in
                VStack(alignment: .leading, spacing: 4) {
                    Text(src.title)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.white)
                    ForEach(Array(src.files.enumerated()), id: \.offset) { _, file in
                        archivedFileLink(file)
                    }
                }
                .padding(12)
                .background(Color.white.opacity(0.04))
                .cornerRadius(10)
            }
        }
    }

    private func glanceCard(title: String, body: String, sub: String, accent: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 6) {
                Text(title)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(.white.opacity(0.5))
                HStack(spacing: 6) {
                    Circle().fill(accent).frame(width: 8, height: 8)
                    Text(body)
                        .font(.system(size: 15, weight: .heavy))
                        .foregroundColor(.white)
                }
                Text(sub)
                    .font(.system(size: 11))
                    .foregroundColor(.white.opacity(0.45))
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.white.opacity(0.05))
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(accent.opacity(0.35), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    private func healthCard(_ report: IngredientHealthReport) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(report.summary)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(.white)
            if let value = report.value, !value.isEmpty {
                let unit = report.unit ?? ""
                Text("\(value)\(unit.isEmpty ? "" : " \(unit)")")
                    .font(.system(size: 13))
                    .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
            }
            let publisher = report.sourcePublisher ?? ""
            let title = report.sourceTitle ?? report.sourceId
            Text(publisher.isEmpty ? title : "\(publisher) · \(title)")
                .font(.system(size: 12))
                .foregroundColor(.white.opacity(0.5))
            if let year = report.evaluationYear, !year.isEmpty {
                Text("Eval \(year)")
                    .font(.system(size: 11))
                    .foregroundColor(.white.opacity(0.4))
            }
            if let url = report.citationUrl, let link = URL(string: url) {
                Link("Open ↗", destination: link)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.orange.opacity(0.08))
        .cornerRadius(10)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(Color.orange.opacity(0.3), lineWidth: 0.5)
        )
    }

    private func useLimitRow(_ lim: IngredientUseLimit) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                let code = lim.foodCategoryCode
                let name = lim.foodCategoryName
                let foodName = (name != nil && !(name!.isEmpty)) ? name! : (code.isEmpty ? lim.countryCode : code)
                Text(foodName)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.white)
                Text(countryTitle(lim.countryCode))
                    .font(.system(size: 11))
                    .foregroundColor(.white.opacity(0.45))
            }
            Spacer()
            Text(lim.maxLevel.isEmpty ? "—" : lim.maxLevel)
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
        }
        .padding(12)
        .background(Color.white.opacity(0.04))
        .cornerRadius(10)
    }

    private func disclosure(_ title: String, expanded: Binding<Bool>, enabled: Bool) -> some View {
        Button {
            if enabled { expanded.wrappedValue.toggle() }
        } label: {
            HStack {
                Text(!enabled ? "·" : (expanded.wrappedValue ? "▼" : "▶"))
                    .font(.system(size: 11))
                    .foregroundColor(.white.opacity(0.45))
                Text(title)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(enabled ? .white : .white.opacity(0.4))
                Spacer()
            }
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }

    private func muted(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 13))
            .foregroundColor(.white.opacity(0.5))
    }
}

private struct CountryRow: Identifiable {
    let id: String
    let code: String
    let label: String
    let status: String
    let condition: String?
    var statusLabel: String {
        switch status.lowercased() {
        case "banned": return "Banned"
        case "restricted": return "Restricted"
        case "permitted": return "Authorised"
        case "no_data": return "No data"
        default: return status
        }
    }
    var statusColor: Color {
        switch status.lowercased() {
        case "banned": return .red
        case "restricted": return .orange
        case "permitted": return Color(red: 0.20, green: 0.83, blue: 0.60)
        default: return .gray
        }
    }
}

@ViewBuilder
private func archivedFileLink(_ file: DataSourceFile) -> some View {
    let kindLabel = file.kind == "parsed" ? "Ingest extract" : file.kind == "historical" ? "Earlier capture" : "Archived copy"
    let sha = String(file.sha256.prefix(12))
    let label = "\(kindLabel): \(file.filename)  sha256:\(sha)…"
    if let href = file.downloadUrl, !href.isEmpty, let fileURL = URL(string: href) {
        Link(label, destination: fileURL)
            .font(.system(size: 11))
            .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
            .lineLimit(2)
    } else {
        Text(label)
            .font(.system(size: 11))
            .foregroundColor(.white.opacity(0.45))
    }
}

private func countryTitle(_ code: String) -> String {
    switch code.uppercased() {
    case "IN": return "India"
    case "EU": return "European Union"
    case "US": return "United States"
    case "AU": return "Australia"
    case "NZ": return "New Zealand"
    case "GB": return "United Kingdom"
    case "CA": return "Canada"
    case "KR": return "South Korea"
    case "JP": return "Japan"
    default: return code
    }
}

private func groupKey(_ kind: String) -> String {
    switch kind.lowercased() {
    case "iarc_2b", "iarc_2b_banned_many": return "carcinogen"
    case "not_authorised_eu": return "authorisation_withdrawn"
    default: return kind.lowercased()
    }
}

private func groupHealth(_ reports: [IngredientHealthReport]) -> [(key: String, reports: [IngredientHealthReport])] {
    var map: [String: [IngredientHealthReport]] = [:]
    for r in reports {
        map[groupKey(r.kind), default: []].append(r)
    }
    return map.keys.sorted { healthKindRank($0) < healthKindRank($1) }.map { (key: $0, reports: map[$0]!) }
}

private func healthKindRank(_ kind: String) -> Int {
    switch kind.lowercased() {
    case "carcinogen": return 0
    case "cancer_precursor_concern": return 1
    case "authorisation_withdrawn": return 2
    case "genotox": return 3
    case "adi": return 4
    case "overexposure_risk", "label_warning_hyperactivity": return 5
    case "anses_of_interest": return 6
    case "evaluation": return 7
    default: return 8
    }
}

private func healthKindTitle(_ kind: String) -> String {
    switch kind.lowercased() {
    case "carcinogen": return "Carcinogen"
    case "cancer_precursor_concern": return "Cancer-related concern"
    case "authorisation_withdrawn": return "Authorisation"
    case "genotox": return "Genotoxicity"
    case "adi": return "Acceptable daily intake"
    case "overexposure_risk": return "Overexposure risk"
    case "anses_of_interest": return "Agency interest list"
    case "label_warning_hyperactivity": return "Label warning"
    case "evaluation": return "Evaluation"
    default: return kind.replacingOccurrences(of: "_", with: " ").capitalized
    }
}

private func healthGlanceLabel(_ reports: [IngredientHealthReport]) -> String {
    if reports.isEmpty { return "No data" }
    let kinds = Set(reports.map { $0.kind.lowercased() })
    if kinds.contains(where: {
        ["carcinogen", "iarc_2b", "iarc_2b_banned_many", "cancer_precursor_concern",
         "authorisation_withdrawn", "not_authorised_eu", "genotox",
         "overexposure_risk", "label_warning_hyperactivity"].contains($0)
    }) { return "Watch" }
    if kinds.contains("adi") || kinds.contains("anses_of_interest") { return "Notes" }
    return "Info"
}

private func marketsGlance(_ details: IngredientDetail) -> (String, String) {
    let bans = Array(details.bans)
    let tracked = details.trackedCountries.count
    if bans.isEmpty { return ("No data", "\(tracked) countries tracked") }
    let banned = bans.filter { $0.status == "banned" }.count
    let restricted = bans.filter { $0.status == "restricted" }.count
    let authorised = bans.filter { $0.status == "permitted" }.count
    let body: String
    if banned > 0 { body = "\(banned) banned" }
    else if restricted > 0 { body = "\(restricted) restricted" }
    else if authorised > 0 { body = "Authorised" }
    else { body = "Mixed" }
    var bits: [String] = []
    if authorised > 0 && banned == 0 { bits.append("\(authorised) authorised") }
    if restricted > 0 { bits.append("\(restricted) restricted") }
    if banned > 0 { bits.append("\(banned) banned") }
    bits.append("\(tracked) tracked")
    return (body, bits.joined(separator: " · "))
}

private func healthSourcePeek(_ reports: [IngredientHealthReport]) -> String {
    var labels: [String] = []
    for r in reports {
        let p = (r.sourcePublisher ?? "").uppercased()
        let label: String?
        if p.contains("IARC") { label = "IARC" }
        else if p.contains("EFSA") { label = "EFSA" }
        else if p.contains("JECFA") { label = "JECFA" }
        else if p.contains("OPEN FOOD") { label = "Open Food Facts" }
        else if r.kind.lowercased().contains("carcinogen"), let v = r.value { label = "IARC \(v)" }
        else { label = nil }
        if let label, !labels.contains(label) { labels.append(label) }
        if labels.count >= 5 { break }
    }
    return labels.joined(separator: " · ")
}

private func healthAccent(_ reports: [IngredientHealthReport]) -> Color {
    healthGlanceLabel(reports) == "No data" ? .gray : .orange
}

private func banAccent(_ bans: [IngredientBan]) -> Color {
    if bans.contains(where: { $0.status == "banned" }) { return .red }
    if bans.contains(where: { $0.status == "restricted" }) { return .orange }
    if bans.contains(where: { $0.status == "permitted" }) { return Color(red: 0.20, green: 0.83, blue: 0.60) }
    return .gray
}

private func countryRows(_ details: IngredientDetail) -> [CountryRow] {
    let byCode = Dictionary(uniqueKeysWithValues: details.bans.map { ($0.countryCode.uppercased(), $0) })
    var order = details.trackedCountries.map { $0.uppercased() }
    order = order.reduce(into: [String]()) { acc, code in
        if !acc.contains(code) { acc.append(code) }
    }
    for code in byCode.keys where !order.contains(code) { order.append(code) }
    return order.map { code in
        let ban = byCode[code]
        return CountryRow(
            id: code,
            code: code,
            label: ban?.countryLabel?.isEmpty == false ? ban!.countryLabel! : countryTitle(code),
            status: ban?.status ?? "no_data",
            condition: ban?.condition
        )
    }
}
