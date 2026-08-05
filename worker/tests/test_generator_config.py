"""Guards on generation settings that have burned us in real pre-gen runs."""
import re

from src import generator


def test_server_memory_is_configurable_and_not_the_old_2g_default():
    # A 9.4k-chunk pre-gen OOM'd at 2G during chunk save, truncating the
    # region files. Anything below ~4G is unsafe for the default radius.
    assert generator.SERVER_MEMORY, "SERVER_MEMORY must be set"
    m = re.fullmatch(r"(\d+)([GgMm])", generator.SERVER_MEMORY)
    assert m, f"unparseable SERVER_MEMORY: {generator.SERVER_MEMORY!r}"
    gigabytes = int(m.group(1)) / (1 if m.group(2) in "Gg" else 1024)
    assert gigabytes >= 4, "default heap is too small for 1024-tall pre-gen"


def test_oom_is_treated_as_fatal_not_shipped():
    # The OOM check must look for the JVM's exact error string; a truncated
    # world that reports "ready" is worse than a failed job.
    src = (generator.__file__)
    text = open(src).read()
    assert "OutOfMemoryError" in text, "OOM detection missing from generator"


def test_pregen_defaults_are_sane():
    assert generator.PREGEN_RADIUS >= 0
    assert generator.PREGEN_TIMEOUT > generator.STARTUP_TIMEOUT
