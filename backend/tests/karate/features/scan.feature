Feature: Image Intent Scan API tests

Background:
  * url baseUrl

Scenario: Scan image with ingredients list intent
  Given path 'api/scan'
  And multipart file image = { read: 'dummy.jpg', filename: 'dummy.jpg', contentType: 'image/jpeg' }
  And header x-mock-image-type = 'ingredients'
  When method post
  Then status 200
  And match response.intent == 'ingredients'
  And match response.ingredients == '#[]'
  # First ingredient should be red (potassium bromate is banned in India)
  And match response.ingredients[0].severity == 'red'
  And match response.ingredients[0].name == 'Potassium bromate'

Scenario: Scan image with product packaging intent (White Cookie Frosting)
  Given path 'api/scan'
  And multipart file image = { read: 'dummy.jpg', filename: 'dummy.jpg', contentType: 'image/jpeg' }
  And header x-mock-image-type = 'product'
  When method post
  Then status 200
  And match response.intent == 'product'
  And match response.match_score == 100
  And match response.data.product.name == 'White Cookie Frosting'
  # Severity in India (default) should be amber (sodium benzoate is restricted)
  And match response.data.severity == 'amber'

Scenario: Scan image with product packaging containing potassium bromate (Traditional White Bread)
  Given path 'api/scan'
  And multipart file image = { read: 'dummy.jpg', filename: 'dummy.jpg', contentType: 'image/jpeg' }
  And header x-mock-image-type = 'product_potassium'
  When method post
  Then status 200
  And match response.intent == 'product'
  And match response.data.product.name == 'Sliced White Bread'
  # Severity in India should be red (potassium bromate is banned)
  And match response.data.severity == 'red'
