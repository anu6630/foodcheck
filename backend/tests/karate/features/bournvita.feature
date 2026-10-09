Feature: Test Bournvita Image Scan

Background:
  * url 'http://localhost:8999'

Scenario: Upload Bournvita image and print matched ingredients
  Given path 'api/scan'
  And multipart file images = { read: '../../../../samples/bournvita_ingredients.jpg', filename: 'bournvita_ingredients.jpg', contentType: 'image/jpeg' }
  When method post
  Then status 200
  * print response
