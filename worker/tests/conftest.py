import atexit
import os
import shutil
import sys
import tempfile
from pathlib import Path

# Point the worker at a throwaway data dir BEFORE src.* modules are imported —
# generator.py resolves DATA_DIR at import time.
_tmp = tempfile.mkdtemp(prefix="gt-worker-tests-")
os.environ["DATA_DIR"] = _tmp
atexit.register(shutil.rmtree, _tmp, True)

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
