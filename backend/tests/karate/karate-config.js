function fn() {
  var env = karate.env; // get system property 'karate.env'
  karate.log('karate.env system property was:', env);
  if (!env) {
    env = 'dev';
  }
  var config = {
    baseUrl: 'http://localhost:8999'
  };

  
  // Set connection timeouts
  karate.configure('connectTimeout', 10000);
  karate.configure('readTimeout', 15000);

  
  return config;
}
