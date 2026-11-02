"""Decode a local copy of this device's version-1 camera configuration.

Derived from the inspected libOplusStringJNI and libOplusSecurity binaries.
Only local files are read/written. The native ARM libraries are not executed.
"""
from pathlib import Path
import hashlib
import json
import subprocess

root = Path(__file__).resolve().parent
library = (root / 'libOplusSecurity.so').read_bytes()
source = root / 'config/oplus_camera_config'
raw = source.read_bytes()
assert raw[:2] == b'\x01\x01', 'Unexpected configuration format/version'
# This offset is specific to the inspected library, not a universal decoder.
expected = json.loads((root / 'decoder-library-sha256.json').read_text())['sha256']
assert hashlib.sha256(library).hexdigest() == expected, 'Library changed'
# decode() builds 32 XOR-obfuscated bytes; AES key expansion takes even bytes.
key = bytes(b ^ 0x3f for b in library[0x7ab:0x7ab + 32])[::2]
payload = raw[4:-4]
assert len(payload) % 16 == 0
result = subprocess.run(
    ['openssl', 'enc', '-d', '-aes-128-ecb', '-K', key.hex(), '-nopad'],
    input=payload, capture_output=True, check=True)
plain = result.stdout.rstrip(b'\x00')
pad = plain[-1]
if 0 < pad <= 16 and plain[-pad:] == bytes([pad]) * pad:
    plain = plain[:-pad]
records = json.loads(plain)
assert isinstance(records, list)
assert all(isinstance(r, dict) and 'VendorTag' in r and 'Value' in r for r in records)
(root / 'config/oplus_camera_config.decoded.json').write_bytes(plain)
flags = [
    'com.oplus.feature.aicomposition.realscene.support',
    'com.oplus.feature.aicomposition.inspiration.support',
]
summary = {
    'source_sha256': hashlib.sha256(raw).hexdigest(),
    'decoder_library_sha256': expected,
    'decoded_sha256': hashlib.sha256(plain).hexdigest(),
    'record_count': len(records),
    'flag_records': {k: [r for r in records if r['VendorTag'] == k] for k in flags},
    'scope': 'Shipped configuration only; not Camera private overrides or process memory',
}
(root / 'config/flag-results.json').write_text(json.dumps(summary, indent=2) + '\n')
print(json.dumps(summary, indent=2))
