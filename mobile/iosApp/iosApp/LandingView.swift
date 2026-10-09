import SwiftUI
import composeApp

enum LandingTab {
    case scan
    case search
    case feed
}

struct LandingView: View {
    @State private var animateBlob1 = false
    @State private var animateBlob2 = false
    @State private var selectedTab: LandingTab = .scan
    
    @State private var activeBarcode: String? = nil
    @State private var activeIngredientId: String? = nil
    @State private var isSearching = false
    @State private var showSubmitSheet = false
    @State private var showMyList = false
    
    var body: some View {
        NavigationStack {
            ZStack {
                // Main Content
                VStack(spacing: 0) {
                    // Header Bar
                    HStack(spacing: 12) {
                        if isSearching && selectedTab == .search {
                            Button(action: {
                                withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                                    isSearching = false
                                }
                            }) {
                                Image(systemName: "chevron.left")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(.white)
                                    .frame(width: 36, height: 36)
                                    .background(.ultraThinMaterial.opacity(0.85))
                                    .clipShape(Circle())
                                    .overlay(
                                        Circle().stroke(Color.white.opacity(0.15), lineWidth: 0.5)
                                    )
                            }
                            .transition(.asymmetric(
                                insertion: .scale.combined(with: .opacity),
                                removal: .scale.combined(with: .opacity)
                            ))
                        }
                        
                        HStack(alignment: .lastTextBaseline, spacing: 1) {
                            Text("Life")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(.white.opacity(0.9))
                            Text("X")
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
                            },
                            onSavedToList: { showMyList = true }
                        )
                    case .search:
                        SearchView(
                            isSearching: $isSearching,
                            onConfidentMatch: { barcode in
                                self.activeBarcode = barcode
                            },
                            onIngredientClick: { ingId in
                                self.activeIngredientId = ingId
                            },
                            onMyListClick: { showMyList = true }
                        )
                    case .feed:
                        FeedView(onIngredientClick: { ingId in
                            self.activeIngredientId = ingId
                        })
                    }
                    
                    Spacer()
                        .frame(height: 90) // Space for the floating tab bar
                }
                
                // Floating Tab Bar overlay
                if !isSearching || selectedTab != .search {
                    VStack {
                        Spacer()
                        
                        HStack(spacing: 20) {
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

                            TabButton(
                                icon: "bubble.left.and.bubble.right",
                                title: "Feed",
                                isActive: selectedTab == .feed
                            ) {
                                withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                                    selectedTab = .feed
                                }
                            }

                            TabButton(
                                icon: "plus.circle",
                                title: "Submit",
                                isActive: false
                            ) {
                                showSubmitSheet = true
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
                    .transition(.move(edge: .bottom).combined(with: .opacity))
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
            .sheet(isPresented: $showSubmitSheet) {
                SubmitRequestView()
            }
            .sheet(isPresented: $showMyList) {
                ShelfListView(onIngredientClick: { ingId in
                    showMyList = false
                    self.activeIngredientId = ingId
                })
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
