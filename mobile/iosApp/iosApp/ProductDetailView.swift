import SwiftUI
import composeApp

struct ProductDetailView: View {
    let barcode: String
    @StateObject private var model = ObservableProductDetailModel()
    @Environment(\.dismiss) var dismiss
    
    @State private var activeIngredientId: String? = nil
    
    var body: some View {
        ZStack {
            // Dark base background
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
            } else if let details = model.product {
                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        // Header info
                        HStack(alignment: .top) {
                            VStack(alignment: .leading, spacing: 6) {
                                Text(details.product.name)
                                    .font(.system(size: 24, weight: .bold))
                                    .foregroundColor(.white)
                                    .fixedSize(horizontal: false, vertical: true)
                                
                                Text("Brand: \(details.product.brand ?? "Unknown")")
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                                
                                Text("Barcode: \(details.product.barcode)")
                                    .font(.system(size: 13))
                                    .foregroundColor(.white.opacity(0.4))
                            }
                            
                            Spacer()
                            
                            SeverityBadgeView(severity: details.severity)
                        }
                        
                        Divider()
                            .background(Color.white.opacity(0.1))
                        
                        // Ingredients Section
                        VStack(alignment: .leading, spacing: 14) {
                            Text("Analyzed Ingredients")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)
                            
                            if details.product.ingredients.isEmpty {
                                Text("No ingredients list available.")
                                    .foregroundColor(.white.opacity(0.4))
                                    .italic()
                            } else {
                                let ingredientsList = details.product.ingredients as? [AnnotatedIngredient] ?? []
                                FlowLayout(spacing: 8) {
                                    ForEach(ingredientsList, id: \.self) { annotated in
                                        Button(action: { activeIngredientId = annotated.id }) {
                                            IngredientChipView(ing: annotated)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .padding(24)
                }
            }
        }
        .navigationTitle("Product Detail")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button("Done") { dismiss() }
                    .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
            }
        }
        .onAppear {
            model.load(barcode: barcode)
        }
        .sheet(item: Binding(
            get: { activeIngredientId.map { IngredientIdIdentifiable(id: $0) } },
            set: { activeIngredientId = $0?.id }
        )) { wrapper in
            IngredientDetailView(ingredientId: wrapper.id)
        }
    }
}
