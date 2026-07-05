Feature: Product Search API tests

Background:
  * url baseUrl

Scenario: Search products by name (match White Cookie Frosting)
  Given path 'api/products/search'
  And param q = 'White'
  When method get
  Then status 200
  And match response == '#[]'
  And match response[0].product.name == 'White Cookie Frosting'
  And match response[0].severity == 'amber'

Scenario: Search with specific country filter (EU)
  Given path 'api/products/search'
  And param q = 'White'
  And param countries = 'EU'
  When method get
  Then status 200
  And match response[0].severity == 'red'

Scenario: Unified search returns grouped products and ingredients (not interleaved)
  Given path 'api/search'
  And param q = 'sodium benzoate'
  When method get
  Then status 200
  And match response.products == '#[]'
  And match response.ingredients == '#[]'
  # "Sodium benzoate" itself should rank first (exact/near-exact match)
  And match response.ingredients[0].id == 'additive-e211'
  And match response.ingredients[0].name == 'Sodium benzoate'
  And match response.ingredients[0].severity == 'amber'

Scenario: Unified search matches a product by name
  Given path 'api/search'
  And param q = 'White Cookie Frosting'
  When method get
  Then status 200
  And match response.products == '#[]'
  And match response.products[0].product.name == 'White Cookie Frosting'

Scenario: Unified search with a query matching neither products nor ingredients
  Given path 'api/search'
  And param q = 'zzz_no_such_thing_zzz'
  When method get
  Then status 200
  And match response == { products: [], ingredients: [] }
