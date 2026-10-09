Feature: Community Contribution & Admin Review Flows

  Background:
    * url 'http://localhost:8999/api'
    * def adminToken = '123'

  # ======================================================================= #
  # Flow 1 — User submits a product request (with image upload)
  # ======================================================================= #

  Scenario: Submit a product request without images returns 201
    Given path '/requests/product'
    And multipart field product_name = 'KitKat Dark India'
    And multipart field country_code = 'IN'
    And multipart field brand = 'Nestle'
    And multipart field barcode = '8901058857781'
    And multipart field category = 'Confectionery'
    And multipart field ingredients_text = 'Sugar, Cocoa Butter, Cocoa Mass, Milk Powder, Emulsifier (Soy Lecithin)'
    And multipart field requester_notes = 'Please verify E471 ban status in India'
    When method post
    Then status 201
    And match response.status == 'pending'
    And match response.request_id == '#number'
    * def productRequestId = response.request_id

  Scenario: Submit a product request with an image returns 201
    Given path '/requests/product'
    And multipart field product_name = 'Bournvita Boost'
    And multipart field country_code = 'IN'
    And multipart field brand = 'Cadbury'
    And multipart file image_1 = { read: 'file:/Volumes/SSDX 1/codes/foodcheck/samples/bournvita.jpg', filename: 'bournvita.jpg', contentType: 'image/jpeg' }
    When method post
    Then status 201
    And match response.status == 'pending'

  # ======================================================================= #
  # Flow 2 — User submits an ingredient check request
  # ======================================================================= #

  Scenario: Submit an ingredient check request returns 201
    Given path '/requests/ingredient'
    And request { ingredient_name: 'Titanium Dioxide', e_number: 'E171', category: 'colour', requester_notes: 'Is this banned in EU foods?' }
    When method post
    Then status 201
    And match response.status == 'pending'
    And match response.request_id == '#number'
    * def ingredientRequestId = response.request_id

  # ======================================================================= #
  # Admin — Unauthenticated call must be rejected
  # ======================================================================= #

  Scenario: Admin endpoint without token returns 403
    Given url 'http://localhost:8999/api/admin/requests/products'
    When method get
    Then status 403

  # ======================================================================= #
  # Admin — List product requests
  # ======================================================================= #

  Scenario: Admin can list pending product requests
    Given url 'http://localhost:8999/api/admin/requests/products'
    And header x-admin-token = adminToken
    And param status = 'pending'
    When method get
    Then status 200
    And match response == '#array'

  # ======================================================================= #
  # Admin — Full flow: submit → list → approve product request
  # ======================================================================= #

  Scenario: Admin approves a product request end-to-end
    # 1. Submit the request
    Given url 'http://localhost:8999/api/requests/product'
    And multipart field product_name = 'Test Biscuit E2E'
    And multipart field country_code = 'IN'
    And multipart field brand = 'Test Brand'
    When method post
    Then status 201
    * def requestId = response.request_id

    # 2. Verify it appears in the admin list
    Given url 'http://localhost:8999/api/admin/requests/products'
    And header x-admin-token = adminToken
    When method get
    Then status 200
    And match response[*].id contains requestId

    # 3. Approve it
    Given url 'http://localhost:8999/api/admin/requests/products/' + requestId + '/status'
    And header x-admin-token = adminToken
    And request { status: 'approved' }
    When method put
    Then status 200
    And match response.status == 'approved'

    # 4. Fetch detail and confirm status
    Given url 'http://localhost:8999/api/admin/requests/products/' + requestId
    And header x-admin-token = adminToken
    When method get
    Then status 200
    And match response.status == 'approved'
    And match response.product_name == 'Test Biscuit E2E'

  # ======================================================================= #
  # Admin — Full flow: submit → list → reject ingredient request
  # ======================================================================= #

  Scenario: Admin rejects an ingredient request end-to-end
    # 1. Submit
    Given url 'http://localhost:8999/api/requests/ingredient'
    And request { ingredient_name: 'Brilliant Blue FCF', e_number: 'E133', category: 'colour' }
    When method post
    Then status 201
    * def ingredId = response.request_id

    # 2. Admin list
    Given url 'http://localhost:8999/api/admin/requests/ingredients'
    And header x-admin-token = adminToken
    When method get
    Then status 200
    And match response[*].id contains ingredId

    # 3. Reject
    Given url 'http://localhost:8999/api/admin/requests/ingredients/' + ingredId + '/status'
    And header x-admin-token = adminToken
    And request { status: 'rejected' }
    When method put
    Then status 200
    And match response.status == 'rejected'

  # ======================================================================= #
  # Admin — Direct catalog operations
  # ======================================================================= #

  Scenario: Admin creates a product directly and publishes it
    * def uniqueBarcode = 'TEST-OREO-' + java.lang.System.currentTimeMillis()
    Given url 'http://localhost:8999/api/admin/products'
    And header x-admin-token = adminToken
    And request { name: 'Oreo Original', barcode: '#(uniqueBarcode)', brand: 'Mondelez', category: 'Biscuits', source: 'local', published: false }
    When method post
    Then status 201
    And match response.published == false
    * def newProductId = response.id

    Given url 'http://localhost:8999/api/admin/products/' + newProductId + '/publish'
    And header x-admin-token = adminToken
    When method put
    Then status 200
    And match response.published == true

  Scenario: Admin creates an ingredient with a ban
    * def uniqueId = 'test-e127-erythrosine-' + java.lang.System.currentTimeMillis()
    Given url 'http://localhost:8999/api/admin/ingredients'
    And header x-admin-token = adminToken
    And request
    """
    {
      "id": "#(uniqueId)",
      "name": "Erythrosine (Test)",
      "category": "colour",
      "e_number": "E127",
      "bans": [
        { "country_code": "EU", "status": "banned", "condition": "Banned in food use in EU since 1994" }
      ]
    }
    """
    When method post
    Then status 201
    And match response.name == 'Erythrosine (Test)'

