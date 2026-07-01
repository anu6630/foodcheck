Feature: Product Details API tests

Background:
  * url baseUrl

Scenario: Get details for existing product with default country filter (India)
  Given path 'api/products/016000452327'
  When method get
  Then status 200
  And match response.product.barcode == '016000452327'
  And match response.product.name == 'White Cookie Frosting'
  And match response.severity == 'amber'
  # Validate that sodium benzoate is flagged as amber (restricted) in India
  And match response.ingredients[?(@.id=='additive-e211')].severity == ['amber']


Scenario: Get details for product with EU country filter (E171 is banned)
  Given path 'api/products/016000452327'
  And param countries = 'EU'
  When method get
  Then status 200
  And match response.product.barcode == '016000452327'
  # E171 (Titanium Dioxide) is banned in EU, so severity must escalate to red
  And match response.severity == 'red'
  And match response.ingredients[?(@.e_number=='E171')].severity == ['red']

Scenario: Get details for product with US country filter (permitted)
  Given path 'api/products/016000452327'
  And param countries = 'US'
  When method get
  Then status 200
  # In US, E171 is permitted, and sodium benzoate has no mapped US ban, so severity is green
  And match response.severity == 'green'

Scenario: Query non-existent barcode (returns 404)
  Given path 'api/products/999999999999'
  When method get
  Then status 404
