# Common
local common = import 'common.jsonnet';
local graal_common = import 'graal-common.json';

# Compiler
local compiler = import 'compiler/ci/ci.jsonnet';

# GraalWasm
local wasm = import 'wasm/ci/ci.jsonnet';

# Espresso
local espresso = import 'espresso/ci/ci.jsonnet';

# Regex
local regex = import 'regex/ci/ci.jsonnet';

# SDK
local sdk = import 'sdk/ci/ci.jsonnet';

# SubstrateVM
local substratevm = import 'substratevm/ci/ci.jsonnet';

# Sulong
local sulong = import 'sulong/ci/ci.jsonnet';

# Tools
local tools = import 'tools/ci/ci.jsonnet';

# Truffle
local truffle = import 'truffle/ci/ci.jsonnet';

# JavaDoc
local javadoc = import "ci_includes/publish-javadoc.jsonnet";

# VM
local vm = import 'vm/ci/ci_includes/vm.jsonnet';

local missing_notify = (import 'common-utils.libsonnet').missing_notify;

{
  # Ensure that entries in common.jsonnet can be resolved.
  _checkCommon: (import 'common.jsonnet'),
  ci_resources:: (import 'ci-resources.libsonnet'),
  overlay: graal_common.ci.overlay,
  specVersion: "3",
  builds: [common.add_excludes_guard(b) for b in (
    compiler.builds +
    wasm.builds +
    espresso.builds +
    regex.builds +
    sdk.builds +
    substratevm.builds +
    sulong.builds +
    tools.builds +
    truffle.builds +
    javadoc.builds +
    vm.builds
  )],
  missingNotify:: missing_notify(self.builds),
  assert std.length(self.missingNotify) == 0 : "Missing notify_emails or notify_groups: " + self.missingNotify,
}
