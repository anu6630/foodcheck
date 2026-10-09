import SwiftUI
import composeApp

struct IngredientDetailView: View {
    let ingredientId: String
    @StateObject private var model = ObservableIngredientDetailModel()
    @Environment(\.dismiss) var dismiss
    
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
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        // Title header
                        VStack(alignment: .leading, spacing: 4) {
                            Text(details.name)
                                .font(.system(size: 24, weight: .bold))
                                .foregroundColor(.white)
                            
                            if let label = details.codesLabel, !label.isEmpty {
                                Text("Codes: \(label)")
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                            } else if let eNum = details.eNumber {
                                Text("E-Number: \(eNum)")
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                            }
                            
                            if let category = details.category {
                                Text("Category: \(category)")
                                    .font(.system(size: 13))
                                    .foregroundColor(.white.opacity(0.5))
                            }
                        }
                        
                        Divider()
                            .background(Color.white.opacity(0.1))
                        
                        // INCI & CAS details
                        if details.inciName != nil || details.casNumber != nil {
                            VStack(alignment: .leading, spacing: 6) {
                                if let inci = details.inciName {
                                    Text("INCI Name: \(inci)")
                                        .font(.system(size: 13))
                                        .foregroundColor(.white.opacity(0.7))
                                }
                                if let cas = details.casNumber {
                                    Text("CAS Number: \(cas)")
                                        .font(.system(size: 13))
                                        .foregroundColor(.white.opacity(0.7))
                                }
                            }
                            .padding(14)
                            .background(Color.white.opacity(0.03))
                            .cornerRadius(12)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Color.white.opacity(0.08), lineWidth: 0.5)
                            )
                        }
                        
                        // Bans & Regulatory status
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Regulatory Status")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)
                            
                            if details.bans.isEmpty {
                                HStack(spacing: 8) {
                                    Image(systemName: "questionmark.circle.fill")
                                        .foregroundColor(.gray)
                                    Text("No regulation data in the countries we track. That is not a safety clearance.")
                                        .font(.system(size: 14))
                                        .foregroundColor(.white.opacity(0.7))
                                }
                                .padding(14)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color.white.opacity(0.04))
                                .cornerRadius(12)
                            } else {
                                ForEach(details.bans, id: \.self) { ban in
                                    VStack(alignment: .leading, spacing: 6) {
                                        HStack {
                                            Text(countryTitle(ban.countryCode))
                                                .font(.system(size: 14, weight: .bold))
                                                .foregroundColor(.white)
                                            Spacer()
                                            
                                            let statusColor: Color = {
                                                switch ban.status.lowercased() {
                                                case "banned": return .red
                                                case "restricted": return .orange
                                                case "permitted": return Color(red: 0.20, green: 0.83, blue: 0.60)
                                                default: return .gray
                                                }
                                            }()
                                            let statusLabel: String = {
                                                switch ban.status.lowercased() {
                                                case "banned": return "BANNED"
                                                case "restricted": return "RESTRICTED"
                                                case "permitted": return "AUTHORISED"
                                                default: return ban.status.uppercased()
                                                }
                                            }()
                                            Text(statusLabel)
                                                .font(.system(size: 11, weight: .bold))
                                                .foregroundColor(statusColor)
                                                .padding(.horizontal, 8)
                                                .padding(.vertical, 4)
                                                .background(statusColor.opacity(0.12))
                                                .cornerRadius(6)
                                        }
                                        
                                        if let condition = ban.condition {
                                            Text("Condition: \(condition)")
                                                .font(.system(size: 12))
                                                .foregroundColor(.white.opacity(0.6))
                                        }
                                        if let ref = ban.regulationRef {
                                            Text("Ref: \(ref)")
                                                .font(.system(size: 11))
                                                .foregroundColor(.gray)
                                        }
                                    }
                                    .padding(14)
                                    .background(Color.white.opacity(0.04))
                                    .cornerRadius(12)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12)
                                            .stroke(Color.white.opacity(0.1), lineWidth: 0.5)
                                    )
                                }
                            }
                        }
                        
                        let limits = Array(details.useLimits.prefix(80))
                        if !limits.isEmpty {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Where it can be used (\(details.useLimits.count) food categories)")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(.white)
                                Text("Numbers follow Codex GSFA food categories (the same scheme FSSAI Appendix A uses).")
                                    .font(.system(size: 12))
                                    .foregroundColor(.white.opacity(0.5))
                                
                                ForEach(Array(limits.enumerated()), id: \.offset) { _, lim in
                                    HStack(alignment: .top) {
                                        VStack(alignment: .leading, spacing: 2) {
                                            let code = lim.foodCategoryCode
                                            let name = lim.foodCategoryName
                                            let foodName = (name != nil && !(name!.isEmpty)) ? name! : (code.isEmpty ? lim.countryCode : code)
                                            Text(foodName)
                                                .font(.system(size: 13, weight: .semibold))
                                                .foregroundColor(.white)
                                            var metaParts: [String] = [countryTitle(lim.countryCode)]
                                            if !code.isEmpty && code != "GMP" { metaParts.append(code) }
                                            if !metaParts.isEmpty {
                                                Text(metaParts.joined(separator: " · "))
                                                    .font(.system(size: 11))
                                                    .foregroundColor(.white.opacity(0.45))
                                            }
                                            if let note = lim.note {
                                                Text(note)
                                                    .font(.system(size: 11))
                                                    .foregroundColor(.white.opacity(0.45))
                                            }
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
                                
                                if details.useLimits.count > limits.count {
                                    Text("and \(details.useLimits.count - limits.count) more categories")
                                        .font(.system(size: 12))
                                        .foregroundColor(.white.opacity(0.45))
                                }
                            }
                        }

                        if !details.sources.isEmpty {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Sources")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(.white)
                                Text("The document and version we actually captured — not an implication that nothing newer exists.")
                                    .font(.system(size: 12))
                                    .foregroundColor(.white.opacity(0.5))
                                ForEach(Array(details.sources.enumerated()), id: \.offset) { _, src in
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(src.title)
                                            .font(.system(size: 13, weight: .semibold))
                                            .foregroundColor(.white)
                                        var versionBits: [String] = []
                                        if let v = src.version, !v.isEmpty { versionBits.append("Version \(v)") }
                                        if let d = src.versionDate, !d.isEmpty { versionBits.append("dated \(d)") }
                                        if let cc = src.countryCode, !cc.isEmpty { versionBits.append(cc) }
                                        if !versionBits.isEmpty {
                                            Text(versionBits.joined(separator: " · "))
                                                .font(.system(size: 12))
                                                .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                                        }
                                        if !src.retrievedAt.isEmpty {
                                            Text("Captured \(String(src.retrievedAt.prefix(10)))")
                                                .font(.system(size: 11))
                                                .foregroundColor(.white.opacity(0.45))
                                        }
                                        if let citation = src.citation {
                                            Text(citation)
                                                .font(.system(size: 11))
                                                .foregroundColor(.gray)
                                        }
                                        if let collected = src.url, !collected.isEmpty, let collectedURL = URL(string: collected) {
                                            Link("Collected from: \(collected)", destination: collectedURL)
                                                .font(.system(size: 11))
                                                .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                                                .lineLimit(2)
                                        }
                                        if let official = src.citationUrl, !official.isEmpty, official != src.url, let officialURL = URL(string: official) {
                                            Link("Official text: \(official)", destination: officialURL)
                                                .font(.system(size: 11))
                                                .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                                                .lineLimit(2)
                                        }
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
                    }
                    .padding(24)
                }
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
