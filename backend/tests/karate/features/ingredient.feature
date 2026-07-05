Feature: Ingredient Details API tests

Background:
  * url baseUrl

Scenario: Get details for a known ingredient with ban data (Sodium benzoate / E211)
  Given path 'api/ingredients/additive-e211'
  When method get
  Then status 200
  And match response.id == 'additive-e211'
  And match response.name == 'Sodium benzoate'
  And match response.e_number == 'E211'
  And match response.tracked_countries == ['IN', 'EU', 'US', 'AU']
  # All bans are returned (not filtered to banned/restricted only)
  And match response.bans == '#[]'
  And match response.bans[?(@.country_code=='IN')].status == ['restricted']
  And match response.bans[?(@.country_code=='EU')].status == ['restricted']

Scenario: Get details for an ingredient permitted in some countries (Titanium dioxide / E171)
  Given path 'api/ingredients/additive-e171'
  When method get
  Then status 200
  And match response.id == 'additive-e171'
  And match response.e_number == 'E171'
  # Every country's status is present, including "permitted" - not just banned/restricted
  And match response.bans[?(@.country_code=='EU')].status == ['banned']
  And match response.bans[?(@.country_code=='IN')].status == ['permitted']
  And match response.bans[?(@.country_code=='US')].status == ['permitted']

Scenario: Query non-existent ingredient id (returns 404)
  Given path 'api/ingredients/no-such-ingredient-xyz'
  When method get
  Then status 404
