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
