import SwiftUI
import composeApp

// Specific view models wrappers
class ObservableSearchModel: ObservableObject {
    let viewModel = ViewModelProvider.shared.getSearchViewModel()
    
    @Published var query: String = ""
    @Published var results: UnifiedSearchResult = UnifiedSearchResult(products: [], ingredients: [])
    @Published var loading: Bool = false
    @Published var error: String? = nil
    
    private var queryWatcher: FlowWatcher<NSString>?
    private var resultsWatcher: FlowWatcher<UnifiedSearchResult>?
    private var loadingWatcher: FlowWatcher<KotlinBoolean>?
    private var errorWatcher: FlowWatcher<NSString>?
    
    init() {
        self.queryWatcher = FlowWatcher(flow: viewModel.query) { [weak self] val in
            DispatchQueue.main.async { self?.query = val as String? ?? "" }
        }
        self.resultsWatcher = FlowWatcher(flow: viewModel.results) { [weak self] val in
            DispatchQueue.main.async { self?.results = val as! UnifiedSearchResult }
        }
        self.loadingWatcher = FlowWatcher(flow: viewModel.loading) { [weak self] val in
            DispatchQueue.main.async { self?.loading = val?.boolValue ?? false }
        }
        self.errorWatcher = FlowWatcher(flow: viewModel.error) { [weak self] val in
            DispatchQueue.main.async { self?.error = val as String? }
        }
    }
    
    func search(text: String) {
        viewModel.search(query: text)
    }
    
    deinit {
        queryWatcher?.close()
        resultsWatcher?.close()
        loadingWatcher?.close()
        errorWatcher?.close()
    }
}

class ObservableProductDetailModel: ObservableObject {
    let viewModel = ViewModelProvider.shared.getProductDetailViewModel()
    
    @Published var product: SearchProductResult? = nil
    @Published var loading: Bool = false
    @Published var error: String? = nil
    
    private var productWatcher: FlowWatcher<SearchProductResult>?
    private var loadingWatcher: FlowWatcher<KotlinBoolean>?
    private var errorWatcher: FlowWatcher<NSString>?
    
    func load(barcode: String) {
        viewModel.load(barcode: barcode)
        
        self.productWatcher = FlowWatcher(flow: viewModel.product) { [weak self] val in
            DispatchQueue.main.async { self?.product = val as SearchProductResult? }
        }
        self.loadingWatcher = FlowWatcher(flow: viewModel.loading) { [weak self] val in
            DispatchQueue.main.async { self?.loading = val?.boolValue ?? false }
        }
        self.errorWatcher = FlowWatcher(flow: viewModel.error) { [weak self] val in
            DispatchQueue.main.async { self?.error = val as String? }
        }
    }
    
    deinit {
        productWatcher?.close()
        loadingWatcher?.close()
        errorWatcher?.close()
    }
}

class ObservableIngredientDetailModel: ObservableObject {
    let viewModel = ViewModelProvider.shared.getIngredientDetailViewModel()
    
    @Published var detail: IngredientDetail? = nil
    @Published var loading: Bool = false
    @Published var error: String? = nil
    
    private var detailWatcher: FlowWatcher<IngredientDetail>?
    private var loadingWatcher: FlowWatcher<KotlinBoolean>?
    private var errorWatcher: FlowWatcher<NSString>?
    
    func load(id: String) {
        viewModel.load(ingredientId: id)
        
        self.detailWatcher = FlowWatcher(flow: viewModel.detail) { [weak self] val in
            DispatchQueue.main.async { self?.detail = val as IngredientDetail? }
        }
        self.loadingWatcher = FlowWatcher(flow: viewModel.loading) { [weak self] val in
            DispatchQueue.main.async { self?.loading = val?.boolValue ?? false }
        }
        self.errorWatcher = FlowWatcher(flow: viewModel.error) { [weak self] val in
            DispatchQueue.main.async { self?.error = val as String? }
        }
    }
    
    deinit {
        detailWatcher?.close()
        loadingWatcher?.close()
        errorWatcher?.close()
    }
}

class ObservableScanModel: ObservableObject {
    let viewModel = ViewModelProvider.shared.getScanViewModel()
    
    @Published var outcome: ScanOutcome? = nil
    @Published var loading: Bool = false
    @Published var statusText: String = "Reading label..."
    @Published var error: String? = nil
    
    private var outcomeWatcher: FlowWatcher<ScanOutcome>?
    private var loadingWatcher: FlowWatcher<KotlinBoolean>?
    private var statusTextWatcher: FlowWatcher<NSString>?
    private var errorWatcher: FlowWatcher<NSString>?
    
    init() {
        self.outcomeWatcher = FlowWatcher(flow: viewModel.outcome) { [weak self] val in
            DispatchQueue.main.async { self?.outcome = val as ScanOutcome? }
        }
        self.loadingWatcher = FlowWatcher(flow: viewModel.loading) { [weak self] val in
            DispatchQueue.main.async { self?.loading = val?.boolValue ?? false }
        }
        self.statusTextWatcher = FlowWatcher(flow: viewModel.statusText) { [weak self] val in
            DispatchQueue.main.async { self?.statusText = val as String? ?? "Reading label..." }
        }
        self.errorWatcher = FlowWatcher(flow: viewModel.error) { [weak self] val in
            DispatchQueue.main.async { self?.error = val as String? }
        }
    }
    
    func scan(image: Data) {
        let kotlinBytes = image.toKotlinByteArray()
        viewModel.scan(images: [kotlinBytes])
    }
    
    func clear() {
        viewModel.clear()
    }
    
    deinit {
        outcomeWatcher?.close()
        loadingWatcher?.close()
        statusTextWatcher?.close()
        errorWatcher?.close()
    }
}
