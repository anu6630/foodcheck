Feature: Image Scan Agent API tests

Background:
  * url baseUrl

Scenario: Scan with a single image - confident product match
  Given path 'api/scan'
  And multipart file images = { read: 'dummy.jpg', filename: 'dummy.jpg', contentType: 'image/jpeg' }
  And header x-mock-image-type = 'confident_match'
  When method post
  Then status 200
  And match response.match_type == 'confident'
  And match response.product.product.name == 'White Cookie Frosting'
  And match response.product.product.barcode == '016000452327'
  # Titanium dioxide (E171) is banned in EU, so aggregate severity escalates to red
  And match response.product.severity == 'red'

Scenario: Scan with two images (front-of-pack + ingredients label) - confident product match
  Given path 'api/scan'
  And multipart file images = { read: 'dummy.jpg', filename: 'front.jpg', contentType: 'image/jpeg' }
  And multipart file images = { read: 'dummy.jpg', filename: 'ingredients.jpg', contentType: 'image/jpeg' }
  And header x-mock-image-type = 'confident_match'
  When method post
  Then status 200
  And match response.match_type == 'confident'
  And match response.product.product.name == 'White Cookie Frosting'

Scenario: Scan yields multiple close candidates - suggestions branch
  Given path 'api/scan'
  And multipart file images = { read: 'dummy.jpg', filename: 'dummy.jpg', contentType: 'image/jpeg' }
  And header x-mock-image-type = 'ambiguous'
  When method post
  Then status 200
  And match response.match_type == 'suggestions'
  And match response.candidates == '#[2]'
  And match response.candidates[0].product.product.name == 'White Cookie Frosting'
  And match response.candidates[0].match_score == 70
  And match response.candidates[1].product.product.name == 'Sliced White Bread'
  And match response.candidates[1].match_score == 62

Scenario: Scan yields no product match but extracts ingredients - ingredients_only branch
  Given path 'api/scan'
  And multipart file images = { read: 'dummy.jpg', filename: 'dummy.jpg', contentType: 'image/jpeg' }
  And header x-mock-image-type = 'ingredients_only'
  When method post
  Then status 200
  And match response.match_type == 'ingredients_only'
  And match response.ingredients == '#[]'
  # First ingredient should be red (potassium bromate is banned in India)
  And match response.ingredients[0].severity == 'red'
  And match response.ingredients[0].name == 'Potassium bromate'
