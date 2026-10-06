#!/usr/bin/env ruby
# Isolated Docker Desktop smoke; no existing databases, realms or volumes are used.
require 'base64'
require 'json'
require 'net/http'
require 'open3'
require 'openssl'
require 'securerandom'
require 'webrick'
require 'yaml'

def command(*args, input: '')
  output, error, status = Open3.capture3(*args, stdin_data: input)
  raise "Command failed: #{args.first}: #{error}" unless status.success?
  output.strip
end

def encode(value)
  Base64.urlsafe_encode64(value, padding: false)
end

def request(base, path, token = nil, method = Net::HTTP::Get, body = nil)
  uri = URI(base + path)
  req = method.new(uri)
  req['Authorization'] = "Bearer #{token}" if token
  if body
    req['Content-Type'] = 'application/json'
    req.body = JSON.generate(body)
  end
  Net::HTTP.start(uri.host, uri.port, nil, open_timeout: 3, read_timeout: 10) { |http| http.request(req) }
end

def expect(response, status, label)
  raise "#{label}: expected #{status}, got #{response.code}" unless response.code.to_i == status
end

def wait_for(label, attempts = 90)
  attempts.times do
    begin
      return if yield
    rescue IOError, SystemCallError, Timeout::Error
      # A container may still be starting or its health transition may be in progress.
    end
    sleep 1
  end
  raise "#{label} timed out"
end

def verify_contract(document)
  scheme = document.dig('components', 'securitySchemes', 'bearerAuth')
  raise 'HTTP bearer JWT scheme missing' unless scheme && scheme['type'] == 'http' &&
    scheme['scheme'] == 'bearer' && scheme['bearerFormat'] == 'JWT'
  requirements = document.fetch('security')
  raise 'Global bearer requirement missing' unless requirements.any? { |item| item.key?('bearerAuth') }
  paths = document.fetch('paths')
  raise 'Public ping security override missing' unless paths.dig('/api/videos/ping', 'get', 'security') == []
  paths.each do |path, item|
    if path == '/actuator/health' || path.start_with?('/actuator/health/')
      raise 'Public probe security override missing' if item['get'] && item['get']['security'] != []
    end
  end
  effective = paths.fetch('/api/videos').fetch('get').fetch('security', requirements)
  raise 'Catalog bearer requirement missing' unless effective.any? { |item| item.key?('bearerAuth') }
end

key = OpenSSL::PKey::RSA.new(2048)
server = WEBrick::HTTPServer.new(Port: 0, BindAddress: '0.0.0.0',
  Logger: WEBrick::Log.new(File::NULL), AccessLog: [])
fixture = "http://host.docker.internal:#{server.config[:Port]}"
issuer = fixture + '/issuer'
jwks = { keys: [{ kty: 'RSA', kid: 'image-smoke', use: 'sig', alg: 'RS256',
  n: encode(key.n.to_s(2)), e: encode(key.e.to_s(2)) }] }
server.mount_proc('/') do |req, res|
  res['Content-Type'] = 'application/json'
  case req.path
  when '/issuer/.well-known/openid-configuration', '/.well-known/openid-configuration/issuer'
    res.body = JSON.generate(issuer: issuer, jwks_uri: fixture + '/jwks')
  when '/jwks'
    res.body = JSON.generate(jwks)
  else
    res.status = 404
    res.body = '{}'
  end
end
thread = Thread.new { server.start }
token = lambda do |role, overrides = {}, signing_key = key|
  claims = { iss: issuer, sub: 'b9a2df7b-278f-4c16-925c-2b480e03e5be', iat: Time.now.to_i,
    exp: Time.now.to_i + 600, aud: ['company-platform-api'], realm_access: { roles: [role] } }.merge(overrides)
  claims.delete(:aud) if claims[:aud].nil?
  unsigned = encode(JSON.generate(alg: 'RS256', kid: 'image-smoke', typ: 'JWT')) + '.' + encode(JSON.generate(claims))
  unsigned + '.' + encode(signing_key.sign(OpenSSL::Digest::SHA256.new, unsigned))
end
suffix = SecureRandom.hex(4)
network = "video-image-smoke-#{suffix}"
database = network + '-postgres'
app = network + '-app'
containers = []
network_created = false
begin
  command('docker', 'network', 'create', network)
  network_created = true
  command('docker', 'run', '--rm', '-d', '--name', database, '--network', network,
    '--tmpfs', '/var/lib/postgresql:rw,nosuid', '-e', 'POSTGRES_USER=root',
    '-e', 'POSTGRES_PASSWORD=root', '-e', 'POSTGRES_DB=videodb', 'postgres:18')
  containers << database
  wait_for('PostgreSQL') do
    _, status = Open3.capture2e('docker', 'exec', database, 'pg_isready', '-U', 'root', '-d', 'videodb')
    status.success? && command('docker', 'logs', database).include?('PostgreSQL init process complete')
  end
  command('docker', 'exec', '-i', database, 'psql', '-U', 'root', '-d', 'videodb', '-v', 'ON_ERROR_STOP=1',
    input: File.read(File.expand_path('../src/test/resources/postgres-test-users.sql', __dir__)))
  image = ARGV.fetch(0, 'video-service:latest')
  command('docker', 'run', '--rm', '-d', '--name', app, '--network', network,
    '-p', '127.0.0.1::9161', '--read-only', '--tmpfs', '/tmp:rw,nosuid',
    '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges', '--memory', '512m',
    '-e', 'SPRING_ACTIVE_PROFILE=image-smoke', '-e', 'SPRING_DOCKER_COMPOSE_ENABLED=false',
    '-e', "SPRING_DATASOURCE_URL=jdbc:postgresql://#{database}:5432/videodb",
    '-e', 'SPRING_DATASOURCE_USERNAME=theuser', '-e', 'SPRING_DATASOURCE_PASSWORD=theuser',
    '-e', 'SPRING_FLYWAY_USER=videoadmin', '-e', 'SPRING_FLYWAY_PASSWORD=videoadmin',
    '-e', "KEYCLOAK_ISSUER_URI=#{issuer}",
    '-e', 'SPRING_CLOUD_VAULT_ENABLED=false', '-e', 'SPRING_CLOUD_CONFIG_ENABLED=false',
    '-e', 'SPRING_CONFIG_IMPORT=', '-e', 'EUREKA_CLIENT_ENABLED=false', image)
  containers << app
  port = command('docker', 'port', app, '9161/tcp').split(':').last
  base = "http://127.0.0.1:#{port}"
  wait_for('Video readiness') { request(base, '/actuator/health/readiness').code == '200' }
  admin = token.call('ADMIN')
  %w[/actuator/health /actuator/health/liveness /actuator/health/readiness].each do |path|
    response = request(base, path)
    expect(response, 200, path)
    raise 'Anonymous health components exposed' if JSON.parse(response.body).key?('components')
  end
  expect(request(base, '/api/videos/ping'), 200, 'Public ping')
  %w[/actuator/info /api-docs /api-docs.yaml /api-docs/swagger-config /swagger-ui/index.html].each do |path|
    expect(request(base, path), 401, 'Anonymous protected request')
    %w[USER MANAGER].each { |role| expect(request(base, path, token.call(role)), 403, role + ' protected request') }
    expect(request(base, path, admin), 200, 'ADMIN protected request')
  end
  raise 'Fresh video database is not empty' unless JSON.parse(request(base, '/api/videos', admin).body).fetch('content') == []
  owner = token.call('USER')
  expect(request(base, '/api/videos', owner), 200, 'Correct JWT audience accepted')
  expect(request(base, '/api/videos', token.call('USER', { aud: nil })), 401, 'Missing JWT audience rejected')
  expect(request(base, '/api/videos', token.call('USER', { aud: ['another-api'] })), 401, 'Wrong JWT audience rejected')
  expect(request(base, '/api/videos'), 401, 'Anonymous catalog denial')
  expect(request(base, '/api/videos', owner), 200, 'USER catalog read')
  response = request(base, '/api/videos', owner, Net::HTTP::Post,
    { title: 'Image smoke video', description: 'Fixture',
      userId: '85bb9ba7-c826-4b0c-8f86-339c9a7f8f0b' })
  expect(response, 200, 'Video creation')
  book = JSON.parse(response.body)
  raise 'Body userId overrode JWT owner' unless book.fetch('userId') == 'b9a2df7b-278f-4c16-925c-2b480e03e5be'
  path = '/api/videos/' + book.fetch('id')
  expect(request(base, path, owner), 200, 'Persisted video read')
  outsider = token.call('USER', { sub: '0f0d27fb-f766-47a6-b99f-21549c9fe2c6' })
  expect(request(base, path, outsider, Net::HTTP::Delete), 403, 'Non-owner delete denial')
  expect(request(base, path, outsider, Net::HTTP::Patch), 403, 'Non-owner completion denial')
  expect(request(base, path, owner), 200, 'Denied deletion preserves video')
  response = request(base, path, owner, Net::HTTP::Patch)
  expect(response, 200, 'Owner completion')
  raise 'Completion missing' unless JSON.parse(response.body).fetch('completed') == true
  saved = JSON.parse(request(base, path, owner).body)
  raise 'Completion not persisted' unless saved.fetch('completed') == true
  document = JSON.parse(request(base, '/api-docs', admin).body)
  raise 'Video OpenAPI missing' unless document.fetch('paths').key?('/api/videos')
  verify_contract(document)
  verify_contract(YAML.safe_load(request(base, '/api-docs.yaml', admin).body))
  [token.call('ADMIN', { iss: issuer + '/wrong' }), token.call('ADMIN', { exp: Time.now.to_i - 300 }),
   token.call('ADMIN', {}, OpenSSL::PKey::RSA.new(2048))].each do |invalid|
    expect(request(base, '/api/videos', invalid), 401, 'Invalid JWT')
  end
  components = JSON.parse(request(base, '/actuator/health', admin).body).fetch('components')
  raise 'Database health missing' unless components.dig('db', 'status') == 'UP'
  migrated = command('docker', 'exec', database, 'psql', '-U', 'root', '-d', 'videodb', '-Atc',
    "SELECT count(*) FROM flyway_schema_history WHERE success AND version = '1'")
  raise 'Flyway migration missing' unless migrated == '1'
  table_owner = command('docker', 'exec', database, 'psql', '-U', 'root', '-d', 'videodb', '-Atc',
    "SELECT tableowner FROM pg_tables WHERE schemaname = 'public' AND tablename = 'tbl_video'")
  raise 'Migration table owner incorrect' unless table_owner == 'videoadmin'
  runtime = command('docker', 'exec', database, 'psql', '-U', 'root', '-d', 'videodb', '-Atc',
    "SELECT count(*) FROM pg_stat_activity WHERE usename = 'theuser' AND datname = 'videodb'")
  raise 'Application is not using runtime role' unless runtime.to_i > 0
  raise 'Container runs as root' if command('docker', 'exec', app, 'id', '-u') == '0'
  raise 'Runtime is not Java 27' unless command('docker', 'exec', app, 'java', '--version').match?(/openjdk 27[.\s]/)
  wait_for('Docker healthy', 20) { command('docker', 'inspect', '--format', '{{.State.Health.Status}}', app) == 'healthy' }
  command('docker', 'stop', database)
  containers.delete(database)
  expect(request(base, '/actuator/health/readiness'), 503, 'Database outage readiness')
  expect(request(base, '/actuator/health/liveness'), 200, 'Database outage liveness')
  puts 'PASS: non-root/read-only Java 27 image, PostgreSQL/Flyway/runtime role, real JWT/docs/security, ownership and persisted completion, healthy probe and outage readiness/liveness separation.'
ensure
  containers.reverse_each do |container|
    _, status = Open3.capture2e('docker', 'stop', container)
    warn "Cleanup failed: #{container}" unless status.success?
  end
  if network_created
    _, status = Open3.capture2e('docker', 'network', 'rm', network)
    warn "Cleanup failed: #{network}" unless status.success?
  end
  server.shutdown
  thread.join
end
