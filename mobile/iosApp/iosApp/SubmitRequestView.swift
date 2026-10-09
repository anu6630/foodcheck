import SwiftUI
import PhotosUI
import composeApp

// ---------------------------------------------------------------------------
// MARK: - SubmitRequestView
// ---------------------------------------------------------------------------

struct SubmitRequestView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel = SubmitRequestIOSViewModel()

    @State private var selectedTab = 0

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Tab Picker
                Picker("Request Type", selection: $selectedTab) {
                    Label("Report Product", systemImage: "shippingbox").tag(0)
                    Label("Check Ingredient", systemImage: "flask").tag(1)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
                .onChange(of: selectedTab) { _ in viewModel.reset() }

                Divider().background(Color.white.opacity(0.08))

                // Content
                ScrollView {
                    if viewModel.submitted {
                        SuccessView(requestId: viewModel.requestId, onDone: { dismiss() })
                    } else if selectedTab == 0 {
                        ProductRequestForm(viewModel: viewModel)
                    } else {
                        IngredientRequestForm(viewModel: viewModel)
                    }
                }
            }
            .background(Color(red: 0.01, green: 0.02, blue: 0.09).ignoresSafeArea())
            .navigationTitle("Submit a Request")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }
                        .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// MARK: - Product Form
// ---------------------------------------------------------------------------

private struct ProductRequestForm: View {
    @ObservedObject var viewModel: SubmitRequestIOSViewModel

    @State private var showPhotoPicker = false
    @State private var selectedItems: [PhotosPickerItem] = []

    private let countries = [
        ("IN", "India"), ("AU", "Australia"), ("US", "United States"),
        ("EU", "European Union"), ("GB", "United Kingdom"),
        ("CA", "Canada"), ("SG", "Singapore"), ("AE", "UAE"),
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Group {
                SectionHeader("Product Details")
                FormField("Product Name *", text: $viewModel.productName, placeholder: "e.g. KitKat Dark")
                FormField("Brand", text: $viewModel.brand, placeholder: "e.g. Nestle")
                FormField("Barcode / EAN", text: $viewModel.barcode, placeholder: "e.g. 8901058857781", keyboardType: .numberPad)
                FormField("Category", text: $viewModel.category, placeholder: "e.g. Confectionery, Beverage")

                VStack(alignment: .leading, spacing: 6) {
                    Text("Country *")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(Color.white.opacity(0.6))
                    Picker("Country", selection: $viewModel.countryCode) {
                        ForEach(countries, id: \.0) { code, name in
                            Text("\(name) (\(code))").tag(code)
                        }
                    }
                    .pickerStyle(.menu)
                    .accentColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(Color.white.opacity(0.06))
                    .cornerRadius(10)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.white.opacity(0.12), lineWidth: 1))
                }
            }

            SectionHeader("Ingredients")
            FormField("Ingredients List", text: $viewModel.ingredientsText,
                      placeholder: "Paste ingredients from the label...", axis: .vertical, minHeight: 80)
            .onChange(of: viewModel.ingredientsText) { text in
                viewModel.searchIngredientRealtime(query: text)
            }

            if viewModel.ingredientsText.trimmingCharacters(in: .whitespaces).count >= 2 {
                IngredientMatchesIOSPanel(
                    ingredientName: viewModel.ingredientsText,
                    suggestions: viewModel.ingredientSuggestions,
                    isSearching: viewModel.isSearchingIngredients,
                    onSelect: nil,
                    onConfirmNew: nil
                )
            }

            SectionHeader("Product Images (up to 3)")
            HStack(spacing: 8) {
                ForEach(viewModel.images.indices, id: \.self) { idx in
                    ZStack(alignment: .topTrailing) {
                        Image(uiImage: UIImage(data: viewModel.images[idx]) ?? UIImage())
                            .resizable()
                            .scaledToFill()
                            .frame(width: 72, height: 72)
                            .clipped()
                            .cornerRadius(10)
                        Button {
                            viewModel.images.remove(at: idx)
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(.white)
                                .background(Color.red)
                                .clipShape(Circle())
                                .font(.system(size: 16))
                        }
                        .offset(x: 4, y: -4)
                    }
                }
                if viewModel.images.count < 3 {
                    PhotosPicker(selection: $selectedItems, maxSelectionCount: 3 - viewModel.images.count, matching: .images) {
                        VStack(spacing: 4) {
                            Image(systemName: "plus.circle")
                                .font(.system(size: 24))
                                .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                            Text("Add Photo")
                                .font(.system(size: 11))
                                .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                        }
                        .frame(width: 72, height: 72)
                        .background(Color(red: 0.06, green: 0.73, blue: 0.51).opacity(0.08))
                        .cornerRadius(10)
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color(red: 0.06, green: 0.73, blue: 0.51).opacity(0.3), lineWidth: 1))
                    }
                    .onChange(of: selectedItems) { items in
                        for item in items {
                            item.loadTransferable(type: Data.self) { result in
                                if case .success(let data) = result, let data = data {
                                    DispatchQueue.main.async {
                                        if viewModel.images.count < 3 {
                                            viewModel.images.append(data)
                                        }
                                    }
                                }
                            }
                        }
                        selectedItems = []
                    }
                }
            }

            FormField("Notes", text: $viewModel.notes, placeholder: "Any notes for the reviewer", axis: .vertical, minHeight: 60)

            if let error = viewModel.errorMessage {
                Text(error)
                    .font(.system(size: 13))
                    .foregroundColor(Color(red: 0.96, green: 0.25, blue: 0.37))
            }

            SubmitButton(isLoading: viewModel.isLoading, label: "Submit Product Request") {
                viewModel.submitProduct()
            }

            Spacer(minLength: 40)
        }
        .padding(20)
    }
}

// ---------------------------------------------------------------------------
// MARK: - Ingredient Form
// ---------------------------------------------------------------------------

private struct IngredientRequestForm: View {
    @ObservedObject var viewModel: SubmitRequestIOSViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            SectionHeader("Ingredient Details")

            Text("Ask us to investigate a specific ingredient's regulatory status across different countries.")
                .font(.system(size: 13))
                .foregroundColor(Color.white.opacity(0.5))

            FormField("Ingredient Name *", text: $viewModel.ingredientName, placeholder: "e.g. Titanium Dioxide")
                .onChange(of: viewModel.ingredientName) { name in
                    viewModel.selectedFromDb = false
                    viewModel.isConfirmedNew = false
                    viewModel.searchIngredientRealtime(query: name)
                }

            // Realtime Search Matches Box
            if viewModel.ingredientName.trimmingCharacters(in: .whitespaces).count >= 2 && !viewModel.selectedFromDb && !viewModel.isConfirmedNew {
                IngredientMatchesIOSPanel(
                    ingredientName: viewModel.ingredientName,
                    suggestions: viewModel.ingredientSuggestions,
                    isSearching: viewModel.isSearchingIngredients,
                    onSelect: { dict in
                        if let name = dict["name"] as? String {
                            viewModel.ingredientName = name
                        }
                        if let eNum = dict["e_number"] as? String {
                            viewModel.eNumber = eNum
                        }
                        if let cat = dict["category"] as? String {
                            viewModel.ingredientCategory = cat
                        }
                        viewModel.selectedFromDb = true
                        viewModel.isConfirmedNew = false
                        viewModel.ingredientSuggestions = []
                    },
                    onConfirmNew: {
                        viewModel.isConfirmedNew = true
                        viewModel.ingredientSuggestions = []
                    }
                )
            } else if viewModel.selectedFromDb {
                HStack(spacing: 8) {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                    Text("Auto-filled from FoodCheck Database!")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(red: 0.06, green: 0.73, blue: 0.51).opacity(0.12))
                .cornerRadius(10)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color(red: 0.06, green: 0.73, blue: 0.51).opacity(0.3), lineWidth: 1))
            } else if viewModel.isConfirmedNew {
                HStack(spacing: 8) {
                    Image(systemName: "sparkles")
                        .foregroundColor(Color(red: 0.5, green: 0.5, blue: 0.98))
                    Text("Requesting as a NEW ingredient entry! Fill in optional details below and click Submit.")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(Color(red: 0.65, green: 0.65, blue: 1.0))
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(red: 0.39, green: 0.40, blue: 0.95).opacity(0.12))
                .cornerRadius(10)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color(red: 0.39, green: 0.40, blue: 0.95).opacity(0.3), lineWidth: 1))
            }

            FormField("INS / E-number", text: $viewModel.eNumber, placeholder: "e.g. INS 171 or E171")
            FormField("Category", text: $viewModel.ingredientCategory, placeholder: "e.g. colour, preservative")
            FormField("Notes / Question", text: $viewModel.ingredientNotes,
                      placeholder: "e.g. Is this banned in EU food products?", axis: .vertical, minHeight: 70)

            if let error = viewModel.errorMessage {
                Text(error)
                    .font(.system(size: 13))
                    .foregroundColor(Color(red: 0.96, green: 0.25, blue: 0.37))
            }

            SubmitButton(
                isLoading: viewModel.isLoading,
                label: viewModel.selectedFromDb ? "Submit Check Request" : "Submit New Ingredient Request"
            ) {
                viewModel.submitIngredient()
            }

            Spacer(minLength: 40)
        }
        .padding(20)
    }
}

// ---------------------------------------------------------------------------
// MARK: - Realtime Matches Panel Component
// ---------------------------------------------------------------------------

private struct IngredientMatchesIOSPanel: View {
    let ingredientName: String
    let suggestions: [[String: Any]]
    let isSearching: Bool
    let onSelect: (([String: Any]) -> Void)?
    let onConfirmNew: (() -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                if isSearching {
                    ProgressView().scaleEffect(0.7).tint(Color(red: 0.06, green: 0.73, blue: 0.51))
                    Text("Searching database...").font(.system(size: 12)).foregroundColor(Color.white.opacity(0.5))
                } else if !suggestions.isEmpty {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 12))
                        .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                    Text("Existing DB Matches (\(suggestions.count)):")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                } else {
                    Image(systemName: "info.circle")
                        .font(.system(size: 12))
                        .foregroundColor(Color(red: 0.5, green: 0.5, blue: 0.98))
                    Text("No exact database match found")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Color(red: 0.65, green: 0.65, blue: 1.0))
                }
            }

            if !isSearching && !suggestions.isEmpty {
                VStack(spacing: 6) {
                    ForEach(Array(suggestions.prefix(3).enumerated()), id: \.offset) { _, dict in
                        Button {
                            onSelect?(dict)
                        } label: {
                            IngredientMatchRow(dict: dict)
                        }
                        .buttonStyle(PlainButtonStyle())
                        .disabled(onSelect == nil)
                    }
                }
            }

            // Explicit Action Button to add as NEW ingredient
            if !isSearching && onConfirmNew != nil {
                Button {
                    onConfirmNew?()
                } label: {
                    HStack {
                        Image(systemName: "plus.circle.fill")
                            .font(.system(size: 14))
                            .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                        Text("Add \"\(ingredientName.trimmingCharacters(in: .whitespaces))\" as New Ingredient")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(10)
                    .background(Color(red: 0.06, green: 0.73, blue: 0.51).opacity(0.1))
                    .cornerRadius(8)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color(red: 0.06, green: 0.73, blue: 0.51).opacity(0.3), lineWidth: 1))
                }
                .buttonStyle(PlainButtonStyle())
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white.opacity(0.05))
        .cornerRadius(12)
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(suggestions.isEmpty ? Color.white.opacity(0.1) : Color(red: 0.06, green: 0.73, blue: 0.51).opacity(0.3), lineWidth: 1))
    }
}

private struct IngredientMatchRow: View {
    let dict: [String: Any]

    var body: some View {
        let name = dict["name"] as? String ?? ""
        let eNum = dict["e_number"] as? String
        let cat = dict["category"] as? String
        let sev = dict["severity"] as? String ?? "gray"

        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(eNum != nil ? "\(name) (\(eNum!))" : name)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.white)
                if let cat = cat {
                    Text(cat).font(.system(size: 11)).foregroundColor(Color.white.opacity(0.5))
                }
            }
            Spacer()
            StatusIOSBadge(severity: sev)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(Color.white.opacity(0.04))
        .cornerRadius(8)
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.white.opacity(0.08), lineWidth: 0.5))
    }
}


private var nullAction: (() -> Void)? { return nil }

private struct StatusIOSBadge: View {
    let severity: String

    var body: some View {
        let (label, bg, fg) = badgeColors(for: severity)
        Text(label)
            .font(.system(size: 10, weight: .bold))
            .foregroundColor(fg)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(bg)
            .cornerRadius(6)
            .overlay(RoundedRectangle(cornerRadius: 6).stroke(fg.opacity(0.5), lineWidth: 0.5))
    }

    private func badgeColors(for sev: String) -> (String, Color, Color) {
        switch sev.lowercased() {
        case "red":
            return ("Banned", Color(red: 0.96, green: 0.25, blue: 0.37).opacity(0.2), Color(red: 0.96, green: 0.25, blue: 0.37))
        case "amber":
            return ("Restricted", Color(red: 0.96, green: 0.62, blue: 0.04).opacity(0.2), Color(red: 0.96, green: 0.62, blue: 0.04))
        case "green", "permitted":
            return ("Authorised", Color(red: 0.20, green: 0.83, blue: 0.60).opacity(0.2), Color(red: 0.20, green: 0.83, blue: 0.60))
        case "gray":
            return ("No data", Color.white.opacity(0.12), Color.white.opacity(0.7))
        default:
            return ("No data", Color.white.opacity(0.15), Color.white.opacity(0.8))
        }
    }
}

// ---------------------------------------------------------------------------
// MARK: - Shared sub-views
// ---------------------------------------------------------------------------

private struct SectionHeader: View {
    let title: String
    init(_ title: String) { self.title = title }
    var body: some View {
        Text(title)
            .font(.system(size: 15, weight: .semibold))
            .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
    }
}

private struct FormField: View {
    let label: String
    @Binding var text: String
    var placeholder: String = ""
    var axis: Axis = .horizontal
    var minHeight: CGFloat? = nil
    var keyboardType: UIKeyboardType = .default

    init(_ label: String, text: Binding<String>, placeholder: String = "",
         axis: Axis = .horizontal, minHeight: CGFloat? = nil, keyboardType: UIKeyboardType = .default) {
        self.label = label
        self._text = text
        self.placeholder = placeholder
        self.axis = axis
        self.minHeight = minHeight
        self.keyboardType = keyboardType
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(Color.white.opacity(0.6))
            TextField(placeholder, text: $text, axis: axis)
                .keyboardType(keyboardType)
                .foregroundColor(.white)
                .padding(12)
                .frame(minHeight: minHeight)
                .background(Color.white.opacity(0.06))
                .cornerRadius(10)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.white.opacity(0.12), lineWidth: 1))
        }
    }
}

private struct SubmitButton: View {
    let isLoading: Bool
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Group {
                if isLoading {
                    ProgressView().tint(.white)
                } else {
                    Text(label).font(.system(size: 15, weight: .semibold))
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 52)
        }
        .foregroundColor(.white)
        .background(isLoading ? Color(red: 0.06, green: 0.73, blue: 0.51).opacity(0.4) : Color(red: 0.06, green: 0.73, blue: 0.51))
        .cornerRadius(12)
        .disabled(isLoading)
    }
}

private struct SuccessView: View {
    let requestId: Int
    let onDone: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Spacer(minLength: 60)
            ZStack {
                Circle()
                    .fill(Color(red: 0.06, green: 0.73, blue: 0.51).opacity(0.15))
                    .frame(width: 90, height: 90)
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 52))
                    .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
            }
            Text("Request Submitted!")
                .font(.system(size: 22, weight: .bold))
                .foregroundColor(.white)
            Text("Your request #\(requestId) is now pending review. Our team will investigate and add it to the database.")
                .font(.system(size: 14))
                .foregroundColor(Color.white.opacity(0.6))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Button("Done", action: onDone)
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(.white)
                .frame(width: 140, height: 48)
                .background(Color(red: 0.06, green: 0.73, blue: 0.51))
                .cornerRadius(12)
            Spacer()
        }
        .padding(20)
    }
}

// ---------------------------------------------------------------------------
// MARK: - ViewModel (iOS-side)
// ---------------------------------------------------------------------------

@MainActor
class SubmitRequestIOSViewModel: ObservableObject {
    // Product request fields
    @Published var productName = ""
    @Published var brand = ""
    @Published var barcode = ""
    @Published var category = ""
    @Published var countryCode = "IN"
    @Published var ingredientsText = ""
    @Published var notes = ""
    @Published var images: [Data] = []

    // Ingredient request fields
    @Published var ingredientName = ""
    @Published var eNumber = ""
    @Published var ingredientCategory = ""
    @Published var ingredientNotes = ""
    @Published var selectedFromDb = false
    @Published var isConfirmedNew = false

    // Realtime search state
    @Published var ingredientSuggestions: [[String: Any]] = []
    @Published var isSearchingIngredients = false

    // UI state
    @Published var isLoading = false
    @Published var errorMessage: String? = nil
    @Published var submitted = false
    @Published var requestId = 0

    private let baseURL = BackendConfig.shared.baseUrl
    private var searchTask: Task<Void, Never>? = nil

    func reset() {
        isLoading = false
        errorMessage = nil
        submitted = false
        requestId = 0
        selectedFromDb = false
        isConfirmedNew = false
        ingredientSuggestions = []
        isSearchingIngredients = false
        searchTask?.cancel()
    }

    func searchIngredientRealtime(query: String) {
        searchTask?.cancel()
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 2 else {
            ingredientSuggestions = []
            isSearchingIngredients = false
            return
        }

        searchTask = Task {
            isSearchingIngredients = true
            try? await Task.sleep(nanoseconds: 300_000_000) // 300ms debounce
            if Task.isCancelled { return }

            guard let encoded = trimmed.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
                  let url = URL(string: "\(baseURL)/api/search?q=\(encoded)") else {
                isSearchingIngredients = false; return
            }

            do {
                let (data, _) = try await URLSession.shared.data(from: url)
                if Task.isCancelled { return }
                if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let ingredients = json["ingredients"] as? [[String: Any]] {
                    self.ingredientSuggestions = ingredients
                }
            } catch {
                self.ingredientSuggestions = []
            }
            self.isSearchingIngredients = false
        }
    }

    func submitProduct() {
        guard !productName.trimmingCharacters(in: .whitespaces).isEmpty else {
            errorMessage = "Product name is required"; return
        }
        Task {
            isLoading = true
            errorMessage = nil
            do {
                let id = try await postProductRequest()
                requestId = id
                submitted = true
            } catch {
                errorMessage = error.localizedDescription
            }
            isLoading = false
        }
    }

    func submitIngredient() {
        guard !ingredientName.trimmingCharacters(in: .whitespaces).isEmpty else {
            errorMessage = "Ingredient name is required"; return
        }
        Task {
            isLoading = true
            errorMessage = nil
            do {
                let id = try await postIngredientRequest()
                requestId = id
                submitted = true
            } catch {
                errorMessage = error.localizedDescription
            }
            isLoading = false
        }
    }

    // ── Networking ────────────────────────────────────────────────────────

    private func postProductRequest() async throws -> Int {
        let url = URL(string: "\(baseURL)/api/requests/product")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"

        let boundary = "FoodCheckBoundary\(UUID().uuidString.replacingOccurrences(of: "-", with: ""))"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        var body = Data()
        let fields: [(String, String)] = [
            ("product_name", productName),
            ("country_code", countryCode),
            ("brand", brand),
            ("barcode", barcode),
            ("category", category),
            ("ingredients_text", ingredientsText),
            ("requester_notes", notes),
        ]
        for (name, value) in fields where !value.isEmpty {
            body.append("--\(boundary)\r\n".data(using: .utf8)!)
            body.append("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n".data(using: .utf8)!)
            body.append("\(value)\r\n".data(using: .utf8)!)
        }
        for (idx, imageData) in images.prefix(3).enumerated() {
            let fieldName = "image_\(idx + 1)"
            body.append("--\(boundary)\r\n".data(using: .utf8)!)
            body.append("Content-Disposition: form-data; name=\"\(fieldName)\"; filename=\"image_\(idx + 1).jpg\"\r\n".data(using: .utf8)!)
            body.append("Content-Type: image/jpeg\r\n\r\n".data(using: .utf8)!)
            body.append(imageData)
            body.append("\r\n".data(using: .utf8)!)
        }
        body.append("--\(boundary)--\r\n".data(using: .utf8)!)
        request.httpBody = body

        let (data, _) = try await URLSession.shared.data(for: request)
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let id = json["request_id"] as? Int else {
            throw URLError(.badServerResponse)
        }
        return id
    }

    private func postIngredientRequest() async throws -> Int {
        let url = URL(string: "\(baseURL)/api/requests/ingredient")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        var body: [String: Any] = ["ingredient_name": ingredientName]
        if !eNumber.isEmpty { body["e_number"] = eNumber }
        if !ingredientCategory.isEmpty { body["category"] = ingredientCategory }
        if !ingredientNotes.isEmpty { body["requester_notes"] = ingredientNotes }
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, _) = try await URLSession.shared.data(for: request)
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let id = json["request_id"] as? Int else {
            throw URLError(.badServerResponse)
        }
        return id
    }
}
