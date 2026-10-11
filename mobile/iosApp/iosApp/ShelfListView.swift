import SwiftUI
import composeApp

private let collapsedChipLimit = 4

struct ShelfListView: View {
    var onIngredientClick: (String) -> Void
    @StateObject private var model = ObservableShelfModel()
    @State private var expandedIds: Set<Int32> = []
    @State private var stanceEditItem: ShelfItemDto? = nil
    @FocusState private var filterFocused: Bool

    private var filterIdle: Bool {
        model.query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !filterFocused
    }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Your saved packs")
                            .font(.system(size: 22, weight: .bold))
                            .foregroundColor(.white.opacity(0.92))
                        Text("Stance colors how you treat each one. Private to this device.")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(.white.opacity(0.45))
                    }

                    HStack(spacing: 12) {
                        ZStack {
                            Circle()
                                .fill(Color(red: 0.06, green: 0.73, blue: 0.51).opacity(filterFocused ? 0.18 : 0.10))
                                .frame(width: 34, height: 34)
                            Image(systemName: "magnifyingglass")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                        }

                        TextField("Filter your list", text: $model.query)
                            .foregroundColor(.white)
                            .font(.system(size: 16, weight: .medium))
                            .autocorrectionDisabled()
                            .focused($filterFocused)
                            .onChange(of: model.query) { newValue in
                                model.search(newValue)
                            }

                        if !model.query.isEmpty {
                            Button(action: { model.query = "" }) {
                                Image(systemName: "xmark")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundColor(.white.opacity(0.45))
                                    .frame(width: 28, height: 28)
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, filterIdle ? 16 : 14)
                    .background(
                        RoundedRectangle(cornerRadius: 22)
                            .fill(filterFocused
                                  ? Color(red: 0.07, green: 0.09, blue: 0.17).opacity(0.96)
                                  : Color.white.opacity(0.06))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 22)
                            .stroke(
                                filterFocused
                                    ? Color(red: 0.06, green: 0.73, blue: 0.51).opacity(0.55)
                                    : Color.white.opacity(0.10),
                                lineWidth: 1
                            )
                    )

                    if !model.items.isEmpty {
                        HStack(spacing: 14) {
                            ForEach(
                                [ShelfStance.avoid, .occasional, .ok, .unsure],
                                id: \.apiValue
                            ) { stance in
                                HStack(spacing: 5) {
                                    Circle()
                                        .fill(stanceAccent(stance))
                                        .frame(width: 6, height: 6)
                                    Text(stance.label)
                                        .font(.system(size: 11, weight: .medium))
                                        .foregroundColor(.white.opacity(0.45))
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 14)

                if let error = model.error {
                    Text(error)
                        .foregroundColor(.red)
                        .font(.caption)
                        .padding(.horizontal, 20)
                }

                if model.items.isEmpty {
                    Spacer()
                    VStack(spacing: 6) {
                        if model.query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            Text("Save a pack from Scan")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(.white.opacity(0.75))
                            Text("It stays on this device only")
                                .font(.system(size: 13))
                                .foregroundColor(.white.opacity(0.35))
                        } else {
                            Text("No matches in your list.")
                                .font(.system(size: 13))
                                .foregroundColor(.white.opacity(0.45))
                        }
                    }
                    .frame(maxWidth: .infinity)
                    Spacer()
                } else {
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 10) {
                            ForEach(model.items, id: \.id) { item in
                                ShelfScanCardView(
                                    item: item,
                                    expanded: expandedIds.contains(item.id),
                                    onToggle: {
                                        if expandedIds.contains(item.id) {
                                            expandedIds.remove(item.id)
                                        } else {
                                            expandedIds.insert(item.id)
                                        }
                                    },
                                    onStanceClick: { stanceEditItem = item },
                                    onIngredientClick: onIngredientClick
                                )
                            }
                            Spacer().frame(height: 72)
                        }
                        .padding(.horizontal, 20)
                    }
                }
            }
            .background(Color(red: 0.01, green: 0.02, blue: 0.09).ignoresSafeArea())
            .overlay {
                if let item = stanceEditItem {
                    let current = ShelfStance.companion.fromApi(raw: item.stance)
                    ZStack {
                        Color.black.opacity(0.45).ignoresSafeArea()
                            .onTapGesture { stanceEditItem = nil }
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text("Stance")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(.white)
                                Spacer()
                                Button { stanceEditItem = nil } label: {
                                    Image(systemName: "xmark")
                                        .foregroundColor(.white.opacity(0.5))
                                        .frame(width: 28, height: 28)
                                }
                            }
                            .padding(.horizontal, 4)
                            .padding(.bottom, 4)
                            ForEach(
                                [ShelfStance.avoid, .occasional, .ok, .unsure],
                                id: \.apiValue
                            ) { option in
                                Button {
                                    model.updateStance(id: item.id, stance: option)
                                    stanceEditItem = nil
                                } label: {
                                    HStack(spacing: 10) {
                                        Circle()
                                            .fill(stanceAccent(option))
                                            .frame(width: 8, height: 8)
                                        Text(option.label)
                                            .font(.system(size: 14, weight: option == current ? .bold : .medium))
                                            .foregroundColor(option == current ? stanceAccent(option) : .white)
                                        Spacer()
                                        if option == current {
                                            Image(systemName: "checkmark")
                                                .foregroundColor(stanceAccent(option))
                                        }
                                    }
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 12)
                                }
                            }
                        }
                        .padding(14)
                        .background(Color(red: 0.06, green: 0.08, blue: 0.14))
                        .clipShape(RoundedRectangle(cornerRadius: 18))
                        .padding(.horizontal, 28)
                    }
                }
            }
        }
    }

    private func stanceAccent(_ stance: ShelfStance) -> Color {
        switch stance {
        case .avoid: return Color(red: 0.94, green: 0.27, blue: 0.27)
        case .occasional: return Color(red: 0.96, green: 0.62, blue: 0.04)
        case .ok: return Color(red: 0.06, green: 0.73, blue: 0.51)
        default: return Color(red: 0.58, green: 0.64, blue: 0.72)
        }
    }
}

struct ShelfScanCardView: View {
    let item: ShelfItemDto
    let expanded: Bool
    let onToggle: () -> Void
    let onStanceClick: () -> Void
    let onIngredientClick: (String) -> Void

    private var stance: ShelfStance {
        ShelfStance.companion.fromApi(raw: item.stance)
    }

    private var accent: Color {
        switch stance {
        case .avoid: return Color(red: 0.94, green: 0.27, blue: 0.27)
        case .occasional: return Color(red: 0.96, green: 0.62, blue: 0.04)
        case .ok: return Color(red: 0.06, green: 0.73, blue: 0.51)
        default: return Color.gray.opacity(0.8)
        }
    }

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            Rectangle()
                .fill(accent)
                .frame(width: 3)
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.displayName)
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(.white)
                            .lineLimit(1)
                        let brand = item.brand?.trimmingCharacters(in: .whitespacesAndNewlines)
                        let companies = (item.companies as? [CatalogCompanyDto] ?? [])
                            .map(\.name)
                            .joined(separator: ", ")
                        let tags = ((item.tags as? [String]) ?? []).joined(separator: ", ")
                        let parts = [brand, companies.isEmpty ? nil : companies, tags.isEmpty ? nil : tags]
                            .compactMap { value -> String? in
                                guard let value, !value.isEmpty else { return nil }
                                return value
                            }
                        if !parts.isEmpty {
                            Text(parts.joined(separator: " · "))
                                .font(.caption)
                                .foregroundColor(.white.opacity(0.5))
                                .lineLimit(1)
                        }
                    }
                    Spacer()
                    Button(action: onStanceClick) {
                        Text(stance.label)
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(accent)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(accent.opacity(0.16))
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                    .buttonStyle(.plain)
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.white.opacity(0.35))
                }

                if item.ingredients.isEmpty {
                    Text("No ingredients on this scan.")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.5))
                } else if expanded {
                    FlowLayout(spacing: 8) {
                        ForEach(Array(item.ingredients.enumerated()), id: \.offset) { _, ing in
                            if ing.isGroup {
                                GroupIngredientChipView(ing: ing, expanded: false)
                            } else {
                                Button(action: { if let id = ing.id { onIngredientClick(id) } }) {
                                    IngredientChipView(ing: ing)
                                }
                            }
                        }
                    }
                } else {
                    CompactIngredientChipsView(
                        ingredients: item.ingredients,
                        maxVisible: collapsedChipLimit,
                        onOverflowClick: onToggle,
                        onIngredientClick: { ing in
                            if ing.isGroup {
                                onToggle()
                            } else if let id = ing.id {
                                onIngredientClick(id)
                            }
                        }
                    )
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 13)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(Color.white.opacity(0.04))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .contentShape(Rectangle())
        .onTapGesture(perform: onToggle)
    }
}
