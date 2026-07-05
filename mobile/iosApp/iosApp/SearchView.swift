import SwiftUI
import composeApp

struct SearchView: View {
    @StateObject private var model = ObservableSearchModel()
    
    // Callbacks to notify parent view when item clicked
    var onConfidentMatch: (String) -> Void
    var onIngredientClick: (String) -> Void
    
    var body: some View {
        VStack(spacing: 0) {
            // Search Text Field
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.white.opacity(0.4))
                
                TextField("Search products or ingredients...", text: $model.query)
                    .foregroundColor(.white)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .onChange(of: model.query) { newValue in
                        model.search(text: newValue)
                    }
                
                if !model.query.isEmpty {
                    Button(action: { model.search(text: "") }) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.white.opacity(0.4))
                    }
                }
            }
            .padding(12)
            .background(Color.white.opacity(0.06))
            .cornerRadius(10)
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 16)
            
            // Search Results
            if model.loading {
                Spacer()
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: Color(red: 0.06, green: 0.73, blue: 0.51)))
                Spacer()
            } else if let error = model.error {
                Spacer()
                Text(error)
                    .foregroundColor(.red)
                Spacer()
            } else if model.results.products.isEmpty && model.results.ingredients.isEmpty {
                Spacer()
                if !model.query.isEmpty {
                    Text("No matches for \"\(model.query)\".")
                        .foregroundColor(.white.opacity(0.5))
                } else {
                    Text("Type to start searching...")
                        .foregroundColor(.white.opacity(0.3))
                }
                Spacer()
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        // Ingredients Section
                        if !model.results.ingredients.isEmpty {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Ingredients (\(model.results.ingredients.count))")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.white.opacity(0.5))
                                    .padding(.horizontal, 20)
                                
                                FlowLayout(spacing: 8) {
                                    ForEach(model.results.ingredients, id: \.self) { ing in
                                        Button(action: {
                                            if let ingId = ing.id {
                                                onIngredientClick(ingId)
                                            }
                                        }) {
                                            IngredientChipView(ing: ing)
                                        }
                                    }
                                }
                                .padding(.horizontal, 20)
                            }
                        }
                        
                        // Products Section
                        if !model.results.products.isEmpty {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Products (\(model.results.products.count))")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.white.opacity(0.5))
                                    .padding(.horizontal, 20)
                                
                                LazyVStack(spacing: 12) {
                                    ForEach(model.results.products, id: \.self) { product in
                                        Button(action: {
                                            onConfidentMatch(product.product.barcode)
                                        }) {
                                            ProductCardView(product: product)
                                        }
                                    }
                                }
                                .padding(.horizontal, 20)
                            }
                        }
                    }
                    .padding(.vertical, 10)
                }
            }
        }
    }
}

struct IngredientChipView: View {
    let ing: AnnotatedIngredient
    
    var color: Color {
        switch ing.severity.lowercased() {
        case "red": return Color(red: 0.96, green: 0.25, blue: 0.37)
        case "amber": return Color(red: 0.96, green: 0.62, blue: 0.04)
        case "green": return Color(red: 0.20, green: 0.83, blue: 0.60)
        default: return .white.opacity(0.6)
        }
    }
    
    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: ing.severity.lowercased() == "green" ? "checkmark.circle.fill" : (ing.severity.lowercased() == "red" ? "xmark.circle.fill" : "exclamationmark.triangle.fill"))
                .font(.system(size: 12))
                .foregroundColor(color)
            
            let label = ing.eNumber != nil ? "\(ing.name) (\(ing.eNumber!))" : ing.name
            Text(label)
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(.white)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(color.opacity(0.08))
        .cornerRadius(20)
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(color.opacity(0.2), lineWidth: 0.5)
        )
    }
}

struct ProductCardView: View {
    let product: SearchProductResult
    
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(product.product.name)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(1)
                
                Spacer()
                
                SeverityBadgeView(severity: product.severity)
            }
            
            Text("Brand: \(product.product.brand ?? "Unknown")")
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
            
            Text("Barcode: \(product.product.barcode)")
                .font(.system(size: 12))
                .foregroundColor(.gray)
        }
        .padding(18)
        .background(Color.white.opacity(0.04))
        .cornerRadius(20)
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(Color.white.opacity(0.12), lineWidth: 0.5)
        )
    }
}

struct SeverityBadgeView: View {
    let severity: String
    
    var color: Color {
        switch severity.lowercased() {
        case "red": return Color(red: 0.96, green: 0.25, blue: 0.37)
        case "amber": return Color(red: 0.96, green: 0.62, blue: 0.04)
        case "green": return Color(red: 0.20, green: 0.83, blue: 0.60)
        default: return .gray
        }
    }
    
    var body: some View {
        Text(severity.uppercased())
            .font(.system(size: 10, weight: .bold))
            .foregroundColor(color)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(color.opacity(0.12))
            .cornerRadius(8)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(color.opacity(0.4), lineWidth: 0.5)
            )
    }
}

struct BarcodeIdentifiable: Identifiable {
    let id: String
}

struct IngredientIdIdentifiable: Identifiable {
    let id: String
}
