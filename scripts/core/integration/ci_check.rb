#!/usr/bin/env ruby
# Parse workflow YAML and check every app Gradle job has pinned Core setup.
require 'yaml'
root = File.expand_path('../../..', __dir__)
Dir[File.join(root, '.github/workflows/*.yml')].sort.each do |path|
  workflow = YAML.load_file(path)
  workflow.fetch('jobs').each do |name, job|
    steps = job.fetch('steps', [])
    builds = steps.any? { |step| step.fetch('run', '').match?(/\.\/gradlew.*(?:app:|assemble|testStandard)/) }
    next unless builds
    raise "Missing Core setup: #{File.basename(path)}/#{name}" unless steps.any? { |step| step['uses'] == './.github/actions/setup-core' }
    raise "CI must not enable coreOnly in release/perf: #{path}" if File.basename(path) != 'test.yml' && steps.any? { |step| step.fetch('run', '').include?('-PcoreOnly') }
    puts "PASS pinned Core setup: #{File.basename(path)}/#{name}"
  end
end
action = YAML.load_file(File.join(root, '.github/actions/setup-core/action.yml'))
raise 'Not a composite action' unless action.dig('runs', 'using') == 'composite'
steps = action.dig('runs', 'steps')
raise 'Missing pinned source checkout' unless steps.any? { |s| s.dig('with', 'repository') == 'hootkit-dev/hootkit-core' && s.dig('with', 'ref') == '${{ steps.lock.outputs.commit }}' }
perf = File.read(File.join(root, '.github/workflows/perf-device.yml'))
raise 'Unsafe baseline source splice' if perf.include?('git checkout "$SHA" -- app/src')
raise 'Missing historical Core compatibility guard' unless perf.include?('No compatible historical Core baseline')
puts 'PASS workflow/action YAML, pinned source, production flags and baseline source-set consistency'
