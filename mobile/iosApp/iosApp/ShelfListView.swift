import SwiftUI
import composeApp

struct ShelfListView: View {
    var onIngredientClick: (String) -> Void
    @StateObject private var model = ObservableShelfModel()

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                Text("My list")
                    .font(.system(size: 18, weight: .heavy))
                    .foregroundColor(.white)
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
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 12) {
                        ForEach(model.items, id: \.id) { item in
                            VStack(alignment: .leading, spacing: 8) {
                                Text(item.displayName)
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundColor(.white)
                                if let brand = item.brand {
                                    Text(brand).font(.caption).foregroundColor(.white.opacity(0.5))
                                }
                                FlowLayout(spacing: 8) {
                                    ForEach(item.ingredients, id: \.self) { ing in
                                        Button(action: { if let id = ing.id { onIngredientClick(id) } }) {
                                            IngredientChipView(ing: ing)
                                        }
                                    }
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(14)
                            .background(Color.white.opacity(0.04))
                            .cornerRadius(12)
                        }
                    }
                }
            }
            .padding(20)
            .background(Color(red: 0.01, green: 0.02, blue: 0.09).ignoresSafeArea())
        }
    }
}
