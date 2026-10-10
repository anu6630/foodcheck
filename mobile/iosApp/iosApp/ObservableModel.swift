import Foundation
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
    @Published var actionMessage: String? = nil
    @Published var brandSuggestions: [CatalogBrandDto] = []
    @Published var companySuggestions: [CatalogCompanyDto] = []
    
    private var outcomeWatcher: FlowWatcher<ScanOutcome>?
    private var loadingWatcher: FlowWatcher<KotlinBoolean>?
    private var statusTextWatcher: FlowWatcher<NSString>?
    private var errorWatcher: FlowWatcher<NSString>?
    private var actionWatcher: FlowWatcher<NSString>?
    private var brandWatcher: FlowWatcher<NSArray>?
    private var companyWatcher: FlowWatcher<NSArray>?
    
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
        self.actionWatcher = FlowWatcher(flow: viewModel.actionMessage) { [weak self] val in
            DispatchQueue.main.async { self?.actionMessage = val as String? }
        }
        self.brandWatcher = FlowWatcher(flow: viewModel.brandSuggestions) { [weak self] val in
            DispatchQueue.main.async {
                self?.brandSuggestions =
                    (val as? [CatalogBrandDto]) ??
                    (val as? NSArray)?.compactMap { $0 as? CatalogBrandDto } ?? []
            }
        }
        self.companyWatcher = FlowWatcher(flow: viewModel.companySuggestions) { [weak self] val in
            DispatchQueue.main.async {
                self?.companySuggestions =
                    (val as? [CatalogCompanyDto]) ??
                    (val as? NSArray)?.compactMap { $0 as? CatalogCompanyDto } ?? []
            }
        }
    }
    
    func scan(image: Data) {
        DispatchQueue.global(qos: .userInitiated).async {
            let kotlinBytes = image.toKotlinByteArray()
            DispatchQueue.main.async {
                self.viewModel.scan(images: [kotlinBytes])
            }
        }
    }
    
    func clear() {
        viewModel.clear()
    }

    func searchBrands(query: String) {
        viewModel.searchBrands(query: query)
    }

    func searchCompanies(query: String) {
        viewModel.searchCompanies(query: query)
    }

    func saveToShelf(name: String, brand: String, brandId: Int?, companyIds: [Int]) {
        let kotlinBrandId: KotlinInt? = brandId.map { KotlinInt(value: Int32($0)) }
        let kotlinCompanyIds = companyIds.map { KotlinInt(value: Int32($0)) }
        viewModel.saveToShelf(
            displayName: name,
            brand: brand.isEmpty ? nil : brand,
            brandId: kotlinBrandId,
            companyIds: kotlinCompanyIds
        )
    }

    func publishStory(caption: String) {
        viewModel.publishStory(caption: caption)
    }

    func clearActionMessage() {
        viewModel.clearActionMessage()
    }
    
    deinit {
        outcomeWatcher?.close()
        loadingWatcher?.close()
        statusTextWatcher?.close()
        errorWatcher?.close()
        actionWatcher?.close()
        brandWatcher?.close()
        companyWatcher?.close()
    }
}

class ObservableFeedModel: ObservableObject {
    let viewModel = ViewModelProvider.shared.getFeedViewModel()
    @Published var stories: [StoryDto] = []
    @Published var selected: StoryDto? = nil
    @Published var error: String? = nil
    @Published var loadingMore: Bool = false
    @Published var likedIds: Set<Int> = []

    private var storiesWatcher: FlowWatcher<NSArray>?
    private var selectedWatcher: FlowWatcher<StoryDto>?
    private var errorWatcher: FlowWatcher<NSString>?
    private var loadingMoreWatcher: FlowWatcher<KotlinBoolean>?
    private var likedWatcher: FlowWatcher<NSArray>?

    init() {
        storiesWatcher = FlowWatcher(flow: viewModel.stories) { [weak self] val in
            DispatchQueue.main.async {
                self?.stories = (val as? [StoryDto]) ?? (val as? NSArray)?.compactMap { $0 as? StoryDto } ?? []
            }
        }
        selectedWatcher = FlowWatcher(flow: viewModel.selected) { [weak self] val in
            DispatchQueue.main.async { self?.selected = val as StoryDto? }
        }
        errorWatcher = FlowWatcher(flow: viewModel.error) { [weak self] val in
            DispatchQueue.main.async { self?.error = val as String? }
        }
        loadingMoreWatcher = FlowWatcher(flow: viewModel.loadingMore) { [weak self] val in
            DispatchQueue.main.async { self?.loadingMore = (val as? KotlinBoolean)?.boolValue ?? false }
        }
        likedWatcher = FlowWatcher(flow: viewModel.likedIds) { [weak self] val in
            DispatchQueue.main.async {
                let arr = (val as? [Any]) ?? (val as? NSArray) as? [Any] ?? []
                self?.likedIds = Set(arr.compactMap { ($0 as? NSNumber)?.intValue ?? ($0 as? Int) })
            }
        }
        viewModel.load()
    }

    func open(id: Int32) { viewModel.open(id: id) }
    func close() { viewModel.close() }
    func comment(_ body: String) { viewModel.comment(body: body) }
    func like(storyId: Int32) { viewModel.like(storyId: storyId) }
    func loadMore() { viewModel.loadMore() }

    deinit {
        storiesWatcher?.close()
        selectedWatcher?.close()
        errorWatcher?.close()
        loadingMoreWatcher?.close()
        likedWatcher?.close()
    }
}

class ObservableShelfModel: ObservableObject {
    let viewModel = ViewModelProvider.shared.getShelfViewModel()
    @Published var items: [ShelfItemDto] = []
    @Published var query: String = ""
    @Published var error: String? = nil

    private var itemsWatcher: FlowWatcher<NSArray>?
    private var queryWatcher: FlowWatcher<NSString>?
    private var errorWatcher: FlowWatcher<NSString>?

    init() {
        itemsWatcher = FlowWatcher(flow: viewModel.items) { [weak self] val in
            DispatchQueue.main.async {
                self?.items = (val as? [ShelfItemDto]) ?? (val as? NSArray)?.compactMap { $0 as? ShelfItemDto } ?? []
            }
        }
        queryWatcher = FlowWatcher(flow: viewModel.query) { [weak self] val in
            DispatchQueue.main.async { self?.query = val as String? ?? "" }
        }
        errorWatcher = FlowWatcher(flow: viewModel.error) { [weak self] val in
            DispatchQueue.main.async { self?.error = val as String? }
        }
        viewModel.load(q: "")
    }

    func search(_ q: String) { viewModel.load(q: q) }

    deinit {
        itemsWatcher?.close()
        queryWatcher?.close()
        errorWatcher?.close()
    }
}
