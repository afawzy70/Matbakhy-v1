"""Validate generated native test-harness syntax before building or executing app binaries."""
from pathlib import Path
import shutil,subprocess,tempfile
root=Path.cwd();instrument=Path(__file__).with_name('instrument.py').resolve()
files=['apps/customer-mobile/e2e-native/slow_proxy.py','apps/customer-mobile/scripts/order-roundtrip-android.sh','apps/customer-mobile/e2e-ios/UITests/OrderingTests.swift','apps/customer-mobile/scripts/order-roundtrip-ios.sh']
with tempfile.TemporaryDirectory(prefix='iqa-native-controls-') as folder:
 target=Path(folder)
 for rel in files:
  p=target/rel;p.parent.mkdir(parents=True,exist_ok=True);shutil.copy(root/rel,p)
 subprocess.run(['python3',str(instrument)],cwd=target,check=True)
 for rel in [files[1],files[3]]:subprocess.run(['bash','-n',str(target/rel)],check=True)
 subprocess.run(['python3','-m','py_compile',str(target/files[0])],check=True)
 ios=(target/files[3]).read_text();swift=(target/files[2]).read_text()
 assert 'zero successful executions' in ios
 for name in ['testG_IndependentSameSessionPolicyEditAndRecovery','testH_IndependentCustomerSupportReply']:assert 'phase '+name in ios and 'func '+name in swift
 assert 'IQA-PENDING-PRECONDITION' in swift
print('PASS: generated harness syntax and mandatory selectors validated; no product code edited')
