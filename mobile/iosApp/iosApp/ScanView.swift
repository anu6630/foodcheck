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
    @State private var showTags = false
    @State private var companyQuery = ""
    @State private var selectedCompanies: [CatalogCompanyDto] = []
    @State private var saveStance: ShelfStance = .unsure
    @State private var selectedTags: [String] = []
    @State private var caption = ""
    @State private var expandedGroups: Set<String> = []
    @State private var toastMessage: String? = nil
    @State private var errorDialog: String? = nil
    private let tagOptions = ["Kids", "Adults", "Elderly", "Home"]
    
    var body: some View {
        ZStack {
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
                            .frame(maxHeight: .infinity)
                        }
                        if FeatureFlags.shared.socialFeedEnabled {
                            Button("Share as story") { showShare = true }
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
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
                        Spacer(minLength: 0)
                        Button(action: { showSave = true }) {
                            Text("Save to my list")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 48)
                                .background(Color(red: 0.06, green: 0.73, blue: 0.51))
                                .clipShape(RoundedRectangle(cornerRadius: 14))
                        }
                        .padding(.top, 8)
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
                                Section("Stance") {
                                    Picker("Stance", selection: $saveStance) {
                                        Text("Avoid").tag(ShelfStance.avoid)
                                        Text("Occasional").tag(ShelfStance.occasional)
                                        Text("OK").tag(ShelfStance.ok)
                                        Text("Unsure").tag(ShelfStance.unsure)
                                    }
                                    .pickerStyle(.segmented)
                                }
                                if !showCompany || !showTags {
                                    Section {
                                        if !showCompany {
                                            Button {
                                                showCompany = true
                                                model.searchCompanies(query: "")
                                            } label: {
                                                Label("Company", systemImage: "plus")
                                            }
                                        }
                                        if !showTags {
                                            Button {
                                                showTags = true
                                            } label: {
                                                Label("Tag", systemImage: "plus")
                                            }
                                        }
                                    }
                                }
                                if showCompany {
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
                                if showTags {
                                    Section("Tags (optional)") {
                                        ForEach(tagOptions, id: \.self) { tag in
                                            Button {
                                                if let idx = selectedTags.firstIndex(of: tag) {
                                                    selectedTags.remove(at: idx)
                                                } else {
                                                    selectedTags.append(tag)
                                                }
                                            } label: {
                                                HStack {
                                                    Text(tag)
                                                    Spacer()
                                                    if selectedTags.contains(tag) {
                                                        Image(systemName: "checkmark")
                                                    }
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
                                        .disabled(model.saving)
                                }
                                ToolbarItem(placement: .confirmationAction) {
                                    Button(model.saving ? "Saving…" : "Save") {
                                        model.saveToShelf(
                                            name: saveName,
                                            brand: saveBrand,
                                            brandId: saveBrandId,
                                            companyIds: selectedCompanies.map { Int($0.id) },
                                            stance: saveStance.apiValue,
                                            tags: selectedTags
                                        )
                                    }
                                    .disabled(
                                        model.saving ||
                                        saveName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                                    )
                                }
                            }
                            .interactiveDismissDisabled(model.saving)
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
                    .onChange(of: model.actionMessage) { _, _ in
                        handleSaveActionMessage()
                    }
                    .onChange(of: model.saving) { _, saving in
                        if !saving { handleSaveActionMessage() }
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
            if model.saving && !showSave {
                Color.black.opacity(0.35).ignoresSafeArea()
                HStack(spacing: 12) {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: Color(red: 0.06, green: 0.73, blue: 0.51)))
                    Text("Saving…")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.white)
                }
                .padding(.horizontal, 22)
                .padding(.vertical, 16)
                .background(Color(red: 0.06, green: 0.08, blue: 0.14))
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }
            if let toastMessage {
                VStack {
                    Spacer()
                    HStack(spacing: 10) {
                        Image(systemName: "checkmark")
                            .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                        Text(toastMessage)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(.white)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                    .frame(maxWidth: .infinity)
                    .background(Color(red: 0.1, green: 0.12, blue: 0.21).opacity(0.96))
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .padding(.horizontal, 24)
                    .padding(.bottom, 28)
                }
            }
            if let errorDialog {
                Color.black.opacity(0.45).ignoresSafeArea()
                    .onTapGesture { self.errorDialog = nil }
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("Couldn’t save")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                        Spacer()
                        Button { self.errorDialog = nil } label: {
                            Image(systemName: "xmark")
                                .foregroundColor(.white.opacity(0.5))
                        }
                    }
                    Text(errorDialog)
                        .font(.system(size: 13))
                        .foregroundColor(.white.opacity(0.6))
                    Button("OK") { self.errorDialog = nil }
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color(red: 0.06, green: 0.73, blue: 0.51))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .padding(18)
                .background(Color(red: 0.06, green: 0.08, blue: 0.14))
                .clipShape(RoundedRectangle(cornerRadius: 18))
                .padding(.horizontal, 28)
            }
        }
    }
    
    private func handleSaveActionMessage() {
        guard let msg = model.actionMessage, !model.saving else { return }
        if msg == "Saved to your list" {
            showSave = false
            saveName = ""
            saveBrand = ""
            saveBrandId = nil
            selectedCompanies = []
            showCompany = false
            showTags = false
            companyQuery = ""
            saveStance = .unsure
            selectedTags = []
            toastMessage = msg
            model.clearActionMessage()
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.1) {
                model.clear()
                onSavedToList()
                dismiss()
            }
        } else {
            errorDialog = msg
            model.clearActionMessage()
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
