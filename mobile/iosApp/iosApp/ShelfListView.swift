import SwiftUI
import composeApp

private let collapsedChipLimit = 4

struct ShelfListView: View {
    var onIngredientClick: (String) -> Void
    @StateObject private var model = ObservableShelfModel()
    @State private var expandedIds: Set<Int32> = []

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                Text("Only this device. Not a public product catalog.")
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.5))
                TextField("Search mine", text: $model.query)
                    .padding(12)
                    .background(Color.white.opacity(0.06))
                    .cornerRadius(10)
                    .foregroundColor(.white)
                    .onChange(of: model.query) { newValue in
                        model.search(newValue)
                    }
                if let error = model.error {
                    Text(error).foregroundColor(.red).font(.caption)
                }
                if model.items.isEmpty {
                    Text("No saved scans yet. Save one from a label scan.")
                        .font(.subheadline)
                        .foregroundColor(.white.opacity(0.5))
                } else {
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 12) {
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
                                    onIngredientClick: onIngredientClick
                                )
                            }
                        }
                    }
                }
            }
            .padding(20)
            .background(Color(red: 0.01, green: 0.02, blue: 0.09).ignoresSafeArea())
        }
    }
}

struct ShelfScanCardView: View {
    let item: ShelfItemDto
    let expanded: Bool
    let onToggle: () -> Void
    let onIngredientClick: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.displayName)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(.white)
                        .lineLimit(1)
                    let brand = item.brand?.trimmingCharacters(in: .whitespacesAndNewlines)
                    let companies = (item.companies as? [CatalogCompanyDto] ?? [])
                        .map(\.name)
                        .joined(separator: ", ")
                    if let brand, !brand.isEmpty, !companies.isEmpty {
                        Text("\(brand) · \(companies)")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.5))
                            .lineLimit(1)
                    } else if let brand, !brand.isEmpty {
                        Text(brand)
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.5))
                            .lineLimit(1)
                    } else if !companies.isEmpty {
                        Text(companies)
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.5))
                            .lineLimit(1)
                    }
                }
                Spacer()
                if item.ingredients.count > collapsedChipLimit || expanded {
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.white.opacity(0.45))
                }
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
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Color.white.opacity(0.04))
        .cornerRadius(12)
        .contentShape(Rectangle())
        .onTapGesture(perform: onToggle)
    }
}
