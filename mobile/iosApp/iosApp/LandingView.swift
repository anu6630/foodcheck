import SwiftUI
import composeApp

enum LandingTab {
    case scan
    case search
}

struct LandingView: View {
    @State private var animateBlob1 = false
    @State private var animateBlob2 = false
    @State private var selectedTab: LandingTab = .scan
    
    @State private var activeBarcode: String? = nil
    @State private var activeIngredientId: String? = nil
    
    var body: some View {
        NavigationStack {
            ZStack {
                // Main Content
                VStack(spacing: 0) {
                    // Header Bar
                    HStack {
                        HStack(alignment: .lastTextBaseline, spacing: 1) {
                            Text("life")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(.white.opacity(0.9))
                            Text("Ex")
                                .font(.system(size: 16, weight: .black))
                                .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                            Text(" Food")
                                .font(.custom("Georgia-BoldItalic", size: 25))
                                .foregroundColor(.white)
                        }
                        Spacer()
                        
                        Circle()
                            .fill(Color(red: 0.06, green: 0.73, blue: 0.51))
                            .frame(width: 8, height: 8)
                    }
                    .padding(.horizontal, 24)
                    .padding(.top, 16)
                    .padding(.bottom, 8)
                    
                    // Embedded Tab Content
                    switch selectedTab {
                    case .scan:
                        ScanView(
                            onConfidentMatch: { barcode in
                                self.activeBarcode = barcode
                            },
                            onIngredientClick: { ingId in
                                self.activeIngredientId = ingId
                            }
                        )
                    case .search:
                        SearchView(
                            onConfidentMatch: { barcode in
                                self.activeBarcode = barcode
                            },
                            onIngredientClick: { ingId in
                                self.activeIngredientId = ingId
                            }
                        )
                    }
                    
                    Spacer()
                        .frame(height: 90) // Space for the floating tab bar
                }
                
                // Floating Tab Bar overlay
                VStack {
                    Spacer()
                    
                    HStack(spacing: 64) {
                        TabButton(
                            icon: "doc.text.viewfinder",
                            title: "Scan",
                            isActive: selectedTab == .scan
                        ) {
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                                selectedTab = .scan
                            }
                        }
                        
                        TabButton(
                            icon: "magnifyingglass",
                            title: "Search",
                            isActive: selectedTab == .search
                        ) {
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                                selectedTab = .search
                            }
                        }
                    }
                    .padding(.horizontal, 40)
                    .padding(.vertical, 12)
                    .background(.ultraThinMaterial.opacity(0.85))
                    .clipShape(Capsule())
                    .overlay(
                        Capsule()
                            .stroke(Color.white.opacity(0.15), lineWidth: 0.5)
                    )
                    .shadow(color: Color.black.opacity(0.35), radius: 15, x: 0, y: 8)
                    .padding(.bottom, 20)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(
                ZStack {
                    Color(red: 0.01, green: 0.02, blue: 0.09)
                    
                    // Animated glowing blob 1 (Emerald)
                    Circle()
                        .fill(Color(red: 0.06, green: 0.73, blue: 0.51).opacity(0.25))
                        .frame(width: 450, height: 450)
                        .blur(radius: 70)
                        .offset(x: animateBlob1 ? 150 : -100, y: animateBlob1 ? -200 : 100)
                    
                    // Animated glowing blob 2 (Indigo)
                    Circle()
                        .fill(Color(red: 0.39, green: 0.40, blue: 0.95).opacity(0.20))
                        .frame(width: 500, height: 500)
                        .blur(radius: 80)
                        .offset(x: animateBlob2 ? -150 : 150, y: animateBlob2 ? 150 : -150)
                    
                    // Frosted glass overlay
                    Rectangle()
                        .fill(.ultraThinMaterial.opacity(0.2))
                }
                .ignoresSafeArea()
            )
            .navigationBarHidden(true)
            .sheet(item: Binding(
                get: { activeBarcode.map { BarcodeIdentifiable(id: $0) } },
                set: { activeBarcode = $0?.id }
            )) { wrapper in
                ProductDetailView(barcode: wrapper.id)
            }
            .sheet(item: Binding(
                get: { activeIngredientId.map { IngredientIdIdentifiable(id: $0) } },
                set: { activeIngredientId = $0?.id }
            )) { wrapper in
                IngredientDetailView(ingredientId: wrapper.id)
            }
            .onAppear {
                withAnimation(.easeInOut(duration: 25).repeatForever(autoreverses: true)) {
                    animateBlob1 = true
                }
                withAnimation(.easeInOut(duration: 28).repeatForever(autoreverses: true)) {
                    animateBlob2 = true
                }
            }
        }
    }
}

struct TabButton: View {
    let icon: String
    let title: String
    let isActive: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.system(size: 22, weight: isActive ? .bold : .medium))
                    .foregroundColor(isActive ? Color(red: 0.06, green: 0.73, blue: 0.51) : .white.opacity(0.4))
                    .scaleEffect(isActive ? 1.15 : 1.0)
                
                Text(title)
                    .font(.system(size: 11, weight: isActive ? .bold : .medium))
                    .foregroundColor(isActive ? .white : .white.opacity(0.4))
            }
            .frame(width: 60)
        }
        .buttonStyle(ScaleButtonStyle())
    }
}

// Subtle click/tap feedback scale animation
struct ScaleButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.98 : 1.0)
            .opacity(configuration.isPressed ? 0.95 : 1.0)
            .animation(.easeOut(duration: 0.15), value: configuration.isPressed)
    }
}
