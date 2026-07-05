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
                            
                            if let eNum = details.eNumber {
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
                                    Image(systemName: "checkmark.shield.fill")
                                        .foregroundColor(.green)
                                    Text("Not flagged in any tracked countries.")
                                        .font(.system(size: 14))
                                        .foregroundColor(.white.opacity(0.7))
                                }
                                .padding(14)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color.green.opacity(0.05))
                                .cornerRadius(12)
                            } else {
                                ForEach(details.bans, id: \.self) { ban in
                                    VStack(alignment: .leading, spacing: 6) {
                                        HStack {
                                            Text("Country: \(ban.countryCode.uppercased())")
                                                .font(.system(size: 14, weight: .bold))
                                                .foregroundColor(.white)
                                            Spacer()
                                            
                                            let isBanned = ban.status.lowercased() == "banned"
                                            Text(ban.status.uppercased())
                                                .font(.system(size: 11, weight: .bold))
                                                .foregroundColor(isBanned ? .red : .orange)
                                                .padding(.horizontal, 8)
                                                .padding(.vertical, 4)
                                                .background((isBanned ? Color.red : Color.orange).opacity(0.12))
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
