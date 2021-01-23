SHELL := /bin/sh

CC := riscv32-unknown-elf-gcc -march=rv32i -mabi=ilp32
OBJDUMP := riscv32-unknown-elf-objdump

CFLAGS := -O0 -Wall -g -std=gnu99 -mcmodel=medany -fno-common -fno-builtin-printf -I ../env
LDFLAGS := -static -nostdlib -nostartfiles -T ../env/test.ld

programs := mix
bins := $(addsuffix .riscv,$(programs))
dumps := $(addsuffix .dump,$(programs))
logs := $(addsuffix .out,$(programs))

all: $(bins) $(dumps)
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
