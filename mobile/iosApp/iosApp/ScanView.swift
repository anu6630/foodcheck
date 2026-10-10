import SwiftUI
import composeApp

struct ScanView: View {
    @StateObject private var model = ObservableScanModel()
    @Environment(\.dismiss) var dismiss
    
    var onConfidentMatch: (String) -> Void
    var onIngredientClick: (String) -> Void
    var onSavedToList: () -> Void = {}

    @State private var showSave = false
    @State private var showShare = false
    @State private var saveName = ""
    @State private var saveBrand = ""
    @State private var saveBrandId: Int? = nil
    @State private var showCompany = false
    @State private var companyQuery = ""
    @State private var selectedCompanies: [CatalogCompanyDto] = []
    @State private var caption = ""
    @State private var expandedGroups: Set<String> = []
    
    var body: some View {
        VStack(spacing: 24) {
            if model.loading {
                    Spacer()
                    VStack(spacing: 16) {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: Color(red: 0.06, green: 0.73, blue: 0.51)))
                            .scaleEffect(1.5)
                        
                        Text(model.statusText)
                            .font(.headline)
                            .foregroundColor(.white.opacity(0.8))
                    }
                    Spacer()
                } else if let error = model.error {
                    Spacer()
                    VStack(spacing: 16) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .font(.system(size: 48))
                            .foregroundColor(.red)
                        Text(error)
                            .foregroundColor(.white)
                            .multilineTextAlignment(.center)
                        
                        Button("Try Again") {
                            model.clear()
                        }
                        .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                    }
                    Spacer()
                } else if let outcome = model.outcome {
                    let ingredients = outcome.labelIngredients()
                    let similar = (outcome as? ScanOutcome.Suggestions)?.candidates ?? []
                    VStack(alignment: .leading, spacing: 14) {
                        HStack(alignment: .top, spacing: 10) {
                            Button(action: {
                                model.clear()
                                dismiss()
                            }) {
                                Image(systemName: "chevron.left")
                                    .font(.system(size: 18, weight: .semibold))
                                    .foregroundColor(.white)
                                    .frame(width: 36, height: 36)
                            }
                            VStack(alignment: .leading, spacing: 2) {
                                Text("From this label")
                                    .font(.system(size: 20, weight: .heavy))
                                    .foregroundColor(.white)
                                Text(
                                    ingredients.isEmpty
                                        ? "No substances read"
                                        : "Market IN · \(ingredients.count) substance\(ingredients.count == 1 ? "" : "s")"
                                )
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(.white.opacity(0.5))
                            }
                            Spacer()
                        }
                        Text("Watch tags are health signals — not the same as banned.")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.5))
                        if ingredients.isEmpty {
                            Text("No ingredients could be read from the photo.")
                                .foregroundColor(.white.opacity(0.5))
                        } else {
                            ScrollView {
                                VStack(alignment: .leading, spacing: 10) {
                                    ForEach(Array(ingredients.enumerated()), id: \.offset) { index, annotated in
                                        if annotated.isGroup {
                                            let key = "\(annotated.role ?? annotated.name)-\(index)"
                                            let open = expandedGroups.contains(key)
                                            Button(action: {
                                                if open { expandedGroups.remove(key) } else { expandedGroups.insert(key) }
                                            }) {
                                                GroupIngredientChipView(ing: annotated, expanded: open)
                                            }
                                            if open {
                                                FlowLayout(spacing: 8) {
                                                    ForEach(annotated.members, id: \.self) { member in
                                                        Button(action: {
                                                            if let id = member.id { onIngredientClick(id) }
                                                        }) {
                                                            IngredientChipView(ing: member)
                                                        }
                                                    }
                                                }
                                                if let label = annotated.labelText, !label.isEmpty {
                                                    Text("as on label: \(label)")
                                                        .font(.caption2)
                                                        .foregroundColor(.white.opacity(0.45))
                                                }
                                            }
                                        } else {
                                            Button(action: {
                                                if let id = annotated.id { onIngredientClick(id) }
                                            }) {
                                                IngredientChipView(ing: annotated)
                                            }
                                        }
                                    }
                                }
                            }
                            .frame(maxHeight: 280)
                        }
                        HStack {
                            Button("Save to my list") { showSave = true }
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                            if FeatureFlags.shared.socialFeedEnabled {
                                Button("Share as story") { showShare = true }
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                            }
                        }
                        if !similar.isEmpty {
                            Text("Similar packs")
                                .font(.subheadline)
                                .foregroundColor(.white)
                            Text("These are similar packs in the catalog — not a confirmed match.")
                                .font(.caption)
                                .foregroundColor(.white.opacity(0.5))
                            ScrollView {
                                LazyVStack(spacing: 10) {
                                    ForEach(similar, id: \.self) { candidate in
                                        Button(action: {
                                            onConfidentMatch(candidate.product.barcode)
                                        }) {
                                            HStack {
                                                VStack(alignment: .leading, spacing: 4) {
                                                    Text(candidate.product.name)
                                                        .font(.system(size: 14, weight: .bold))
                                                        .foregroundColor(.white)
                                                        .lineLimit(1)
                                                    Text("Brand: \(candidate.product.brand ?? "Unknown")")
                                                        .font(.system(size: 12))
                                                        .foregroundColor(.gray)
                                                }
                                                Spacer()
                                                Text("\(candidate.matchScore)%")
                                                    .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                                            }
                                            .padding(16)
                                            .background(Color.white.opacity(0.04))
                                            .cornerRadius(12)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                    .sheet(isPresented: $showSave) {
                        NavigationStack {
                            Form {
                                Section("Pack") {
                                    TextField("Name this pack", text: $saveName)
                                    TextField("Brand", text: $saveBrand)
                                        .onChange(of: saveBrand) { _, newValue in
                                            saveBrandId = nil
                                            model.searchBrands(query: newValue)
                                        }
                                    if !model.brandSuggestions.isEmpty {
                                        ScrollView(.horizontal, showsIndicators: false) {
                                            HStack(spacing: 8) {
                                                ForEach(model.brandSuggestions.prefix(6), id: \.id) { brand in
                                                    Button(brand.name) {
                                                        saveBrand = brand.name
                                                        saveBrandId = Int(brand.id)
                                                        if let cidObj = brand.companyId,
                                                           let cname = brand.companyName {
                                                            let cid = cidObj.intValue
                                                            if !selectedCompanies.contains(where: { Int($0.id) == cid }) {
                                                                selectedCompanies.append(
                                                                    CatalogCompanyDto(id: Int32(cid), name: cname)
                                                                )
                                                                showCompany = true
                                                            }
                                                        }
                                                        model.searchBrands(query: brand.name)
                                                    }
                                                    .font(.system(size: 12, weight: .medium))
                                                    .padding(.horizontal, 10)
                                                    .padding(.vertical, 6)
                                                    .background(Color.white.opacity(0.08))
                                                    .clipShape(Capsule())
                                                }
                                            }
                                        }
                                    }
                                }
                                if !showCompany {
                                    Section {
                                        Button {
                                            showCompany = true
                                            model.searchCompanies(query: "")
                                        } label: {
                                            Label("Company", systemImage: "plus")
                                        }
                                    }
                                } else {
                                    Section("Related companies (optional)") {
                                        TextField("Search companies", text: $companyQuery)
                                            .onChange(of: companyQuery) { _, newValue in
                                                model.searchCompanies(query: newValue)
                                            }
                                        ForEach(
                                            model.companySuggestions.filter { c in
                                                !selectedCompanies.contains(where: { $0.id == c.id })
                                            }.prefix(8),
                                            id: \.id
                                        ) { company in
                                            Button(company.name) {
                                                selectedCompanies.append(company)
                                            }
                                        }
                                        ForEach(selectedCompanies, id: \.id) { company in
                                            HStack {
                                                Text(company.name)
                                                Spacer()
                                                Button(role: .destructive) {
                                                    selectedCompanies.removeAll { $0.id == company.id }
                                                } label: {
                                                    Image(systemName: "xmark.circle.fill")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            .scrollContentBackground(.hidden)
                            .background(Color(red: 0.06, green: 0.08, blue: 0.14))
                            .navigationTitle("Save to my list")
                            .navigationBarTitleDisplayMode(.inline)
                            .toolbar {
                                ToolbarItem(placement: .cancellationAction) {
                                    Button("Close") { showSave = false }
                                }
                                ToolbarItem(placement: .confirmationAction) {
                                    Button("Save") {
                                        model.saveToShelf(
                                            name: saveName,
                                            brand: saveBrand,
                                            brandId: saveBrandId,
                                            companyIds: selectedCompanies.map { Int($0.id) }
                                        )
                                        showSave = false
                                        saveName = ""
                                        saveBrand = ""
                                        saveBrandId = nil
                                        selectedCompanies = []
                                        showCompany = false
                                        companyQuery = ""
                                    }
                                    .disabled(saveName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                                }
                            }
                            .onAppear {
                                model.searchBrands(query: saveBrand)
                            }
                        }
                        .presentationDetents([.medium, .large])
                        .presentationDragIndicator(.visible)
                    }
                    .sheet(isPresented: $showShare) {
                        NavigationStack {
                            Form {
                                Section {
                                    Text("Posts this label in the app. Not an official product entry.")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                    TextField("Caption (optional)", text: $caption, axis: .vertical)
                                        .lineLimit(2...4)
                                }
                            }
                            .scrollContentBackground(.hidden)
                            .background(Color(red: 0.06, green: 0.08, blue: 0.14))
                            .navigationTitle("Share as story")
                            .navigationBarTitleDisplayMode(.inline)
                            .toolbar {
                                ToolbarItem(placement: .cancellationAction) {
                                    Button("Close") { showShare = false }
                                }
                                ToolbarItem(placement: .confirmationAction) {
                                    Button("Publish") {
                                        model.publishStory(caption: caption)
                                        showShare = false
                                        caption = ""
                                    }
                                }
                            }
                        }
                        .presentationDetents([.medium])
                        .presentationDragIndicator(.visible)
                    }
                    .alert("Done", isPresented: Binding(
                        get: { model.actionMessage != nil },
                        set: { if !$0 { model.clearActionMessage() } }
                    )) {
                        Button("OK") { model.clearActionMessage() }
                    } message: {
                        Text(model.actionMessage ?? "")
                    }
                } else {
                    Spacer()
                    
                    // Pulsing custom scanner target
                    ScannerTargetView()
                    
                    Spacer()
                    
                    // Glass trigger buttons
                    HStack(spacing: 16) {
                        Button(action: { triggerPicker(source: .camera) }) {
                            VStack(spacing: 12) {
                                Image(systemName: "camera.shutter.button.fill")
                                    .font(.system(size: 26))
                                    .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                                    .frame(width: 56, height: 56)
                                    .background(Color(red: 0.06, green: 0.73, blue: 0.51).opacity(0.12))
                                    .clipShape(Circle())
                                    .overlay(Circle().stroke(Color(red: 0.06, green: 0.73, blue: 0.51).opacity(0.3), lineWidth: 0.5))
                                
                                Text("Camera Scan")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.white)
                                
                                Text("Take a label photo")
                                    .font(.system(size: 11))
                                    .foregroundColor(.white.opacity(0.4))
                                    .multilineTextAlignment(.center)
                            }
                            .padding(.vertical, 24)
                            .frame(maxWidth: .infinity)
                            .background(.ultraThinMaterial.opacity(0.95))
                            .cornerRadius(20)
                            .overlay(
                                RoundedRectangle(cornerRadius: 20)
                                    .stroke(Color.white.opacity(0.12), lineWidth: 0.5)
                            )
                        }
                        .buttonStyle(ScaleButtonStyle())
                        
                        Button(action: { triggerPicker(source: .gallery) }) {
                            VStack(spacing: 12) {
                                Image(systemName: "photo.fill.on.rectangle.fill")
                                    .font(.system(size: 24))
                                    .foregroundColor(.white.opacity(0.8))
                                    .frame(width: 56, height: 56)
                                    .background(Color.white.opacity(0.06))
                                    .clipShape(Circle())
                                    .overlay(Circle().stroke(Color.white.opacity(0.15), lineWidth: 0.5))
                                
                                Text("Upload Image")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.white)
                                
                                Text("Select from library")
                                    .font(.system(size: 11))
                                    .foregroundColor(.white.opacity(0.4))
                                    .multilineTextAlignment(.center)
                            }
                            .padding(.vertical, 24)
                            .frame(maxWidth: .infinity)
                            .background(.ultraThinMaterial.opacity(0.95))
                            .cornerRadius(20)
                            .overlay(
                                RoundedRectangle(cornerRadius: 20)
                                    .stroke(Color.white.opacity(0.12), lineWidth: 0.5)
                            )
                        }
                        .buttonStyle(ScaleButtonStyle())
                    }
                    .padding(.horizontal, 20)
                    
                    Spacer()
                }
            }
        }
    
    private func triggerPicker(source: ImagePickerSource) {
        guard let controller = topMostViewController() else { return }
        ImagePickerPresenter.present(source: source, from: controller) { data in
            if let data = data {
                model.scan(image: data)
            }
        }
    }
}

struct ScannerTargetView: View {
    @State private var rotateDashes = 0.0
    @State private var scalePulse = 1.0
    
    var body: some View {
        VStack(spacing: 12) {
            ZStack {
                // Rotating outer dashed border
                Circle()
                    .stroke(style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round, dash: [8, 8]))
                    .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51).opacity(0.6))
                    .frame(width: 190, height: 190)
                    .rotationEffect(.degrees(rotateDashes))
                    
                // Corner brackets mimicking scanner framing guides
                ScannerBrackets()
                    .stroke(Color.white.opacity(0.25), lineWidth: 2)
                    .frame(width: 220, height: 220)
                
                // Pulsing inner lens
                Circle()
                    .fill(
                        RadialGradient(
                            colors: [Color(red: 0.06, green: 0.73, blue: 0.51).opacity(0.12), .clear],
                            center: .center,
                            startRadius: 0,
                            endRadius: 80
                        )
                    )
                    .frame(width: 160, height: 160)
                    .scaleEffect(scalePulse)
                
                Image(systemName: "camera.viewfinder")
                    .font(.system(size: 40))
                    .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
            }
            .frame(height: 240)
            .onAppear {
                withAnimation(.linear(duration: 20).repeatForever(autoreverses: false)) {
                    rotateDashes = 360
                }
                withAnimation(.easeInOut(duration: 1.8).repeatForever(autoreverses: true)) {
                    scalePulse = 1.1
                }
            }
            
            Text("Align ingredients label inside the camera frame")
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(.white.opacity(0.4))
        }
    }
}

struct ScannerBrackets: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let length: CGFloat = 20
        
        // Top Left
        path.move(to: CGPoint(x: rect.minX, y: rect.minY + length))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.minX + length, y: rect.minY))
        
        // Top Right
        path.move(to: CGPoint(x: rect.maxX - length, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY + length))
        
        // Bottom Left
        path.move(to: CGPoint(x: rect.minX, y: rect.maxY - length))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX + length, y: rect.maxY))
        
        // Bottom Right
        path.move(to: CGPoint(x: rect.maxX - length, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY - length))
        
        return path
    }
}
