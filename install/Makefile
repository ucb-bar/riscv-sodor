SHELL := /bin/sh

DESTDIR ?= .

install: install-tests install-bmarks

include ../emulator/common/tests.mk

global_asm_tests_dump := $(addsuffix .dump,$(global_asm_tests))
global_asm_tests_dir := $(DESTDIR)/riscv-tests

global_bmarks := $(addsuffix .riscv,$(global_bmarks))
global_bmarks_dump := $(addsuffix .dump,$(global_bmarks))
global_bmarks_dir := $(DESTDIR)/riscv-bmarks

srcdir := $(realpath ../riscv-tests)

install-tests: $(srcdir)/isa
	$(MAKE) -C $< XLEN=32 $(global_asm_tests) $(global_asm_tests_dump)
	install -p -m 755 $(addprefix $</,$(global_asm_tests)) $(global_asm_tests_dir)/
	install -p -m 644 $(addprefix $</,$(global_asm_tests_dump)) $(global_asm_tests_dir)/

install-bmarks: $(srcdir)/benchmarks
	$(MAKE) -C $< XLEN=32 $(global_bmarks) $(global_bmarks_dump)
	install -p -m 755 $(addprefix $</,$(global_bmarks)) $(global_bmarks_dir)/
	install -p -m 644 $(addprefix $</,$(global_bmarks_dump)) $(global_bmarks_dir)/

.PHONY: install install-tests install-bmarks

.PHONY: clean
clean:
	$(MAKE) -C $(srcdir)/isa clean
	$(MAKE) -C $(srcdir)/benchmarks clean
