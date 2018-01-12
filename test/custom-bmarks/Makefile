SHELL := /bin/sh
rvtests := $(abspath ../../riscv-tests)

CC := riscv32-unknown-elf-gcc -march=rv32i -mabi=ilp32
OBJDUMP := riscv32-unknown-elf-objdump

CFLAGS := -O0 -Wall -g -std=gnu99 -mcmodel=medany -fno-common -fno-builtin-printf -I $(rvtests)/env
LDFLAGS := -static -nostdlib -nostartfiles -T $(rvtests)/benchmarks/common/test.ld

programs := mix
bins := $(addsuffix .riscv,$(programs))
dumps := $(addsuffix .dump,$(programs))
logs := $(addsuffix .out,$(programs))

all: $(bins)
dump: $(dumps)
run: $(logs)

%.riscv: %.c crt.S
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $^

%.dump: %.riscv
	$(OBJDUMP) -D $< > $@

%.out: %.riscv
	spike --isa=rv32i -l $< > $@ 2>&1

clean:
	rm -f -- $(bins) $(dumps) $(logs)

.PHONY: all dump run clean
.SUFFIXES:
